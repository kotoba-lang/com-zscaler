(ns asobi.methods.analyze
  "asobi 遊び — edge-primary participation/enclosure analyzer over the freed-time graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606073200).

  Reads a kotoba-EDN play/expression graph (:organism/* nodes + :en/* 縁 over the
  asobi-ontology) and surfaces — aggregate-first — where PARTICIPATION is open (the access
  surface to widen) and where ENCLOSURE gates the freed-time telos (paywall / attention-
  platform / lock), routed to OPENING.

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. karma lives ONLY on edges (:en/access-load). A node's
      participation-openness is the INTEGRAL of its incident OPENING 縁 — computed on READ,
      never a stored per-work score. There is no :asobi/popularity-of-work.
    G1 — PARTICIPATION / ACCESS map, never an engagement / attention / popularity ranking.
      No retention metric, no recommend-for-time-on-platform. The 取-holder is the ENCLOSURE;
      the bearer is the play; the routing is OPENING.
    N3 — non-adjudicating. Access categories (:public-domain … :proprietary) are DISCLOSED
      facts, never asobi verdicts.

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

;; ── disclosed access category → representative openness weight (NOT a verdict; mirrors schema)
(def access-weight
  {":public-domain" 1.0 ":open-license" 0.9 ":free-gratis" 0.7
   ":ticketed" 0.4 ":paywalled" 0.2 ":proprietary" 0.1})

(def opening-kinds #{":open-access" ":teaches" ":participates" ":hosts" ":performs"})
(def enclosure-kinds #{":encloses"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Node first-touch insertion order is tracked in the nodes map's ::node-order metadata
  (a vector of ids) so `node-ids` can replay Python's `for nid in nodes` dict order even
  past 8 keys (where array-map promotes to an unordered hash-map)."
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
                  (vary-meta nodes' update ::node-order (fnil conj []) id))))
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update acc :edges conj f)
       :else acc))
   {:nodes (with-meta (array-map) {::node-order []}) :edges []}
   forms))

(defn node-ids
  "Node ids in first-touch insertion order (Python `for nid in nodes` dict order).
  Falls back to (keys nodes) if no ::node-order metadata is present."
  [nodes]
  (or (::node-order (meta nodes)) (keys nodes)))

#?(:clj
   (defn load-file*
     "Read + parse an asobi EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/access-load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/access-load")]
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
   {\"openness\" {node v} \"enclosure\" {node v} \"enclosure_out\" {holder v}}.

   openness[node]        = Σ incident inbound OPENING load × disclosed access weight of bearer
   enclosure[node]       = Σ incident inbound :encloses load (the 取 borne; routed to opening)
   enclosure_out[holder] = Σ outbound :encloses load (the 取-holder gating play)

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable
   sort in `rank` ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (loop [es edges
         openness (ordered-map) enclosure (ordered-map) enclosure-out (ordered-map)]
    (if (empty? es)
      {"openness" openness
       "enclosure" enclosure
       "enclosure_out" enclosure-out}
      (let [e (first es)
            kind (get e ":en/kind")
            load- (->load e)
            src (get e ":en/from")
            dst (get e ":en/to")]
        (cond
          (contains? opening-kinds kind)
          (let [bearer (get nodes dst {})
                w (get access-weight (get bearer ":work/access") 0.6)]
            (recur (rest es)
                   (omap-update openness dst #(+ % (* load- w)))
                   enclosure
                   enclosure-out))

          (contains? enclosure-kinds kind)
          (recur (rest es)
                 openness
                 (omap-update enclosure dst #(+ % load-))
                 (omap-update enclosure-out src #(+ % load-)))

          :else
          (recur (rest es) openness enclosure enclosure-out))))))

(defn opening-priority
  "Per-node OPENING priority by the openness↔enclosure BALANCE: enclosure − openness. `analyze`
  reports enclosure (the 取 borne) and openness separately, but a work can be heavily enclosed in one
  venue AND openly accessible in another (e.g. a film paywalled on a platform yet screened free in
  the public domain), so enclosure alone overstates the priority. The NET (enclosure minus openness)
  surfaces the works gated MORE than they are open — the real opening priority — while a negative net
  marks the accessible exemplars (net-open). An ACCESS map, never an engagement / popularity ranking
  (G1; the 取-holder is the enclosure, routed to OPENING, no retention metric, G8); aggregate +
  edge-primary, reading the on-read integrals (G2, no stored score). Takes an `analyze` result + the
  nodes; returns [node net enclosure openness label] by net descending."
  ([analysis nodes] (opening-priority analysis nodes 20))
  ([analysis nodes limit]
   (let [openness (get analysis "openness" {})
         enclosure (get analysis "enclosure" {})
         ids (distinct (concat (keys enclosure) (keys openness)))]
     (->> ids
          (map (fn [id]
                 (let [e (double (get enclosure id 0.0))
                       o (double (get openness id 0.0))]
                   [id (- e o) e o (get-in nodes [id ":organism/label"] id)])))
          (sort-by (fn [[_ net _ _ _]] (- net)))
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
  "Render the freed-time participation report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-work (count-kind nodes #{":work"})
        n-prac (count-kind nodes #{":practice"})
        n-enc (count-kind nodes #{":enclosure"})
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# asobi 遊び — freed-time participation report (aggregate-first)\n")
    (conj! L (str "> **G1 — PARTICIPATION / ACCESS map, NEVER an engagement ranking.** No retention "
                  "metric, no recommend-for-time, no popularity score. The 取-holder is the "
                  "ENCLOSURE; the bearer is the play; the routing is OPENING. Access categories are "
                  "DISCLOSED, not asobi verdicts (N3). karma lives only on edges, on read (N1).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-work " works · " n-prac
                  " practices · " n-enc " enclosures) · " (count edges) " 縁 · "
                  auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Participation-openness — the freed-time access surface to widen\n")
    (conj! L "_Σ incident opening-load × disclosed access weight; the commons to keep open._\n")
    (conj! L "| rank | node | access | participation-openness |")
    (conj! L "|---:|---|:--:|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "openness") nodes))]
      (let [acc0 (get-in nodes [nid ":work/access"])
            acc (if (or (nil? acc0) (false? acc0)) "—" acc0)
            label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (lstrip-colon (str acc)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Enclosure concentration — 取-holders gating the freed-time telos\n")
    (conj! L (str "_Σ outbound enclosure-load; cross-link to tsumugi/danjo where a power-entity "
                  "operates the enclosure (accountability, aggregate-first). Routed to OPENING._\n"))
    (conj! L "| rank | enclosure | kind | gating-load |")
    (conj! L "|---:|---|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "enclosure_out") nodes))]
      (let [kind0 (get-in nodes [nid ":enclosure/kind"])
            kind (if (or (nil? kind0) (false? kind0)) "—" kind0)
            label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (lstrip-colon (str kind)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Enclosed play — works/events bearing the most enclosure (open these)\n")
    (conj! L "| rank | node | enclosure-load |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "enclosure") nodes 12))]
      (let [label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |"))))

    (conj! L (str "\n---\n_asobi 遊び · ADR-2606073200 · mirror-only · non-adjudicating · "
                  "edge-primary · opening-routed · no-addictive-design. Live ingest is "
                  "G7/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/participation-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-asobi-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "participation-report.md") (report-md nodes edges res))
       (println (str "asobi: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "participation-report.md")))
       (when-let [top (first (rank (get res "openness") nodes 1))]
         (println (str "  top participation-openness: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
