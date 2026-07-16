(ns tsugite.methods.analyze
  "tsugite 継ぎ手 — edge-primary peoples-continuity analyzer over the peoples graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606073800).

  Reads a kotoba-EDN peoples graph (:organism/* nodes + :en/* 縁 over the peoples-ontology)
  and surfaces — aggregate-first, at COLLECTIVE scale — where DISPLACEMENT + ERASURE pressure
  concentrates on human collectives and the tongues they carry (the continuity surface),
  where PROTECTION buffers absorb it, and how fragile each people↔tongue transmission coupling
  is, all routed to CONTINUITY (継承) — safe passage + protection + revitalization.

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. karma/pressure lives ONLY on edges (:en/peril-load). A bearer's
      continuity-need is the INTEGRAL of its incident inbound pressure 縁 (severity × disclosed
      vitality weight) — computed on READ, never a stored per-collective score. There is no
      :tsugite/score-of-people.
    G1 — PEOPLES-CONTINUITY map at AGGREGATE / collective scale, NEVER a person-tracking /
      individual-locator / border-enforcement / deportation / surveillance aid. No individual
      records, no real-time location, no biometric. The bearer is ALWAYS a collective. It routes
      to refuge + revitalization, never to interdiction.
    N3 — non-adjudicating. Displacement figures and language-vitality categories are DISCLOSED
      facts (UNHCR/IOM/UNESCO/Ethnologue), never tsugite verdicts.

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

;; ── disclosed language-vitality → representative weight (NOT a verdict; mirrors schema)
(def vitality-weight
  {":extinct" 1.0 ":critically-endangered" 0.9 ":severely-endangered" 0.8
   ":definitely-endangered" 0.6 ":vulnerable" 0.4 ":safe" 0.1})

(def pressure-kinds #{":displaces" ":erases"})
(def haven-kinds #{":shelters" ":revitalizes" ":protects"})
(def transmission-kinds #{":speaks"})

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
     "Read + parse a peoples EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/peril-load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/peril-load")]
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
   {\"continuity\" {bearer v} \"protection\" {node v} \"fragility\" {node v}
    \"pressure_out\" {src v}}.

   continuity[bearer]  = Σ incident inbound pressure load × disclosed vitality weight
   protection[node]    = Σ incident inbound :shelters/:revitalizes/:protects load
   fragility[node]     = Σ incident :speaks load (people ↔ tongue transmission coupling)
   pressure_out[src]   = Σ outbound pressure load (the 取-holder driver)

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable
   sort in `rank` ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (loop [es edges
         continuity (ordered-map) protection (ordered-map)
         fragility (ordered-map) pressure-out (ordered-map)]
    (if (empty? es)
      {"continuity" continuity
       "protection" protection
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
                w (get vitality-weight (get bearer ":lang/vitality") 0.6)]
            (recur (rest es)
                   (omap-update continuity dst #(+ % (* load- w)))
                   protection
                   fragility
                   (omap-update pressure-out src #(+ % load-))))

          (contains? haven-kinds kind)
          (recur (rest es)
                 continuity
                 (omap-update protection dst #(+ % load-))
                 fragility
                 pressure-out)

          (contains? transmission-kinds kind)
          (recur (rest es)
                 continuity
                 protection
                 (-> fragility
                     (omap-update src #(+ % load-))
                     (omap-update dst #(+ % load-)))
                 pressure-out)

          :else
          (recur (rest es) continuity protection fragility pressure-out))))))

(defn double-jeopardy
  "Per-COLLECTIVE double jeopardy: the product of continuity-pressure × transmission-fragility — the
  collectives bearing BOTH displacement/erasure pressure (continuity) AND a fragile people↔language
  `:speaks` coupling (fragility), at risk of losing safe passage AND their tongue at once. This
  operationalizes the documented coupling — when displacement imperils a people, transmission-
  fragility shows the language at downstream risk, BOTH routed to continuity. The continuity ranking
  alone misses the language dimension and the fragility ranking misses the displacement dimension;
  their product surfaces the collectives where the two strands COMPOUND (it lists ONLY those with
  both > 0) — the highest continuity priority (safe passage + revitalization together). Aggregate,
  collective-scale (no individual / location / person-tracking, G1); reads the on-read integrals
  (G2, no stored score). Takes an `analyze` result + nodes; returns
  [collective jeopardy continuity fragility label] by jeopardy descending."
  ([analysis nodes] (double-jeopardy analysis nodes 20))
  ([analysis nodes limit]
   (let [continuity (get analysis "continuity" {})
         fragility (get analysis "fragility" {})
         ids (distinct (concat (keys continuity) (keys fragility)))]
     (->> ids
          (keep (fn [id]
                  (let [c (double (get continuity id 0.0))
                        f (double (get fragility id 0.0))
                        j (* c f)]
                    (when (pos? j)
                      [id j c f (get-in nodes [id ":organism/label"] id)]))))
          (sort-by (fn [[_ j _ _ _]] (- j)))
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

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":organism/kind")) (vals nodes))))

(defn report-md
  "Render the peoples-continuity report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-ppl (count-kind nodes ":people")
        n-lang (count-kind nodes ":language")
        n-press (count-kind nodes ":pressure")
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# tsugite 継ぎ手 — peoples-continuity report (collective-aggregate)\n")
    (conj! L (str "> **G1 — PEOPLES-CONTINUITY map, NEVER person-tracking.** Collective scale only; "
                  "no individual records, no real-time location, no biometric, no border-enforcement "
                  "/ deportation use. The 取-holder is the pressure; the bearer is the collective / "
                  "tongue; the routing is continuity (継承) — safe passage + protection + "
                  "revitalization, never interdiction. Displacement figures + language vitality are "
                  "DISCLOSED (N3). Pressure lives only on edges, integrated on read (N1).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-ppl " peoples · " n-lang " languages · "
                  n-press " pressures) · " (count edges) " 縁 · " auth "/" (count nodes)
                  " :authoritative\n"))

    (conj! L "\n## Continuity need — collectives/tongues bearing the most displacement+erasure\n")
    (conj! L "_Σ incident inbound pressure-load × disclosed vitality weight; routed to continuity._\n")
    (conj! L "| rank | bearer | vitality | continuity-need |")
    (conj! L "|---:|---|:--:|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "continuity") nodes))]
      (let [vit0 (get-in nodes [nid ":lang/vitality"])
            vit (if (or (nil? vit0) (false? vit0)) "—" vit0)
            label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (lstrip-colon (str vit)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Pressure concentration — drivers of displacement + erasure\n")
    (conj! L "_Σ outbound pressure-load; the 取-holders, routed to protection + revitalization._\n")
    (conj! L "| rank | pressure | kind | imposed-load |")
    (conj! L "|---:|---|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "pressure_out") nodes))]
      (let [kind0 (get-in nodes [nid ":pressure/kind"])
            kind (if (or (nil? kind0) (false? kind0)) "—" kind0)
            label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (lstrip-colon (str kind)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Protection buffers — refuge / revitalization (the 守り)\n")
    (conj! L "| rank | node | protection-buffer |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "protection") nodes 12))]
      (let [label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |"))))

    (conj! L "\n## Transmission fragility — people↔tongue coupling at risk (loss cascades)\n")
    (conj! L "| rank | node | fragility |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "fragility") nodes 10))]
      (let [label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |"))))

    (conj! L (str "\n---\n_tsugite 継ぎ手 · ADR-2606073800 · mirror-only · continuity-routed · "
                  "non-adjudicating · no-person-tracking · edge-primary. Live aggregate ingest is "
                  "G7/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/continuity-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-peoples-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "continuity-report.md") (report-md nodes edges res))
       (println (str "tsugite: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "continuity-report.md")))
       (when-let [top (first (rank (get res "continuity") nodes 1))]
         (println (str "  top continuity-need: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
