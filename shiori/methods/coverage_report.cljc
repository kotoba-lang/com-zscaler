(ns shiori.methods.coverage-report
  "shiori 栞 — wellbecoming-detraction COVERAGE report (ADR-2606082100). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the detraction graph: by cohort kind, by detractor kind, by detractor
  severity, by driver kind, by mitigator kind — with a gap map naming thin/missing buckets.
  Coverage of all cohorts/detractors is ~0 by design (a bounded :representative, AGGREGATE seed).

  Pure fns; reuses shiori.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [shiori.methods.analyze :as analyze]))

;; honest external denominators (public wellbeing-research scale context)
(def denominators
  [["Adults reporting frequent loneliness (~ share)" "1 in 4"]
   ["Workers reporting chronic work stress (~ share)" "~44%"]
   ["People in housing cost-overburden (OECD, ~)" "tens of millions"]
   ["Tracked Wellbecoming detractor kinds (this seed)" 12]])

(def cohort-kinds [":workers" ":gig-workers" ":youth" ":students" ":elderly" ":isolated-aged"
                   ":unpaid-carers" ":low-income" ":chronically-ill" ":new-parents"])
(def detractor-kinds [":precarity" ":overwork" ":isolation" ":addictive-design" ":debt-burden"
                      ":housing-insecurity" ":chronic-pain" ":information-pollution"
                      ":discrimination" ":care-deprivation" ":meaning-deficit" ":sleep-deprivation"])
(def severity [":critical" ":severe" ":moderate" ":mild"])
(def driver-kinds [":always-on-work-culture" ":engagement-maximizing-design" ":high-cost-credit"
                   ":thin-safety-net" ":unaffordable-housing-market" ":care-infrastructure-gap"
                   ":algorithmic-feed" ":job-insecurity-norm"])
(def mitigator-kinds [":social-connection" ":secure-housing" ":meaningful-work" ":rest-recovery"
                      ":mutual-aid" ":treatment-access" ":financial-stability"
                      ":information-hygiene" ":care-support" ":time-sovereignty"])
(def THIN 2)

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn report
  "Render the wellbecoming-detraction coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        cohorts (filter #(= ":cohort" (get % ":organism/kind")) vals*)
        detrs (filter #(= ":detractor" (get % ":organism/kind")) vals*)
        drvs (filter #(= ":driver" (get % ":organism/kind")) vals*)
        mits (filter #(= ":mitigator" (get % ":organism/kind")) vals*)
        coh-c (counter (map #(get % ":cohort/kind") cohorts))
        detr-c (counter (map #(get % ":detractor/kind") detrs))
        sev-c (counter (map #(get % ":detractor/severity") detrs))
        drv-c (counter (map #(get % ":driver/kind") drvs))
        mit-c (counter (map #(get % ":mitigator/kind") mits))
        L (transient [])]
    (conj! L "# shiori 栞 — wellbecoming-detraction coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all cohorts/detractors is ~0 by design (bounded "
                  "AGGREGATE seed; G1 = no individuals). This names the covered backbone and the "
                  "next-wave gaps.\n"))
    (conj! L (str "**Seed**: " (count cohorts) " cohorts · " (count detrs) " detractors · "
                  (count drvs) " drivers · " (count mits) " mitigators · " (count edges) " 縁\n"))

    (conj! L "\n## Scale context (modelled as cohorts, not individuals — by design, G1)\n")
    (conj! L "| denominator | value |")
    (conj! L "|---|---:|")
    (doseq [[name val] denominators]
      (conj! L (str "| " name " | " val " |")))

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
      (bucket "Cohort-kind coverage" cohort-kinds coh-c)
      (bucket "Detractor-kind coverage" detractor-kinds detr-c)
      (bucket "Detractor-severity coverage (DISCLOSED)" severity sev-c)
      (bucket "Driver-kind coverage (structural patterns)" driver-kinds drv-c)
      (bucket "Mitigator-kind coverage (the 守り)" mitigator-kinds mit-c))

    (let [missing (concat
                   (for [k cohort-kinds :when (= 0 (get coh-c k 0))] (lstrip-colon k))
                   (for [k detractor-kinds :when (= 0 (get detr-c k 0))] (lstrip-colon k))
                   (for [k driver-kinds :when (= 0 (get drv-c k 0))] (lstrip-colon k))
                   (for [k mitigator-kinds :when (= 0 (get mit-c k 0))] (lstrip-colon k)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (conj! L "\n---\n_shiori 栞 · ADR-2606082100 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-wellbecoming-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "shiori coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
