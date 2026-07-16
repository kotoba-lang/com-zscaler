(ns nusa.methods.analyze
  "nusa 幣 — ritual/industrial hemp heritage analyzer.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606039800).

  Reads a kotoba-EDN ritual-hemp heritage graph (:hemp/* low-THC cultivars, :rite/*
  purification artifacts, :rite.event/* ceremonies, :imbe/* provisioning lineage,
  :hemp.license/* cultivation-revival design) and emits:

    1. an aggregate-first heritage report (out/heritage-report.md) — the rites, the
       Imbe/aratae tradition, the cultivar THC-class breakdown, and the now-open
       low-THC licensed-cultivation space — framed toward heritage + 祓い/清め, never
       toward recreational use or 解禁 advocacy.
    2. the derived heritage datoms (out/ritual-hemp-graph.kotoba.edn), flagged
       :derived — never re-ingested as authoritative fact.

  CONSTITUTIONAL framing:
    G1 — every :hemp/* cultivar MUST be :thc-class :fiber | :low-thc. Any other class
         is REJECTED (raises ex-info — the Clojure analogue of ValueError); the data
         model + this analyzer cannot represent a recreational catalog. 嗜好THC
         (recreational THC) is STRUCTURALLY EXCLUDED (unrepresentable). (Enforcement #3.)
    G2 — no consumption/dosing output. G3 — non-adjudicating: legislative facts are
         recorded as facts; no 推進/反対 stance. G7 — all sourcing :representative.

  House style: Python ':…' keyword strings stay strings (incl. all :hemp/* / :rite/*
  / :imbe/* / :hemp.license/* attrs); pure fns; file I/O only at edges via clojure.java.io.
  Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, \"string\", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; \":ns/name\" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

;; G1: the ONLY permitted hemp THC classes. Anything else is a charter violation.
(def allowed-thc-classes [":fiber" ":low-thc"])

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

#?(:clj
   (defn load-edn
     "Read + parse a ritual-hemp EDN graph file. File I/O only at this edge."
     [path]
     (read-edn (slurp (str path)))))

;; ── classify the flat datom vector into entity buckets ──────────────────────
;; Insertion order of each bucket is preserved (array-map / ::order) to match
;; Python dict iteration order (Counter increment order in screen-thc).

(defn- ordered-assoc
  "assoc k→v on a map carrying ::order metadata (first-touch key order, mirroring a
  Python dict). array-map only preserves order ≤8 keys, so we track order explicitly."
  [m k v]
  (let [had? (contains? m k)
        m' (assoc m k v)]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order (fnil conj []) k)))))

(defn- omap-keys
  "Keys of an ordered map in first-touch order (falls back to seq order if no ::order)."
  [m]
  (or (::order (meta m)) (keys m)))

(defn classify
  "Return [hemp rites events imbe licenses] — each an insertion-ordered map keyed by id."
  [rows]
  (reduce
   (fn [[hemp rites events imbe licenses] r]
     (cond
       (not (map? r)) [hemp rites events imbe licenses]
       (contains? r ":hemp/id")
       [(ordered-assoc hemp (get r ":hemp/id") r) rites events imbe licenses]
       (contains? r ":rite/id")
       [hemp (ordered-assoc rites (get r ":rite/id") r) events imbe licenses]
       (contains? r ":rite.event/id")
       [hemp rites (ordered-assoc events (get r ":rite.event/id") r) imbe licenses]
       (contains? r ":imbe/id")
       [hemp rites events (ordered-assoc imbe (get r ":imbe/id") r) licenses]
       (contains? r ":hemp.license/id")
       [hemp rites events imbe (ordered-assoc licenses (get r ":hemp.license/id") r)]
       :else [hemp rites events imbe licenses]))
   [(with-meta {} {}) (with-meta {} {}) (with-meta {} {})
    (with-meta {} {}) (with-meta {} {})]
   rows))

(defn screen-thc
  "G1 enforcement point #3: refuse any cultivar that is not fibre/low-THC.

  Returns the cultivar THC-class breakdown as an insertion-ordered map (Counter analogue,
  first-touch increment order). Raises ex-info on a prohibited class so the analyzer cannot
  silently render a recreational catalog."
  [hemp]
  (reduce
   (fn [breakdown hid]
     (let [h (get hemp hid)
           cls (get h ":hemp/thc-class")]
       (when-not (some #{cls} allowed-thc-classes)
         (throw (ex-info
                 (str "G1 violation: cultivar " (pr-str hid) " has :thc-class " (pr-str cls) "; "
                      "only " (pr-str (vec allowed-thc-classes)) " are permitted (nusa is fibre/ritual-only, "
                      "recreational/high-THC excluded by construction).")
                 {:g1-violation true :cultivar hid :thc-class cls})))
       (ordered-assoc breakdown cls (inc (get breakdown cls 0)))))
   (with-meta {} {})
   (omap-keys hemp)))

(defn analyze
  "Returns the heritage analysis map (1:1 with Python analyze)."
  [hemp rites events imbe licenses]
  (let [thc-breakdown (screen-thc hemp)
        ;; rite → fibre source coverage (preserve rites insertion order; preserve uses-cultivar order)
        rite-fiber (reduce
                    (fn [m rid]
                      (let [r (get rites rid)
                            cs (filterv #(contains? hemp %) (or (get r ":rite/uses-cultivar") []))]
                        (ordered-assoc m rid cs)))
                    (with-meta {} {})
                    (omap-keys rites))
        ;; which rites each cultivar supplies — defaultdict(set), first-touch key order
        cultivar-rites (reduce
                        (fn [m rid]
                          (reduce (fn [m c]
                                    (ordered-assoc m c (conj (get m c #{}) rid)))
                                  m
                                  (get rite-fiber rid)))
                        (with-meta {} {})
                        (omap-keys rite-fiber))
        ;; licence design: only fibre/low-THC cultivars (must already hold by G1)
        licence-clean (reduce
                       (fn [m lid]
                         (let [lc (get licenses lid)
                               cls (get-in hemp [(get lc ":hemp.license/cultivar") ":hemp/thc-class"])]
                           (if (some #{cls} allowed-thc-classes)
                             (ordered-assoc m lid lc)
                             m)))
                       (with-meta {} {})
                       (omap-keys licenses))
        ;; G4/G5/G8 invariants present on every licence design
        licence-invariants-ok
        (every? (fn [lid]
                  (let [lc (get licence-clean lid)]
                    (and (= ":member" (get lc ":hemp.license/licensee-principal"))
                         (= ":member-okaimono" (get lc ":hemp.license/funding"))
                         (false? (get lc ":hemp.license/server-held-key"))
                         (true? (get lc ":hemp.license/outward-gated")))))
                (omap-keys licence-clean))
        purification (count (filter #(= ":purification" (get (get rites %) ":rite/purpose"))
                                    (omap-keys rites)))]
    {:thc-breakdown thc-breakdown
     :rite-fiber rite-fiber
     :cultivar-rites cultivar-rites
     :licence-clean licence-clean
     :licence-invariants-ok licence-invariants-ok
     :purification purification}))

;; ── report rendering (matches render_report's f-strings) ─────────────────────

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- pybool
  "Python str(bool): True/False."
  [b] (if b "True" "False"))

(defn render-report
  "Render the ritual-hemp heritage report markdown (1:1 with render_report)."
  [hemp rites events imbe licenses a]
  (let [L (transient [])
        thc (:thc-breakdown a)
        rite-fiber (:rite-fiber a)
        licence-clean (:licence-clean a)]
    (conj! L "# nusa 幣 — ritual/industrial hemp heritage report")
    (conj! L "")
    (conj! L (str "> ADR-2606039800 · **aggregate-first** · heritage + 祓い/清め framing. "
                  "Fibre + ritual + low-THC cultivation ONLY — recreational THC excluded by the "
                  "`:thc-class` invariant (G1); no 解禁 推進/反対 stance (G3); cannabis-derived "
                  "medicine out of scope (G10). All sourcing `:representative` — bounded illustrative "
                  "seed; legal references require primary-source (e-Gov 法令 / 官報) verification."))
    (conj! L "")
    (conj! L (str "- cultivars: **" (count hemp) "**  ·  ritual artifacts: **" (count rites) "**  "
                  "·  ceremonies: **" (count events) "**  ·  provisioning lineage: **" (count imbe) "**  "
                  "·  cultivation-license designs: **" (count licenses) "**"))
    (conj! L (str "- ritual artifacts whose purpose is `:purification`: **" (:purification a) "/" (count rites) "**"))
    (conj! L "")

    ;; ── G1: cultivar THC-class breakdown (the headline charter signal) ──
    (conj! L "## Cultivar THC-class breakdown (G1 invariant)")
    (conj! L "")
    (conj! L (str "Every cultivar is fibre/ritual low-THC. `:psychoactive` is not a representable "
                  "class — the data model cannot hold a recreational catalog."))
    (conj! L "")
    (conj! L "| THC class | cultivars |")
    (conj! L "|---|---:|")
    (doseq [cls (sort (omap-keys thc))]
      (conj! L (str "| `" cls "` | " (get thc cls) " |")))
    (conj! L "")
    (conj! L "| cultivar | name | THC class | fibre use | region |")
    (conj! L "|---|---|---|---|---|")
    (doseq [hid (sort (omap-keys hemp))]
      (let [h (get hemp hid)
            uses (str/join ", " (map lstrip-colon (or (get h ":hemp/fiber-use") [])))
            region (str/join ", " (or (get h ":hemp/region") []))]
        (conj! L (str "| `" hid "` | " (get h ":hemp/name" hid) " | `" (get h ":hemp/thc-class")
                      "` | " uses " | " region " |"))))
    (conj! L "")

    ;; ── rites + the aratae / Imbe tradition ──
    (conj! L "## Ritual artifacts (祓い・清め) and their fibre source")
    (conj! L "")
    (conj! L "| artifact | kind | purpose | material | fibre cultivar(s) |")
    (conj! L "|---|---|---|---|---|")
    (doseq [rid (sort (omap-keys rites))]
      (let [r (get rites rid)
            cs (str/join ", " (get rite-fiber rid []))]
        (conj! L (str "| " (get r ":rite/name" rid) " | `" (get r ":rite/kind") "` | "
                      "`" (get r ":rite/purpose") "` | `" (get r ":rite/material") "` | " cs " |"))))
    (conj! L "")

    (conj! L "## Imperial / ceremonial heritage (as-of history, 非終末論)")
    (conj! L "")
    (doseq [eid (sort (omap-keys events))]
      (let [e (get events eid)
            arts (str/join ", " (or (get e ":rite.event/uses-artifact") []))
            lin (str/join ", " (or (get e ":rite.event/lineage") []))]
        (conj! L (str "- **" (get e ":rite.event/name" eid) "** (`as-of " (get e ":rite.event/as-of") "`) — "
                      "artifacts: " (if (= "" arts) "—" arts) "; lineage: " (if (= "" lin) "—" lin)))))
    (conj! L "")
    (when (seq imbe)
      (conj! L "### Provisioning lineage")
      (conj! L "")
      (doseq [iid (sort (omap-keys imbe))]
        (let [i (get imbe iid)]
          (conj! L (str "- **" (get i ":imbe/name" iid) "** (`" (get i ":imbe/role") "`, " (get i ":imbe/region" "?") ")"
                        " — " (get i ":imbe/note" "")))))
      (conj! L ""))

    ;; ── now-open licensed cultivation space ──
    (conj! L "## Now-open low-THC licensed-cultivation space")
    (conj! L "")
    (conj! L (str "The 2023 reform (大麻草の栽培の規制に関する法律, 令和5年法律第84号) reopened a licensed "
                  "**低THC 栽培者** pathway for fibre/ritual hemp. Each design below is member-principal "
                  "(G4), server-keyless (G5), and outward-gated (G8) — R0 design only, not a filing."))
    (conj! L "")
    (conj! L (str "- member-principal / member-okaimono / serverHeldKey=false / outward-gated invariants hold "
                  "on all designs: **" (pybool (:licence-invariants-ok a)) "**"))
    (conj! L "")
    (conj! L "| design | cultivar | purpose | licensee | funding | legal basis |")
    (conj! L "|---|---|---|---|---|---|")
    (doseq [lid (sort (omap-keys licence-clean))]
      (let [lc (get licence-clean lid)]
        (conj! L (str "| `" lid "` | " (get lc ":hemp.license/cultivar") " | "
                      "`" (get lc ":hemp.license/purpose") "` | `" (get lc ":hemp.license/licensee-principal") "` | "
                      "`" (get lc ":hemp.license/funding") "` | " (get lc ":hemp.license/legal-basis" "—") " |"))))
    (conj! L "")
    (conj! L (str "> Direction 2 (legislative observation + public-comment support) is NOT here — it lives in "
                  "**danjo** (non-adjudicating fact trace) and **moushibumi** (neutral 意見公募 support). "
                  "nusa records heritage + designs cultivation; it does not advocate (G3)."))
    (conj! L "")
    (str/join "\n" (persistent! L))))

(defn render-datoms
  "Render the DERIVED heritage datoms EDN (1:1 with render_datoms)."
  [hemp rites events imbe licenses a]
  (let [L (transient [])
        thc (:thc-breakdown a)
        cultivar-rites (:cultivar-rites a)]
    (conj! L ";; nusa 幣 — DERIVED heritage datoms (ADR-2606039800)")
    (conj! L ";; :derived — analyzer output, NOT re-ingested as authoritative fact (G7).")
    (conj! L "[")
    (doseq [cls (sort (omap-keys thc))]
      (conj! L (str " {:nusa.derived/thc-class " cls " :nusa.derived/cultivar-count "
                    (get thc cls) " :nusa.derived/sourcing :derived}")))
    (doseq [hid (sort (omap-keys hemp))]
      (let [n (count (get cultivar-rites hid #{}))]
        (conj! L (str " {:nusa.derived/cultivar \"" hid "\" :nusa.derived/supplies-rites "
                      n " :nusa.derived/sourcing :derived}"))))
    (conj! L "]")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/{heritage-report.md, ritual-hemp-graph.kotoba.edn}."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-ritual-hemp.kotoba.edn"))
           out (if (some #{"--out"} argv)
                 (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                 (clojure.java.io/file (-> *file* clojure.java.io/file .getParentFile) "out"))
           rows (load-edn seed)
           [hemp rites events imbe licenses] (classify rows)
           a (analyze hemp rites events imbe licenses)]
       (.mkdirs out)
       (spit (clojure.java.io/file out "heritage-report.md")
             (render-report hemp rites events imbe licenses a))
       (spit (clojure.java.io/file out "ritual-hemp-graph.kotoba.edn")
             (render-datoms hemp rites events imbe licenses a))
       (println (str "nusa: " (count hemp) " cultivars, " (count rites) " rites, "
                     (count events) " ceremonies → " out))
       0)))
