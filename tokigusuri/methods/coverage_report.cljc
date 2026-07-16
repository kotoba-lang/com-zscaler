(ns tokigusuri.methods.coverage-report
  "tokigusuri 時薬 — pharmaceutical patent-cliff COVERAGE report (ADR-2606171300). Structural
  sibling of hokorobi.methods.coverage-report.

  Honest coverage of the pharma-patent graph: by drug modality, by exclusivity-status, by
  essentiality tier, by barrier-kind, by bearer-kind — with a gap map naming thin/missing
  buckets. Coverage of all marketed drugs is ~0 by design (a bounded :representative seed).

  Pure fns; reuses tokigusuri.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [tokigusuri.methods.analyze :as analyze]))

;; honest external denominators for the drug count
(def denominators
  [["WHO Essential Medicines List items (~)" 600]
   ["FDA-approved active ingredients (~)" 1500]
   ["FDA Orange Book products (~)" 20000]
   ["All marketed drug products worldwide (~)" 100000]])

(def modalities [":small-molecule" ":biologic" ":vaccine"])
(def statuses [":on-patent" ":expiring" ":off-patent"])
(def essentiality [":eml-core" ":eml-complementary" ":on-market" ":niche"])
(def barrier-kinds [":primary-patent" ":secondary-patent" ":data-exclusivity" ":spc"
                    ":orphan-exclusivity" ":pay-for-delay" ":patent-thicket"])
(def bearers [":patients" ":lmic-populations" ":health-systems" ":payers" ":uninsured"])
(def THIN 2)

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- comma
  "Python f'{n:,}' — group integer digits with commas."
  [n]
  (let [s (str (long n))
        neg (str/starts-with? s "-")
        digits (if neg (subs s 1) s)
        rev (reverse (vec digits))
        grouped (->> rev
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     reverse
                     (str/join ","))]
    (str (when neg "-") grouped)))

(defn- sci2
  "Python f'{x:.2e}' — scientific notation, 2 fraction digits."
  [x]
  (format "%.2e" (double x)))

(defn report
  "Render the pharma-patent coverage-report markdown."
  [nodes edges]
  (let [vals* (vals nodes)
        drugs (filter #(= ":drug" (get % ":organism/kind")) vals*)
        barriers (filter #(= ":barrier" (get % ":organism/kind")) vals*)
        holders (filter #(= ":holder" (get % ":organism/kind")) vals*)
        bears (filter #(= ":bearer" (get % ":organism/kind")) vals*)
        mod-c (counter (map #(get % ":drug/modality") drugs))
        st-c (counter (map #(get % ":drug/exclusivity-status") drugs))
        ess-c (counter (map #(get % ":drug/essentiality") drugs))
        bk-c (counter (map #(get % ":barrier/kind") barriers))
        br-c (counter (map #(get % ":bearer/kind") bears))
        n-drug (count drugs)
        L (transient [])]
    (conj! L "# tokigusuri 時薬 — pharmaceutical patent-cliff coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all marketed drugs is ~0 by design (bounded "
                  "seed). This names the patent-cliff backbone covered and the next-wave gaps.\n"))
    (conj! L (str "**Seed**: " n-drug " drugs · " (count barriers) " exclusivity-barriers · "
                  (count holders) " holders · " (count bears) " bearers · " (count edges) " 縁\n"))

    (conj! L "\n## Drug coverage vs denominators\n")
    (conj! L "| denominator | count | seed | fraction |")
    (conj! L "|---|---:|---:|---:|")
    (doseq [[name denom] denominators]
      (conj! L (str "| " name " | " (comma denom) " | " n-drug " | "
                    (sci2 (/ (double n-drug) denom)) " |")))

    (letfn [(bucket [title ks cnt]
              (conj! L (str "\n## " title "\n"))
              (conj! L "| bucket | count | status |")
              (conj! L "|---|---:|:--|")
              (doseq [k ks]
                (let [c (get cnt k 0)
                      status (cond (= c 0) "— **MISSING**"
                                   (< c THIN) "⚠ thin"
                                   :else "ok")]
                  (conj! L (str "| " (lstrip-colon k) " | " c " | " status " |")))))]
      (bucket "Modality coverage" modalities mod-c)
      (bucket "Exclusivity-status coverage (DISCLOSED)" statuses st-c)
      (bucket "Essentiality tier coverage (WHO EML)" essentiality ess-c)
      (bucket "Barrier-kind coverage" barrier-kinds bk-c)
      (bucket "Bearer-kind coverage" bearers br-c))

    (let [missing (concat
                   (for [m modalities :when (= 0 (get mod-c m 0))] (lstrip-colon m))
                   (for [s statuses :when (= 0 (get st-c s 0))] (lstrip-colon s))
                   (for [b barrier-kinds :when (= 0 (get bk-c b 0))] (lstrip-colon b))
                   (for [b bearers :when (= 0 (get br-c b 0))] (lstrip-colon b)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (conj! L "\n---\n_tokigusuri 時薬 · ADR-2606171300 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-pharma-patent-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "tokigusuri coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
