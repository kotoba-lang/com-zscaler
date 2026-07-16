(ns hoshimori.methods.analyze
  "hoshimori 星守 — edge-primary orbital-congestion analyzer over the orbit graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606073600).

  Reads a kotoba-EDN orbital graph (:organism/* nodes + :en/* 縁 over the orbit-ontology)
  and surfaces — aggregate-first, at orbital-shell granularity — where ORBITAL CONGESTION /
  collision risk concentrates (the stewardship surface), where STEWARDSHIP buffers absorb it,
  and how fragile the public services that depend on each regime are, all routed to
  STEWARDSHIP (orbital sustainability).

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. karma/congestion lives ONLY on edges (:en/orbit-load). A regime's
      congestion-concentration is the INTEGRAL of its incident inbound hazard/occupancy 縁
      (severity × disclosed regime weight) — computed on READ, never a stored per-object score.
      There is no :hoshimori/threat-of-object.
    G1 — STEWARDSHIP / sustainability map, NEVER a targeting / interception / weaponization
      aid. No precise predictive ephemeris (no interception-grade state vector); readouts are
      orbital-shell / regime-aggregate. ASAT / kinetic-intercept uses are unrepresentable.
    N3 — non-adjudicating. Regime defs and named public debris EVENTS are DISCLOSED facts,
      never hoshimori verdicts.

  House style: Python ':…' keyword strings stay strings (incl. all :organism/* / :en/* attrs);
  pure fns; file I/O only at edges via clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, \"string\", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; \":ns/name\" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of each tok-re match that captured)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t)
              (step)
              (cons t (step))))))))))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string;
  int → long; else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END sentinel)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── disclosed orbital-regime → representative criticality/crowding weight (NOT a verdict)
(def regime-weight
  {":leo-low" 1.0 ":sso" 0.9 ":geo" 0.8 ":leo-high" 0.7
   ":meo" 0.6 ":heo" 0.4})

(def hazard-kinds #{":congests" ":imperils"})
(def stewardship-kinds #{":remediates" ":deconflicts" ":deorbits"})
(def dependency-kinds #{":depends-on"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of nodes is preserved to match Python dict iteration order: the nodes map
  carries ::node-order metadata = a vector of node ids in first-touch order (an array-map
  only preserves order ≤8 keys, and there are >8 nodes, so the order is tracked explicitly —
  see `node-ids`). Datom emit iterates nodes in this order to stay byte-identical to Python."
  [forms]
  (reduce
   (fn [{:keys [nodes edges] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":organism/id")
       (let [nid (get f ":organism/id")
             had? (contains? nodes nid)
             nodes' (assoc nodes nid f)]
         (assoc acc :nodes
                (if had?
                  (with-meta nodes' (meta nodes))
                  (vary-meta nodes' update ::node-order (fnil conj []) nid))))
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update acc :edges conj f)
       :else acc))
   {:nodes (with-meta {} {::node-order []}) :edges []}
   forms))

(defn node-ids
  "Node ids of a loaded nodes map in first-touch insertion order (mirrors Python dict order).
  Falls back to (keys nodes) if no ::node-order metadata is present."
  [nodes]
  (or (::node-order (meta nodes)) (keys nodes)))

#?(:clj
   (defn load-file*
     "Read + parse an orbit EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/orbit-load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/orbit-load")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn- ordered-map
  "Wrap a plain map with the first-touch insertion order of its keys (mirroring a Python
  defaultdict). Returns metadata-carrying map: ::order is a vector of keys in first-touch
  order. (array-map only preserves order ≤8 keys, so we track order explicitly.)"
  []
  ^{::order []} {})

(defn- omap-update
  "update an ordered-map: apply f to the value at k (default 0.0 via fnil), recording k's
  first-touch position in ::order metadata."
  [m k f]
  (let [had? (contains? m k)
        m' (update m k (fnil f 0.0))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2). Returns
   {\"congestion\" {shell v} \"stewardship\" {node v} \"fragility\" {node v}
    \"congestion_out\" {src v}}.

   congestion[shell]   = Σ incident inbound hazard/occupancy load × disclosed regime weight
   stewardship[node]   = Σ incident inbound :remediates/:deconflicts/:deorbits load
   fragility[node]     = Σ incident :depends-on load (service ↔ regime cascade risk)
   congestion_out[src] = Σ outbound hazard/occupancy load (the 取-holder occupying/imperiling)

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable
   sort in `rank` ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (loop [es edges
         congestion (ordered-map) stewardship (ordered-map)
         fragility (ordered-map) congestion-out (ordered-map)]
    (if (empty? es)
      {"congestion" congestion
       "stewardship" stewardship
       "fragility" fragility
       "congestion_out" congestion-out}
      (let [e (first es)
            kind (get e ":en/kind")
            load- (->load e)
            src (get e ":en/from")
            dst (get e ":en/to")]
        (cond
          (contains? hazard-kinds kind)
          (let [shell (get nodes dst {})
                w (get regime-weight (get shell ":shell/regime") 0.6)]
            (recur (rest es)
                   (omap-update congestion dst #(+ % (* load- w)))
                   stewardship
                   fragility
                   (omap-update congestion-out src #(+ % load-))))

          (contains? stewardship-kinds kind)
          (recur (rest es)
                 congestion
                 (omap-update stewardship dst #(+ % load-))
                 fragility
                 congestion-out)

          (contains? dependency-kinds kind)
          (recur (rest es)
                 congestion
                 stewardship
                 (-> fragility
                     (omap-update src #(+ % load-))
                     (omap-update dst #(+ % load-)))
                 congestion-out)

          :else
          (recur (rest es) congestion stewardship fragility congestion-out))))))

(defn stewardship-gap
  "Orbital shells that are CONGESTED yet have NO stewardship touching them — the UNADDRESSED
  congestion priority. `analyze` ranks shells by congestion, but the most-congested shell may already
  be stewarded (remediation / deconfliction / deorbit edges); this surfaces the shells that bear
  hazard load and NO :remediates/:deconflicts/:deorbits edge at all — where stewardship is MISSING,
  not merely where congestion is high. Shell-level aggregate (no precise ephemeris / no per-object
  positional data, G1); edge-primary, counted on read (G2); routed to stewardship, never a targeting
  aid. Returns [shell congestion-load label] for the unstewarded congested shells, by load descending."
  ([nodes edges] (stewardship-gap nodes edges 20))
  ([nodes edges limit]
   (let [congested (reduce (fn [m e]
                             (if (contains? hazard-kinds (get e ":en/kind"))
                               (update m (get e ":en/to") (fnil + 0.0) (->load e))
                               m))
                           {} edges)
         stewarded (reduce (fn [s e]
                             (if (contains? stewardship-kinds (get e ":en/kind"))
                               (conj s (get e ":en/to") (get e ":en/from"))
                               s))
                           #{} edges)]
     (->> congested
          (remove (fn [[shell _]] (contains? stewarded shell)))
          (filter (fn [[_ load]] (pos? load)))
          (sort-by (fn [[_ load]] (- load)))
          (map (fn [[shell load]] [shell load (get-in nodes [shell ":organism/label"] shell)]))
          (take limit)
          vec))))

(defn- omap-items
  "Items of an ordered-map in first-touch order (falls back to seq order if no ::order)."
  [d]
  (let [order (::order (meta d))]
    (if order
      (map (fn [k] [k (get d k)]) order)
      (seq d))))

(defn rank
  "Top-`limit` (id, label, value) rows of d, sorted by -value only (STABLE — ties keep
  first-touch insertion order, mirroring Python's `sorted(d.items(), key=lambda kv: -kv[1])`
  on an insertion-ordered dict)."
  ([d nodes] (rank d nodes 20))
  ([d nodes limit]
   (->> (sort-by (fn [[_ v]] (- v)) (omap-items d))
        (take limit)
        (mapv (fn [[nid v]]
                [nid (get-in nodes [nid ":organism/label"] nid) v])))))

;; ── report rendering (matches report_md's f-strings) ────────────────────────

(defn- fmt3 [v] (format "%.3f" (double v)))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- count-kind [nodes ks]
  (count (filter #(contains? ks (get % ":organism/kind")) (vals nodes))))

(defn report-md
  "Render the orbital-congestion stewardship report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-shell (count-kind nodes #{":shell"})
        n-op (count-kind nodes #{":operator"})
        n-haz (count-kind nodes #{":hazard"})
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# hoshimori 星守 — orbital-congestion stewardship report (shell-aggregate)\n")
    (conj! L (str "> **G1 — STEWARDSHIP map, NEVER a targeting / interception aid.** No precise "
                  "predictive ephemeris (no interception-grade state vector); readouts are "
                  "orbital-shell / regime-aggregate. The 取-holder is the hazard/occupancy; the "
                  "bearer is the regime + the public services on it; the routing is stewardship "
                  "(orbital sustainability). Regime defs + named public debris EVENTS are DISCLOSED "
                  "(N3). Congestion lives only on edges, integrated on read (N1). Mirrors only "
                  "already-public catalogs.\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-shell " shells · " n-op
                  " operators · " n-haz " hazards) · " (count edges) " 縁 · "
                  auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Congestion concentration — regimes bearing the most crowding/collision risk\n")
    (conj! L "_Σ incident inbound hazard/occupancy load × disclosed regime weight; routed to stewardship._\n")
    (conj! L "| rank | shell | regime | congestion |")
    (conj! L "|---:|---|:--:|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "congestion") nodes))]
      (let [reg0 (get-in nodes [nid ":shell/regime"])
            reg (if (or (nil? reg0) (false? reg0)) "—" reg0)]
        (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid) " | "
                      (lstrip-colon (str reg)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Occupancy / hazard concentration — 取-holders crowding or imperiling orbit\n")
    (conj! L "_Σ outbound occupancy/hazard load; routed to deconfliction + debris remediation._\n")
    (conj! L "| rank | source | load |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "congestion_out") nodes))]
      (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |")))

    (conj! L "\n## Stewardship buffers — remediation / deconfliction / disposal (the 守り)\n")
    (conj! L "| rank | node | stewardship-buffer |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "stewardship") nodes 12))]
      (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |")))

    (conj! L "\n## Service-dependency fragility — public utilities exposed to a regime's loss\n")
    (conj! L "| rank | node | fragility |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "fragility") nodes 10))]
      (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |")))

    (conj! L (str "\n---\n_hoshimori 星守 · ADR-2606073600 · mirror-only · stewardship-routed · "
                  "non-adjudicating · no-targeting · edge-primary. Live catalog ingest is "
                  "G7/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/congestion-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-orbit-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "congestion-report.md") (report-md nodes edges res))
       (println (str "hoshimori: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "congestion-report.md")))
       (when-let [top (first (rank (get res "congestion") nodes 1))]
         (println (str "  top congestion concentration: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
