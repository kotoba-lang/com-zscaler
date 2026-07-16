(ns asobi.methods.coverage-report
  "asobi 遊び — freed-time COVERAGE report (ADR-2606073200). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the play/expression graph: by access category, by work medium, by
  practice domain, by venue kind, by enclosure kind — with a gap map naming thin/missing
  buckets. Coverage of all culture is ~0 by design (a bounded :representative seed); this
  makes the real covered backbone measurable and names the next wave.

  Pure fns; reuses asobi.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [asobi.methods.analyze :as analyze]))

(def media [":music" ":film" ":text" ":game" ":stage" ":visual" ":sport-form"])
(def domains [":sport" ":music" ":dance" ":stage" ":craft" ":game" ":letters"])
(def venues [":public-library" ":public-park" ":museum" ":hall" ":field"
             ":makerspace" ":online-commons" ":enclosed-venue"])
(def enclosures [":paywall" ":attention-platform" ":ticketing-lock"
                 ":copyright-lock" ":geo-block"])
(def access [":public-domain" ":open-license" ":free-gratis" ":ticketed"
             ":paywalled" ":proprietary"])
(def THIN 2)

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn report
  "Render the freed-time coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        works (filter #(= ":work" (get % ":organism/kind")) vals*)
        pracs (filter #(= ":practice" (get % ":organism/kind")) vals*)
        venues* (filter #(= ":venue" (get % ":organism/kind")) vals*)
        encs (filter #(= ":enclosure" (get % ":organism/kind")) vals*)
        med-c (counter (map #(get % ":work/medium") works))
        dom-c (counter (map #(get % ":practice/domain") pracs))
        ven-c (counter (map #(get % ":venue/kind") venues*))
        enc-c (counter (map #(get % ":enclosure/kind") encs))
        acc-c (counter (map #(get % ":work/access") works))
        L (transient [])]
    (conj! L "# asobi 遊び — freed-time coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all culture is ~0 by design (bounded seed). "
                  "This names the participation backbone covered and the next-wave gaps.\n"))
    (conj! L (str "**Seed**: " (count works) " works · " (count pracs) " practices · "
                  (count venues*) " venues · " (count encs) " enclosures · " (count edges) " 縁\n"))

    (conj! L "\n## Access spread (DISCLOSED facts, not verdicts)\n")
    (conj! L "| access | count |")
    (conj! L "|:--:|---:|")
    (doseq [a access]
      (conj! L (str "| " (lstrip-colon a) " | " (get acc-c a 0) " |")))

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
      (bucket "Work-medium coverage" media med-c)
      (bucket "Practice-domain coverage" domains dom-c)
      (bucket "Venue-kind coverage" venues ven-c)
      (bucket "Enclosure-kind coverage" enclosures enc-c))

    (let [missing (concat
                   (for [b media :when (= 0 (get med-c b 0))] (lstrip-colon b))
                   (for [d domains :when (= 0 (get dom-c d 0))] (lstrip-colon d))
                   (for [v venues :when (= 0 (get ven-c v 0))] (lstrip-colon v))
                   (for [e enclosures :when (= 0 (get enc-c e 0))] (lstrip-colon e)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (conj! L "\n---\n_asobi 遊び · ADR-2606073200 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-asobi-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "asobi coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
