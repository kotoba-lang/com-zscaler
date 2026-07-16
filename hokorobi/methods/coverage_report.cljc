(ns hokorobi.methods.coverage-report
  "hokorobi 綻び — systemic finance-risk COVERAGE report (ADR-2606073400). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the finance-risk graph: by institution sector, by systemic-importance
  tier, by risk-kind, by bearer-kind — with a gap map naming thin/missing buckets. Coverage of
  all institutions is ~0 by design (a bounded :representative seed).

  Pure fns; reuses hokorobi.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [hokorobi.methods.analyze :as analyze]))

;; honest external denominators for the institution count
(def denominators
  [["FSB G-SIBs (~)" 29]
   ["IAIS internationally-active insurer groups (~)" 60]
   ["Globally significant banks (~)" 1000]
   ["All licensed banks worldwide (~)" 25000]])

(def sectors [":bank" ":insurer" ":reinsurer" ":pension-fund" ":ccp" ":shadow-bank"])
(def sii [":g-sib" ":d-sib" ":large" ":mid" ":small"])
(def risk-kinds [":leverage" ":maturity-mismatch" ":interconnection"
                 ":protection-gap" ":underfunding" ":concentration" ":liquidity"])
(def bearers [":depositors" ":pensioners" ":policyholders" ":taxpayers" ":real-economy"])
(def THIN 2)

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- comma
  "Python f'{n:,}' — group integer digits with commas (no fraction here)."
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
  "Python f'{x:.2e}' — scientific notation, 2 fraction digits, lowercase e, signed 2+ exp."
  [x]
  (format "%.2e" (double x)))

(defn report
  "Render the finance-risk coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        insts (filter #(= ":institution" (get % ":organism/kind")) vals*)
        risks (filter #(= ":risk" (get % ":organism/kind")) vals*)
        bears (filter #(= ":bearer" (get % ":organism/kind")) vals*)
        sec-c (counter (map #(get % ":inst/sector") insts))
        sii-c (counter (map #(get % ":inst/sii") insts))
        rk-c (counter (map #(get % ":risk/kind") risks))
        br-c (counter (map #(get % ":bearer/kind") bears))
        n-inst (count insts)
        L (transient [])]
    (conj! L "# hokorobi 綻び — systemic finance-risk coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all institutions is ~0 by design (bounded "
                  "seed). This names the systemic backbone covered and the next-wave gaps.\n"))
    (conj! L (str "**Seed**: " n-inst " institutions · " (count risks) " risk-sources · "
                  (count bears) " bearers · " (count edges) " 縁\n"))

    (conj! L "\n## Institution coverage vs denominators\n")
    (conj! L "| denominator | count | seed | fraction |")
    (conj! L "|---|---:|---:|---:|")
    (doseq [[name denom] denominators]
      (conj! L (str "| " name " | " (comma denom) " | " n-inst " | "
                    (sci2 (/ (double n-inst) denom)) " |")))

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
      (bucket "Sector coverage" sectors sec-c)
      (bucket "Systemic-importance tier coverage (DISCLOSED)" sii sii-c)
      (bucket "Risk-kind coverage" risk-kinds rk-c)
      (bucket "Bearer-kind coverage" bearers br-c))

    (let [missing (concat
                   (for [s sectors :when (= 0 (get sec-c s 0))] (lstrip-colon s))
                   (for [r risk-kinds :when (= 0 (get rk-c r 0))] (lstrip-colon r))
                   (for [b bearers :when (= 0 (get br-c b 0))] (lstrip-colon b)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (conj! L "\n---\n_hokorobi 綻び · ADR-2606073400 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-finrisk-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "hokorobi coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
