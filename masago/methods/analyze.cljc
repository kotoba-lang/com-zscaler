(ns masago.methods.analyze
  "masago 真砂 — open materials-discovery (公開材料) KG-mirror analyzer.
  Clojure / kotoba-datomic native (ADR-2606151027).

  Reads a kotoba-EDN open-materials graph (:mat/* nodes — :material / :element / :property /
  :application / :dataset-source — + :en/* 縁 over the open-materials-ontology) and emits:

    1. an aggregate-first discovery report (out/discovery-report.md) — the materials bearing the
       most disclosed computed evidence, the application classes with the most candidate evidence,
       and the composition breadth — routed to RESEARCH, never a make/buy decision.
    2. an honest coverage report (out/coverage-report.md) — coverage vs the ~10^8 open-materials
       commons (≈0 by design), with a gap map (G5).
    3. the canonical kotoba Datom log (out/materials-datoms.kotoba.edn) — ground EAVT
       [e a v tx :add] + derived readouts flagged transient (N1/G2, ADR-2605312345).

  CONSTITUTIONAL framing:
    G1 — RESEARCH map, NEVER a weapons-design or synthesis-recipe tool. Computed PROPERTIES +
         crystal STRUCTURES only. A synthesis-route / precursor / enrichment field is REJECTED
         (raises ex-info — the Clojure analogue of ValueError); weaponizable :application/class
         values are likewise REJECTED. Fabrication + force are structurally unrepresentable (§1.12).
    G2 — edge-primary (N1). Discovery evidence lives ONLY on edges (:en/grasping-load × disclosed
         :en/confidence). A material's discovery-priority is the INTEGRAL of its incident
         :has-property + :candidate-for 縁, computed on read — never a stored :material/score.
         The raw computed VALUE rides the edge as :en/value (DISCLOSED, never re-judged, N3).
    G3 — non-adjudicating. Property values + provenance (DFT/MLIP/experimental) are DISCLOSED
         source facts, never masago verdicts. No make/buy/trade decision; no :verdict route.
    G4 — open-license only. Every :dataset-source carries an OPEN license (REJECTED otherwise).
    G6 — Murakumo-only narration; NO MLIP/ML model execution here (R0/R1 mirror disclosed values;
         model execution is R2+ on owned/donated compute, never commercial GPU rental).

  House style (matches the hotaru/nusa Tier-B Clojure ports): EDN ':…' keyword strings stay
  strings (incl. all :mat/* / :material/* / :en/* attrs); pure fns; file I/O only at edges via
  clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil) ──────
;; Keywords are kept as ":ns/name" strings (NOT clojure keywords) so the whole pipeline stays
;; string-keyed (shared convention with the hotaru/nusa ports).

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
            (if (nil? t) (step) (cons t (step))))))))))

(defn atom-of
  "\"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string; int → long;
  else double; else raw string."
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
  "Consume one form from the token vector at index i. Returns [value next-i]."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)] (recur i (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

#?(:clj
   (defn load-edn
     "Read + parse an open-materials EDN graph file. File I/O only at this edge."
     [path]
     (read-edn (slurp (str path)))))

;; ── classify the flat datom vector into nodes + edges ────────────────────────
(defn classify
  "Return [nodes edges] — nodes a map keyed by :mat/id, edges a vector (read order)."
  [rows]
  (reduce
   (fn [[nodes edges] r]
     (cond
       (not (map? r)) [nodes edges]
       (contains? r ":mat/id") [(assoc nodes (get r ":mat/id") r) edges]
       (and (contains? r ":en/from") (contains? r ":en/to")) [nodes (conj edges r)]
       :else [nodes edges]))
   [{} []]
   rows))

;; ── constitutional invariants (the three places: schema :db/allowed = lexicon enum = here) ────
;; DISCLOSED provenance confidence → representative evidence weight (mirrors schema :confidence/weight)
(def confidence-weight
  {":experimental" 1.0 ":dft" 0.8 ":mlip-predicted" 0.6 ":mlip-screened" 0.5 ":estimated" 0.3})
(def ^:private default-conf 0.5)  ;; missing confidence → treat as MLIP-screened (conservative)

;; G1: synthesis/recipe fields are structurally unrepresentable — refuse any that appear.
(def forbidden-node-attrs
  #{":synthesis/route" ":synthesis/precursor" ":precursor/list"
    ":enrichment/route" ":processing/route" ":recipe"})
;; G1: weaponizable application classes are unrepresentable.
(def forbidden-app-classes
  #{":weapon" ":energetic" ":explosive" ":propellant" ":warhead" ":fissile" ":enrichment"})

(defn- open-license? [lic]
  (let [l (str/lower-case (str (or lic "")))]
    (and (seq l) (or (str/includes? l "cc-by") (str/includes? l "open")))))

(defn screen
  "G1/G4 enforcement point: refuse a graph that is not charter-clean. Raises ex-info on:
    - any node carrying a synthesis-route / precursor / enrichment field (G1),
    - any :application node whose :application/class is weaponizable (G1),
    - any :dataset-source whose :source/license is not open (G4).
  Returns the DISCLOSED provenance-confidence breakdown (a map confidence→edge-count)."
  [nodes edges]
  (doseq [[nid n] nodes]
    (when-let [bad (seq (filter forbidden-node-attrs (keys n)))]
      (throw (ex-info (str "G1 violation: node " (pr-str nid) " carries synthesis-route field(s) "
                           (pr-str (vec bad)) "; masago mirrors computed properties + structures "
                           "ONLY (no synthesis/precursor/enrichment recipe is representable).")
                      {:g1-violation true :node nid :attrs (vec bad)})))
    (when (= ":application" (get n ":mat/kind"))
      (let [cls (get n ":application/class")]
        (when (forbidden-app-classes cls)
          (throw (ex-info (str "G1 violation: application " (pr-str nid) " has weaponizable class "
                               (pr-str cls) "; weaponizable application classes are unrepresentable.")
                          {:g1-violation true :node nid :class cls})))))
    (when (= ":dataset-source" (get n ":mat/kind"))
      (when-not (open-license? (get n ":source/license"))
        (throw (ex-info (str "G4 violation: dataset-source " (pr-str nid) " has non-open license "
                             (pr-str (get n ":source/license")) "; only openly-licensed sources "
                             "(CC-BY / open) are ingestible.")
                        {:g4-violation true :node nid :license (get n ":source/license")})))))
  (reduce (fn [m e]
            (if-let [c (get e ":en/confidence")]
              (update m c (fnil inc 0))
              m))
          {} edges))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2). Calls screen first (G1/G4).

    discovery[material] = Σ incident (:has-property + :candidate-for) load × confidence weight.
    readiness[app]      = Σ inbound :candidate-for load × confidence weight.
    breadth[node]       = Σ incident :composed-of / :candidate-for / :similar-to load."
  [nodes edges]
  (let [confidence (screen nodes edges)]
    (loop [es edges, discovery {}, readiness {}, breadth {}]
      (if-let [e (first es)]
        (let [k (get e ":en/kind")
              load- (double (or (get e ":en/grasping-load") 0.0))
              w (get confidence-weight (get e ":en/confidence") default-conf)
              src (get e ":en/from"), dst (get e ":en/to")]
          (cond
            (or (= k ":has-property") (= k ":candidate-for"))
            (recur (rest es)
                   (update discovery src (fnil + 0.0) (* load- w))
                   (if (= k ":candidate-for") (update readiness dst (fnil + 0.0) (* load- w)) readiness)
                   (if (= k ":candidate-for")
                     (-> breadth (update src (fnil + 0.0) load-) (update dst (fnil + 0.0) load-))
                     breadth))
            (or (= k ":composed-of") (= k ":similar-to"))
            (recur (rest es) discovery readiness
                   (-> breadth (update src (fnil + 0.0) load-) (update dst (fnil + 0.0) load-)))
            :else (recur (rest es) discovery readiness breadth)))
        {:discovery discovery :readiness readiness :breadth breadth :confidence confidence}))))

;; ── helpers ──────────────────────────────────────────────────────────────────
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- kind-count [nodes k]
  (count (filter #(= k (get % ":mat/kind")) (vals nodes))))

(defn- rank
  "Top-n [id label score] rows, score-desc then id-asc (deterministic)."
  ([d nodes] (rank d nodes 20))
  ([d nodes n]
   (->> d
        (sort-by (fn [[id v]] [(- (double v)) id]))
        (take n)
        (mapv (fn [[id v]] [id (get-in nodes [id ":mat/label"] id) v])))))

(defn- fmt3 [v] (format "%.3f" (double v)))

;; ── discovery report (markdown) ──────────────────────────────────────────────
(defn render-report
  [nodes edges a]
  (let [L (transient [])
        P #(conj! L %)
        auth (count (filter #(= ":authoritative" (get % ":mat/sourcing")) (vals nodes)))]
    (P "# masago 真砂 — open-materials discovery-priority report (aggregate-first)")
    (P "")
    (P (str "> **G1 — RESEARCH map, NEVER a weapons-design or synthesis-recipe tool.** masago "
            "mirrors computed PROPERTIES + STRUCTURES only; no synthesis route, no precursor, no "
            "enrichment procedure; weaponizable application classes are not representable. Property "
            "values + provenance (DFT/MLIP/experimental) are DISCLOSED source facts, not masago "
            "verdicts (N3). Discovery evidence lives only on edges, integrated on read (N1); routed "
            "to RESEARCH, never a make/buy decision."))
    (P "")
    (P (str "**Graph**: " (count nodes) " nodes (" (kind-count nodes ":material") " materials · "
            (kind-count nodes ":element") " elements · " (kind-count nodes ":property")
            " property types · " (kind-count nodes ":application") " applications · "
            (kind-count nodes ":dataset-source") " sources) · " (count edges) " 縁 · "
            auth "/" (count nodes) " :authoritative"))
    (P "")
    (P "## Material discovery-priority — materials bearing the most disclosed computed evidence")
    (P "")
    (P (str "_Σ incident computed-property + application-candidacy load × disclosed confidence "
            "weight; routed to research, never a make/buy decision._"))
    (P "")
    (P "| rank | material | formula | system | discovery-priority |")
    (P "|---:|---|---|---|---:|")
    (doseq [[i [id label v]] (map-indexed vector (rank (:discovery a) nodes))]
      (let [n (get nodes id)]
        (P (str "| " (inc i) " | " label " | " (get n ":material/formula" "—") " | "
                (lstrip-colon (get n ":material/crystal-system" "—")) " | " (fmt3 v) " |"))))
    (P "")
    (P "## Application readiness — application classes with the most candidate evidence")
    (P "")
    (P "_Σ inbound candidacy load × disclosed confidence; where the open commons concentrates candidate materials._")
    (P "")
    (P "| rank | application | readiness |")
    (P "|---:|---|---:|")
    (doseq [[i [_ label v]] (map-indexed vector (rank (:readiness a) nodes))]
      (P (str "| " (inc i) " | " label " | " (fmt3 v) " |")))
    (P "")
    (P "## Composition breadth — nodes woven most broadly into the graph")
    (P "")
    (P "| rank | node | kind | breadth |")
    (P "|---:|---|---|---:|")
    (doseq [[i [id label v]] (map-indexed vector (rank (:breadth a) nodes 12))]
      (P (str "| " (inc i) " | " label " | " (lstrip-colon (get-in nodes [id ":mat/kind"] "—")) " | " (fmt3 v) " |")))
    (P "")
    (P (str "---"))
    (P (str "_masago 真砂 · ADR-2606151027 · mirror-only · non-adjudicating · edge-primary · "
            "research-routed. Live ingest (Materials Project REST / OMat24 dumps) is G7/Council-gated; "
            "MLIP model execution is R2+ (owned/donated compute only, G6)._"))
    (str/join "\n" (persistent! L))))

;; ── coverage report (markdown) ───────────────────────────────────────────────
(def ^:private material-denom
  [["Materials Project entries (~)" 170000]
   ["OQMD entries (~)" 1200000]
   ["OMat24 DFT single-point calculations (~)" 110000000]])
(def ^:private applications
  [":battery-cathode" ":battery-anode" ":solid-electrolyte" ":semiconductor" ":photovoltaic"
   ":catalyst" ":thermoelectric" ":superconductor" ":dielectric" ":structural" ":magnet"
   ":hydrogen-storage"])
(def ^:private property-kinds
  [":formation-energy" ":energy-above-hull" ":band-gap" ":bulk-modulus" ":shear-modulus"
   ":density" ":ionic-conductivity" ":seebeck" ":magnetization" ":dielectric" ":critical-temperature"])
(def ^:private crystal-systems
  [":cubic" ":tetragonal" ":orthorhombic" ":hexagonal" ":trigonal" ":monoclinic" ":triclinic"])
(def ^:private source-ids
  [":omat24" ":materials-project" ":oqmd" ":nomad" ":aflow" ":jarvis"])
(def ^:private confidence-cats
  [":experimental" ":dft" ":mlip-predicted" ":mlip-screened" ":estimated"])
(def ^:private thin 2)

(defn render-coverage
  [nodes edges]
  (let [L (transient [])
        P #(conj! L %)
        nmat (kind-count nodes ":material")
        app-c (frequencies (keep #(get % ":application/class") (vals nodes)))
        prop-c (frequencies (keep #(get % ":property/kind") (vals nodes)))
        sys-c (frequencies (keep #(get % ":material/crystal-system") (vals nodes)))
        src-c (frequencies (keep (fn [n] (when (= ":dataset-source" (get n ":mat/kind"))
                                           (str ":" (last (str/split (get n ":mat/id" "") #"\." 2)))))
                                 (vals nodes)))
        conf-c (frequencies (keep #(get % ":en/confidence") edges))]
    (P "# masago 真砂 — open-materials coverage report")
    (P "")
    (P (str "> Honest denominator: coverage of the full ~10^8 open-materials commons is ~0 by design "
            "(bounded seed). This names the application/property backbone covered and the next-wave "
            "gaps. PUBLIC computed-materials data only — no synthesis routes (G1)."))
    (P "")
    (P (str "**Seed**: " nmat " materials · " (kind-count nodes ":element") " elements · "
            (kind-count nodes ":property") " property types · " (kind-count nodes ":application")
            " applications · " (kind-count nodes ":dataset-source") " sources · " (count edges) " 縁"))
    (P "")
    (P "## Material coverage vs denominators")
    (P "")
    (P "| denominator | count | seed | fraction |")
    (P "|---|---:|---:|---:|")
    (doseq [[name denom] material-denom]
      (P (str "| " name " | " denom " | " nmat " | " (format "%.2e" (/ (double nmat) denom)) " |")))
    (P "")
    (P "## Provenance-confidence spread (DISCLOSED facts, not verdicts)")
    (P "")
    (P "| confidence | edges |")
    (P "|:--:|---:|")
    (doseq [c confidence-cats] (P (str "| " (lstrip-colon c) " | " (get conf-c c 0) " |")))
    (letfn [(bucket [title ks counter]
              (P "") (P (str "## " title)) (P "")
              (P "| bucket | count | status |") (P "|---|---:|:--|")
              (doseq [k ks]
                (let [c (get counter k 0)
                      status (cond (zero? c) "— **MISSING**" (< c thin) "⚠ thin" :else "ok")]
                  (P (str "| " (lstrip-colon k) " | " c " | " status " |")))))]
      (bucket "Application-class coverage" applications app-c)
      (bucket "Property-type coverage" property-kinds prop-c)
      (bucket "Crystal-system coverage" crystal-systems sys-c)
      (bucket "Dataset-source coverage" source-ids src-c))
    (let [missing (concat (remove #(pos? (get app-c % 0)) applications)
                          (remove #(pos? (get prop-c % 0)) property-kinds)
                          (remove #(pos? (get sys-c % 0)) crystal-systems)
                          (remove #(pos? (get src-c % 0)) source-ids))]
      (P "") (P "## Gap map — next-wave targets") (P "")
      (P (if (seq missing)
           (str "Missing buckets: " (str/join ", " (map lstrip-colon missing)) ".")
           "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (P "") (P "---") (P "_masago 真砂 · ADR-2606151027 · coverage honesty (G5)._")
    (str/join "\n" (persistent! L))))

;; ── kotoba Datom log (canonical EAVT state, ADR-2605312345) ──────────────────
(def ^:private node-attrs
  [":mat/kind" ":mat/label" ":mat/sourcing" ":mat/links"
   ":material/formula" ":material/spacegroup" ":material/crystal-system" ":material/source-id"
   ":element/symbol" ":element/z" ":property/kind" ":property/unit" ":application/class"
   ":source/license" ":source/doi" ":source/url"])
(def ^:private edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/grasping-load" ":en/value" ":en/confidence" ":en/sourcing"])

(defn- fmtv
  "EAVT value formatter: keyword-strings as-is, other strings quoted, numbers literal."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":") v
                    (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (integer? v) (str v)
    (float? v) (let [s (format "%.6g" (double v))]
                 (if (str/includes? s ".")
                   (-> s (str/replace #"0+$" "") (str/replace #"\.$" "")) s))
    :else (str v)))

(defn- g [v] (let [s (format "%.6g" (double v))]
               (if (str/includes? s ".") (-> s (str/replace #"0+$" "") (str/replace #"\.$" "")) s)))

(defn render-datoms
  "Render the canonical kotoba Datom log EDN: ground [e a v tx :add] + derived (transient, N1/G2)."
  [nodes edges a tx]
  (let [L (transient [])
        P #(conj! L %)]
    (P ";; masago 真砂 — GENERATED kotoba Datom log (ADR-2606151027). DO NOT hand-edit.")
    (P ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
    (P ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).")
    (P ";; G1: PUBLIC computed-materials data only — properties + structures; no synthesis routes.")
    (P "[")
    ;; GROUND: node datoms (sorted id → deterministic)
    (doseq [nid (sort (keys nodes))]
      (let [n (get nodes nid)]
        (doseq [att node-attrs]
          (when (some? (get n att))
            (P (str "[" (fmtv nid) " " att " " (fmtv (get n att)) " " tx " :add]"))))))
    ;; GROUND: edge datoms (content-stable id en.<from>.<kind>.<to>)
    (doseq [e (sort-by (fn [e] [(get e ":en/kind") (get e ":en/from") (get e ":en/to")]) edges)]
      (let [eid (str "en." (get e ":en/from") "." (lstrip-colon (get e ":en/kind")) "." (get e ":en/to"))]
        (doseq [att edge-attrs]
          (when (some? (get e att))
            (P (str "[" (fmtv eid) " " att " " (fmtv (get e att)) " " tx " :add]"))))))
    ;; DERIVED (transient — NOT persisted; N1/G2)
    (P ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
    (doseq [[id v] (sort-by (fn [[id v]] [(- (double v)) id]) (:discovery a))]
      (P (str "[" (fmtv id) " :bond/discovery-priority " (g v) " " tx " :derived] ;; :bond/is-transient true")))
    (doseq [[id v] (sort-by (fn [[id v]] [(- (double v)) id]) (:readiness a))]
      (P (str "[" (fmtv id) " :bond/application-readiness " (g v) " " tx " :derived] ;; :bond/is-transient true")))
    (doseq [[id v] (sort-by (fn [[id v]] [(- (double v)) id]) (:breadth a))]
      (P (str "[" (fmtv id) " :bond/composition-breadth " (g v) " " tx " :derived] ;; :bond/is-transient true")))
    (P "]")
    (str (str/join "\n" (persistent! L)) "\n")))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/{discovery-report.md, coverage-report.md,
     materials-datoms.kotoba.edn}."
     [& argv]
     (let [argv (vec argv)
           methods-dir (if (and *file* (not= *file* "NO_SOURCE_PATH"))
                         (-> *file* clojure.java.io/file .getParentFile)
                         (clojure.java.io/file "20-actors" "masago" "methods"))
           actor-dir (.getParentFile methods-dir)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file actor-dir "data" "seed-open-materials-graph.kotoba.edn"))
           out (if (some #{"--out"} argv)
                 (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                 (clojure.java.io/file methods-dir "out"))
           [nodes edges] (classify (load-edn seed))
           a (analyze nodes edges)]
       (.mkdirs out)
       (spit (clojure.java.io/file out "discovery-report.md") (render-report nodes edges a))
       (spit (clojure.java.io/file out "coverage-report.md") (render-coverage nodes edges))
       (spit (clojure.java.io/file out "materials-datoms.kotoba.edn") (render-datoms nodes edges a 1))
       (let [top (first (rank (:discovery a) nodes 1))]
         (println (str "masago: " (count nodes) " nodes, " (count edges) " 縁"
                       (when top (str " · top discovery-priority: " (second top) " (" (fmt3 (nth top 2)) ")"))
                       " → " out)))
       0)))
