(ns hokorobi.methods.analyze
  "hokorobi 綻び — edge-primary systemic finance-risk analyzer over the finrisk graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606073400).

  Reads a kotoba-EDN finance-risk graph (:organism/* nodes + :en/* 縁 over the
  finrisk-ontology) and surfaces — aggregate-first — where SYSTEMIC RISK concentrates
  (the resilience surface) vs where RESILIENCE buffers absorb it, routed to RESILIENCE (繕い).

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. karma/risk lives ONLY on edges (:en/risk-load). A node's
      systemic-risk-concentration is the INTEGRAL of its incident inbound risk 縁 (severity ×
      disclosed systemic-importance weight) — computed on READ, never a stored per-institution
      score. There is no :hokorobi/solvency-of-bank.
    G1 — RESILIENCE map, never a panic / bank-run trigger, never a trading / short /
      market-moving signal, never a per-institution solvency verdict. It NEVER trades. The
      取-holder is the RISK-SOURCE; the bearer is the public; the routing is resilience.
    N3 — non-adjudicating. Systemic-importance designations (G-SIB / D-SIB / …) are DISCLOSED
      facts, never hokorobi verdicts; no investment advice / forecast (kanjo discipline).

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

;; ── disclosed systemic-importance tier → representative weight (NOT a verdict; mirrors schema)
(def sii-weight
  {":g-sib" 1.0 ":d-sib" 0.7 ":large" 0.5 ":mid" 0.3 ":small" 0.1})

(def risk-kinds #{":exposes" ":interconnects" ":underfunds" ":protection-gap"})
(def resilience-kinds #{":backstops" ":capitalizes" ":diversifies"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of nodes is preserved: the nodes map carries ::node-order metadata (a vector
  of ids in first-touch order) so iteration matches Python dict order even past 8 entries
  (a Clojure array-map silently converts to an unordered hash-map above 8 keys). Read it back
  with `node-ids`."
  [forms]
  (reduce
   (fn [{:keys [nodes edges] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":organism/id")
       (let [id (get f ":organism/id")
             had? (contains? nodes id)
             nodes' (assoc nodes id f)]
         (assoc acc :nodes
                (if had?
                  (with-meta nodes' (meta nodes))
                  (with-meta nodes' (update (meta nodes) ::node-order (fnil conj []) id)))))
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update acc :edges conj f)
       :else acc))
   {:nodes (with-meta {} {::node-order []}) :edges []}
   forms))

(defn node-ids
  "Node ids in first-touch insertion order (the seed-file order), mirroring Python dict
  iteration. Falls back to (keys nodes) if no ::node-order metadata is present."
  [nodes]
  (or (::node-order (meta nodes)) (keys nodes)))

#?(:clj
   (defn load-file*
     "Read + parse a finrisk EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/risk-load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/risk-load")]
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
   {\"systemic\" {node v} \"resilience\" {node v} \"risk_out\" {source v}}.

   systemic[node]   = Σ incident inbound risk-load × disclosed systemic-importance weight
   resilience[node] = Σ incident inbound :backstops/:capitalizes/:diversifies load
   risk_out[source] = Σ outbound risk-load (the 取-holder risk-source)

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable
   sort in `rank` ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (loop [es edges
         systemic (ordered-map) resilience (ordered-map) risk-out (ordered-map)]
    (if (empty? es)
      {"systemic" systemic
       "resilience" resilience
       "risk_out" risk-out}
      (let [e (first es)
            kind (get e ":en/kind")
            load- (->load e)
            src (get e ":en/from")
            dst (get e ":en/to")]
        (cond
          (contains? risk-kinds kind)
          (let [bearer (get nodes dst {})
                w (get sii-weight (get bearer ":inst/sii") 0.6)]
            (recur (rest es)
                   (omap-update systemic dst #(+ % (* load- w)))
                   resilience
                   (omap-update risk-out src #(+ % load-))))

          (contains? resilience-kinds kind)
          (recur (rest es)
                 systemic
                 (omap-update resilience dst #(+ % load-))
                 risk-out)

          :else
          (recur (rest es) systemic resilience risk-out))))))

(defn contagion-linchpins
  "Per-node CONTAGION degree: the count of :interconnects edges incident to a node — how
  interconnected it is, hence how widely its distress would PROPAGATE through the system. The
  systemic-risk integral weighs the load a node bears; this counts the contagion LINKS, surfacing the
  linchpin market infrastructure (clearing CCPs, dealer banks) whose interconnectedness makes them
  systemic concentrators regardless of their own borne load — routed to resilience (redundancy /
  ring-fencing). A structural NETWORK reading (degree/centrality), NEVER a per-institution solvency
  verdict or a panic / trading signal (G1); edge-primary, on read (G2). Returns
  [node contagion-degree label] by degree descending."
  ([nodes edges] (contagion-linchpins nodes edges 20))
  ([nodes edges limit]
   (->> edges
        (filter #(= ":interconnects" (get % ":en/kind")))
        (reduce (fn [m e] (-> m
                              (update (get e ":en/from") (fnil inc 0))
                              (update (get e ":en/to") (fnil inc 0))))
                {})
        (sort-by (fn [[n d]] [(- d) (str n)]))
        (map (fn [[n d]] [n d (get-in nodes [n ":organism/label"] n)]))
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

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":organism/kind")) (vals nodes))))

(defn report-md
  "Render the systemic finance-risk report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-inst (count-kind nodes ":institution")
        n-risk (count-kind nodes ":risk")
        n-bear (count-kind nodes ":bearer")
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# hokorobi 綻び — systemic finance-risk report (aggregate-first)\n")
    (conj! L (str "> **G1 — RESILIENCE map, NEVER a panic / bank-run / trading signal.** No "
                  "market-moving signal, no per-institution solvency verdict; it NEVER trades. The "
                  "取-holder is the risk-source; the bearer is the public; the routing is resilience "
                  "(繕い). Systemic-importance designations are DISCLOSED, not hokorobi verdicts "
                  "(N3); no advice/forecast. Risk lives only on edges, integrated on read (N1).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-inst " institutions · " n-risk
                  " risk-sources · " n-bear " bearers) · " (count edges) " 縁 · "
                  auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Systemic-risk concentration — where fragility accumulates (resilience surface)\n")
    (conj! L "_Σ incident inbound risk-load × disclosed systemic-importance weight; routed to resilience._\n")
    (conj! L "| rank | node | SII | systemic-risk |")
    (conj! L "|---:|---|:--:|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "systemic") nodes))]
      (let [sii0 (get-in nodes [nid ":inst/sii"])
            sii (if (or (nil? sii0) (false? sii0)) "—" sii0)]
        (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid)
                      " | " (lstrip-colon (str sii)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Risk-source concentration — 取-holders imposing the most systemic fragility\n")
    (conj! L "_Σ outbound risk-load; the channels of contagion, routed to resilience._\n")
    (conj! L "| rank | risk-source | kind | imposed-load |")
    (conj! L "|---:|---|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "risk_out") nodes))]
      (let [kind0 (get-in nodes [nid ":risk/kind"])
            kind (if (or (nil? kind0) (false? kind0)) "—" kind0)]
        (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid)
                      " | " (lstrip-colon (str kind)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Resilience buffers — absorptive capacity (the mending 繕い)\n")
    (conj! L "| rank | node | resilience-buffer |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "resilience") nodes 12))]
      (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid) " | " (fmt3 v) " |")))

    (conj! L (str "\n---\n_hokorobi 綻び · ADR-2606073400 · mirror-only · observation-only · "
                  "non-adjudicating · never-trades · edge-primary · resilience-routed. Live ingest "
                  "(FSB/IAIS/regulator) is G7/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/systemic-risk-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-finrisk-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "systemic-risk-report.md") (report-md nodes edges res))
       (println (str "hokorobi: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "systemic-risk-report.md")))
       (when-let [top (first (rank (get res "systemic") nodes 1))]
         (println (str "  top systemic-risk concentration: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
