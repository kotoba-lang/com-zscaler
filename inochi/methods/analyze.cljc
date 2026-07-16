(ns inochi.methods.analyze
  "inochi 命 — edge-primary ecological 取-concentration analyzer over the biosphere graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606073000).

  Reads a kotoba-EDN biosphere graph (:organism/* nodes + :en/* 縁 over the
  biosphere-ontology), and surfaces — aggregate-first — where ECOLOGICAL custody-debt
  (extinction / degradation pressure) accumulates over the living world, routed to
  RESTORATION (release), and where dependency cascades make that debt fragile.

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. karma/取 lives ONLY on edges (:en/grasping-load). A node's
      restoration-priority is the INTEGRAL of its incident inbound :pressures edges
      (severity × disclosed IUCN weight) — computed on READ, never a stored per-organism
      score. There is no :biosphere/score-of-species.
    G1 — RESTORATION map, never a target-list. No precise occurrence coordinates are read
      or emitted; spatial readouts are biome/realm-aggregate only. The 取-holder is the
      PRESSURE; the bearer is the living world; the routing is restoration.
    N3 — non-adjudicating. IUCN categories are DISCLOSED facts, never inochi verdicts.

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

;; ── disclosed IUCN category → representative threat weight (NOT a verdict; mirrors schema)
(def iucn-weight
  {":EX" 1.0 ":EW" 1.0 ":CR" 0.9 ":EN" 0.7 ":VU" 0.5
   ":NT" 0.3 ":LC" 0.1 ":DD" 0.4})

(def pressure-kinds #{":pressures"})
(def dependency-kinds #{":depends-on" ":keystone-of"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of nodes is preserved (ordered map) to match Python dict order."
  [forms]
  (reduce
   (fn [{:keys [nodes edges] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":organism/id") (assoc-in acc [:nodes (get f ":organism/id")] f)
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update acc :edges conj f)
       :else acc))
   {:nodes (array-map) :edges []}
   forms))

#?(:clj
   (defn load-file*
     "Read + parse a biosphere EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/grasping-load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/grasping-load")]
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
   {\"restoration\" {node v} \"fragility\" {node v} \"pressure_out\" {pressure v}}.

   restoration[node] = Σ incident inbound :pressures load × IUCN weight of bearer
   fragility[node]   = Σ incident :depends-on / :keystone-of load (cascade risk)
   pressure_out[node]= Σ outbound :pressures load a pressure imposes (the 取-holder)

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable
   sort in `rank` ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (loop [es edges
         restoration (ordered-map) fragility (ordered-map) pressure-out (ordered-map)]
    (if (empty? es)
      {"restoration" restoration
       "fragility" fragility
       "pressure_out" pressure-out}
      (let [e (first es)
            kind (get e ":en/kind")
            load- (->load e)
            src (get e ":en/from")
            dst (get e ":en/to")]
        (cond
          (contains? pressure-kinds kind)
          (let [bearer (get nodes dst {})
                w (get iucn-weight (get bearer ":taxon/iucn") 0.5)]
            (recur (rest es)
                   (omap-update restoration dst #(+ % (* load- w)))
                   fragility
                   (omap-update pressure-out src #(+ % load-))))

          (contains? dependency-kinds kind)
          (recur (rest es)
                 restoration
                 (-> fragility
                     (omap-update src #(+ % load-))
                     (omap-update dst #(+ % load-)))
                 pressure-out)

          :else
          (recur (rest es) restoration fragility pressure-out))))))

(defn systemic-pressures
  "Per-PRESSURE reach: how many DISTINCT bearers (species / ecosystems / biomes) each pressure
  threatens, alongside the total grasping-load it imposes. `analyze`'s pressure_out ranks pressures
  by total LOAD; this ranks them by BREADTH — a threat spreading across many bearers is more
  SYSTEMIC than one concentrated on a few (even at equal load), the restoration priority a load-only
  view misses. The 取-holder ranked here is the PRESSURE, never a bearer — it is a restoration map,
  never a hunting / target-list (G1), and reach + load are edge-primary, counted on read
  (G2; no stored score, no occurrence coordinates). Ranked [pressure reach total-load label] by
  (reach desc, total-load desc)."
  ([nodes edges] (systemic-pressures nodes edges 20))
  ([nodes edges limit]
   (->> edges
        (reduce (fn [m e]
                  (if (contains? pressure-kinds (get e ":en/kind"))
                    (-> m
                        (update-in [(get e ":en/from") :bearers] (fnil conj #{}) (get e ":en/to"))
                        (update-in [(get e ":en/from") :load] (fnil + 0.0) (->load e)))
                    m))
                {})
        (map (fn [[p {:keys [bearers load]}]]
               [p (count bearers) load (get-in nodes [p ":organism/label"] p)]))
        (sort-by (fn [[_ reach load _]] [(- reach) (- load)]))
        (take limit)
        vec)))

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
  "Render the biosphere restoration-priority report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-species (count-kind nodes #{":species"})
        n-eco (count-kind nodes #{":ecosystem" ":biome"})
        n-press (count-kind nodes #{":pressure"})
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# inochi 命 — biosphere restoration-priority report (aggregate-first)\n")
    (conj! L (str "> **G1 — RESTORATION map, NEVER a target-list.** No occurrence coordinates; "
                  "biome/realm-aggregate only. The 取-holder is the pressure; the bearer is the "
                  "living world; the routing is restoration. IUCN categories are DISCLOSED, not "
                  "inochi verdicts (N3). 取 lives only on edges, integrated on read (N1).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-species " species · " n-eco
                  " ecosystems/biomes · " n-press " pressures) · " (count edges) " 縁 · "
                  auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Restoration priority — living world bearing the most custody-debt\n")
    (conj! L "_Σ incident inbound pressure-load × disclosed IUCN weight; routed to restoration._\n")
    (conj! L "| rank | bearer | IUCN | restoration-priority |")
    (conj! L "|---:|---|:--:|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "restoration") nodes))]
      (let [iucn0 (get-in nodes [nid ":taxon/iucn"])
            iucn (if (or (nil? iucn0) (false? iucn0)) "—" iucn0)
            label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (lstrip-colon (str iucn)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Pressure concentration — 取-holders imposing the most ecological debt\n")
    (conj! L (str "_Σ outbound pressure-load; cross-link to tsumugi/danjo power-graph where a "
                  "power-entity drives the pressure (accountability, aggregate-first)._\n"))
    (conj! L "| rank | pressure | kind | imposed-load |")
    (conj! L "|---:|---|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "pressure_out") nodes))]
      (let [kind0 (get-in nodes [nid ":pressure/kind"])
            kind (if (or (nil? kind0) (false? kind0)) "—" kind0)
            label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (lstrip-colon (str kind)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Cascade fragility — dependency / keystone load (loss propagates)\n")
    (conj! L "| rank | node | fragility |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "fragility") nodes 12))]
      (let [label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |"))))

    (conj! L (str "\n---\n_inochi 命 · ADR-2606073000 · mirror-only · non-adjudicating · "
                  "edge-primary · restoration-routed. Live ingest (IUCN/GBIF/IPCC) is "
                  "G7/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/restoration-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-biosphere-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "restoration-report.md") (report-md nodes edges res))
       (println (str "inochi: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "restoration-report.md")))
       (when-let [top (first (rank (get res "restoration") nodes 1))]
         (println (str "  top restoration-priority: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
