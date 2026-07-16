(ns tokigusuri.methods.analyze
  "tokigusuri 時薬 — edge-primary pharmaceutical patent-cliff / off-patent-access analyzer.
  Structural sibling of hokorobi.methods.analyze (ADR-2606073400); pharma-access scope.

  Reads a kotoba-EDN pharma-patent graph (:organism/* nodes + :en/* 縁 over the
  pharma-patent-ontology) and surfaces — aggregate-first — where ACCESS-BARRIER concentrates
  (which essential medicines are gated by remaining exclusivity = the release surface) vs where
  RELEASE buffers (generic / biosimilar / expiry availability) restore access, routed to
  RELEASE (解放 — the liberation of the medicine to all).

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. access-barrier lives ONLY on edges (:en/barrier-load). A node's
      access-barrier-concentration is the INTEGRAL of its incident inbound barrier 縁 (severity ×
      disclosed essentiality weight) — computed on READ, never a stored per-drug score. There is
      no :tokigusuri/monopoly-of-drug.
    G1 — RELEASE map, never a patent-busting / infringement-inducement tool, never a freedom-to-
      operate legal opinion, never an investment / short signal, never a per-company verdict. The
      取-holder is the EXCLUSIVITY-BARRIER; the bearer is patients / the public; the routing is
      LAWFUL release (generic/biosimilar on off-patent + disclosed MPP / TRIPS routes on-patent).
    N3 — non-adjudicating. patent / exclusivity / expiry are DISCLOSED facts (FDA Orange Book,
      patent registers, WHO EML, Medicines Patent Pool), never tokigusuri verdicts; no FTO /
      infringement determination, no investment advice.

  House style: Python ':…' keyword strings stay strings (incl. all :organism/* / :en/* attrs);
  pure fns; file I/O only at edges via clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil)
;; Keywords are kept as ":ns/name" strings (NOT clojure keywords) so the whole pipeline stays
;; string-keyed and deterministic across hosts.

(def ^:private tok-re
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
  "\"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string; int → long;
  else float; else raw string."
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
  [end-marker next-i] when a closing ] or } is hit."
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
  "Parse the first top-level form from EDN text."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── disclosed essentiality tier → representative access weight (NOT a verdict; mirrors schema)
(def essentiality-weight
  {":eml-core" 1.0 ":eml-complementary" 0.7 ":on-market" 0.4 ":niche" 0.2})

(def barrier-kinds #{":monopolizes" ":blocks" ":evergreens" ":delays" ":gates-access"})
(def release-kinds #{":generic-of" ":biosimilar-of" ":supplies" ":overcomes"})

;; The 取-holder lens (G1): only an EXCLUSIVITY-HOLDER (a :barrier or an :originator :holder)
;; can be a barrier SOURCE. :gates-access is drug→bearer — it still loads the bearer's barrier
;; concentration (the public bears it), but the medicine itself is NEVER tallied as the
;; 取-holder. A drug is the gated object, never the villain.
(def ^:private holder-imposed-kinds #{":monopolizes" ":blocks" ":evergreens" ":delays"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  Insertion order of nodes is preserved via ::node-order metadata (read back with `node-ids`)."
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
  "Node ids in first-touch insertion order (the seed-file order). Falls back to (keys nodes)."
  [nodes]
  (or (::node-order (meta nodes)) (keys nodes)))

#?(:clj
   (defn load-file*
     "Read + parse a pharma-patent EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/barrier-load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/barrier-load")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn- ordered-map
  "Wrap a plain map with first-touch insertion order in ::order metadata (mirroring a Python
  defaultdict; array-map only preserves order ≤8 keys, so order is tracked explicitly)."
  []
  ^{::order []} {})

(defn- omap-update
  "update an ordered-map: apply f at k (default 0.0 via fnil), recording k's first-touch order."
  [m k f]
  (let [had? (contains? m k)
        m' (update m k (fnil f 0.0))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2). Returns
   {\"barrier\" {node v} \"release\" {node v} \"barrier_out\" {source v}}.

   barrier[node]     = Σ incident inbound barrier-load × disclosed essentiality weight
   release[node]     = Σ incident inbound :generic-of/:biosimilar-of/:supplies/:overcomes load
   barrier_out[src]  = Σ outbound barrier-load (the 取-holder exclusivity-barrier source)

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable sort in
   `rank` ties exactly the seed iteration order."
  [nodes edges]
  (loop [es edges
         barrier (ordered-map) release (ordered-map) barrier-out (ordered-map)]
    (if (empty? es)
      {"barrier" barrier
       "release" release
       "barrier_out" barrier-out}
      (let [e (first es)
            kind (get e ":en/kind")
            load- (->load e)
            src (get e ":en/from")
            dst (get e ":en/to")]
        (cond
          (contains? barrier-kinds kind)
          (let [target (get nodes dst {})
                w (get essentiality-weight (get target ":drug/essentiality") 0.5)]
            (recur (rest es)
                   (omap-update barrier dst #(+ % (* load- w)))
                   release
                   ;; only a true 取-holder (barrier/originator) is a source; a drug gating
                   ;; access to the public (:gates-access) is the gated object, not the holder.
                   (if (contains? holder-imposed-kinds kind)
                     (omap-update barrier-out src #(+ % load-))
                     barrier-out)))

          (contains? release-kinds kind)
          (recur (rest es)
                 barrier
                 (omap-update release dst #(+ % load-))
                 barrier-out)

          :else
          (recur (rest es) barrier release barrier-out))))))

(defn- omap-items
  "Items of an ordered-map in first-touch order (falls back to seq order if no ::order)."
  [d]
  (let [order (::order (meta d))]
    (if order
      (map (fn [k] [k (get d k)]) order)
      (seq d))))

(defn rank
  "Top-`limit` (id, label, value) rows of d, sorted by -value only (STABLE — ties keep
  first-touch insertion order)."
  ([d nodes] (rank d nodes 20))
  ([d nodes limit]
   (->> (sort-by (fn [[_ v]] (- v)) (omap-items d))
        (take limit)
        (mapv (fn [[nid v]]
                [nid (get-in nodes [nid ":organism/label"] nid) v])))))

;; ── report rendering ─────────────────────────────────────────────────────────

(defn- fmt3 [v] (format "%.3f" (double v)))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":organism/kind")) (vals nodes))))

(defn report-md
  "Render the pharmaceutical patent-cliff / access report markdown."
  [nodes edges res]
  (let [n-drug (count-kind nodes ":drug")
        n-barrier (count-kind nodes ":barrier")
        n-holder (count-kind nodes ":holder")
        n-bear (count-kind nodes ":bearer")
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# tokigusuri 時薬 — pharmaceutical patent-cliff / off-patent-access report (aggregate-first)\n")
    (conj! L (str "> **G1 — RELEASE map, NEVER a patent-busting / FTO-opinion / trading signal.** No "
                  "freedom-to-operate determination, no infringement verdict, no per-company verdict, no "
                  "pharma-equity signal. The 取-holder is the exclusivity-barrier; the bearer is patients / "
                  "the public; the routing is LAWFUL release (解放 — generic/biosimilar on off-patent + "
                  "disclosed MPP / TRIPS routes on-patent). patent / exclusivity / expiry are DISCLOSED "
                  "(WHO EML / Orange Book / MPP), not tokigusuri verdicts (N3). Barrier lives only on "
                  "edges, integrated on read (N1).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-drug " drugs · " n-barrier
                  " exclusivity-barriers · " n-holder " holders · " n-bear " bearers) · "
                  (count edges) " 縁 · " auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Access-barrier concentration — essential medicines most gated by remaining exclusivity (the release surface)\n")
    (conj! L "_Σ incident inbound barrier-load × disclosed essentiality weight; routed to RELEASE._\n")
    (conj! L "| rank | drug / bearer | essentiality | access-barrier |")
    (conj! L "|---:|---|:--:|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "barrier") nodes))]
      (let [ess0 (get-in nodes [nid ":drug/essentiality"])
            ess (if (or (nil? ess0) (false? ess0)) "—" ess0)]
        (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid)
                      " | " (lstrip-colon (str ess)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Exclusivity-barrier concentration — 取-holders imposing the most access-barrier\n")
    (conj! L "_Σ outbound barrier-load; the exclusivity that gates generic entry, routed to release._\n")
    (conj! L "| rank | barrier / holder | kind | imposed-load |")
    (conj! L "|---:|---|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "barrier_out") nodes))]
      (let [kind0 (or (get-in nodes [nid ":barrier/kind"]) (get-in nodes [nid ":holder/kind"]))
            kind (if (or (nil? kind0) (false? kind0)) "—" kind0)]
        (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid)
                      " | " (lstrip-colon (str kind)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Release buffers — generic / biosimilar availability (the liberation 解放)\n")
    (conj! L "| rank | node | release-buffer |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid _ v]] (map-indexed vector (rank (get res "release") nodes 12))]
      (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid) " | " (fmt3 v) " |")))

    (conj! L (str "\n---\n_tokigusuri 時薬 · ADR-2606171300 · mirror-only · observation→handoff · "
                  "non-adjudicating · no-FTO-opinion · edge-primary · release-routed. Live ingest "
                  "(Orange Book / WHO EML / Medicines Patent Pool) is G7/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/patent-cliff-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-pharma-patent-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "patent-cliff-report.md") (report-md nodes edges res))
       (println (str "tokigusuri: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "patent-cliff-report.md")))
       (when-let [top (first (rank (get res "barrier") nodes 1))]
         (println (str "  top access-barrier concentration: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
