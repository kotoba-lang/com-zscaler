(ns aburi.methods.coverage-report
  "aburi 炙り — tracker-exposure COVERAGE report (ADR-2606161630). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the exposure graph: by surface kind, by permission kind, by collector kind, by
  collector catalogue (provenance), by data-type kind — with a gap map naming thin/missing buckets.
  Coverage of all surfaces/collectors is ~0 by design (a bounded :representative public seed); this
  makes the covered backbone measurable and names the next wave (more surfaces, more SDKs).

  Pure fns; reuses aburi.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [aburi.methods.analyze :as analyze]))

;; honest external denominators (public scale context — NOT measurements of a person)
(def denominators
  [["Trackers catalogued by Exodus Privacy (public, ~)" "hundreds"]
   ["Apps Exodus has analysed (public, ~)" "100,000+"]
   ["Median trackers in a free Android app (research, ~)" "several"]
   ["Collectors modelled (this seed)" 14]])

(def surface-kinds [":search" ":social" ":app-store" ":mobile-app" ":website" ":os"])
(def permission-kinds [":ad-id" ":precise-location" ":location" ":contacts" ":camera"
                       ":microphone" ":photos" ":browsing-history" ":purchase-history"
                       ":app-usage" ":health" ":tos-data-sharing" ":ad-personalization"])
(def collector-kinds [":ad-network" ":dsp" ":ssp" ":exchange" ":data-broker"
                      ":analytics" ":tracker-sdk"])
(def catalogs [":exodus" ":apple-privacy" ":play-data-safety" ":sellers-json" ":iab"])
(def datatype-kinds [":precise-location" ":coarse-location" ":device-id" ":contacts"
                     ":browsing" ":purchases" ":app-usage" ":identifiers" ":photos"
                     ":health-fitness"])
(def THIN 2)

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn report
  "Render the tracker-exposure coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        surfs (filter #(= ":surface" (get % ":organism/kind")) vals*)
        perms (filter #(= ":permission" (get % ":organism/kind")) vals*)
        cols (filter #(= ":collector" (get % ":organism/kind")) vals*)
        dts (filter #(= ":datatype" (get % ":organism/kind")) vals*)
        surf-c (counter (map #(get % ":surface/kind") surfs))
        perm-c (counter (map #(get % ":permission/kind") perms))
        col-c (counter (map #(get % ":collector/kind") cols))
        cat-c (counter (map #(get % ":collector/catalog") cols))
        dt-c (counter (map #(get % ":datatype/kind") dts))
        L (transient [])]
    (conj! L "# aburi 炙り — tracker-exposure coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all real surfaces/collectors is ~0 by design "
                  "(bounded REPRESENTATIVE public seed; G1 = own-data, no real person). This names the "
                  "covered backbone and the next-wave gaps (more surfaces, more catalogued SDKs).\n"))
    (conj! L (str "**Seed**: " (count surfs) " surfaces · " (count perms) " permissions · "
                  (count cols) " collectors · " (count dts) " data-types · " (count edges) " 縁\n"))

    (conj! L "\n## Scale context (public catalogues — NOT measurements of a person, G1/N3)\n")
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
      (bucket "Surface-kind coverage" surface-kinds surf-c)
      (bucket "Permission-kind coverage" permission-kinds perm-c)
      (bucket "Collector-kind coverage" collector-kinds col-c)
      (bucket "Collector-catalogue coverage (provenance, G5)" catalogs cat-c)
      (bucket "Data-type-kind coverage" datatype-kinds dt-c))

    (let [missing (concat
                   (for [k surface-kinds :when (= 0 (get surf-c k 0))] (lstrip-colon k))
                   (for [k permission-kinds :when (= 0 (get perm-c k 0))] (lstrip-colon k))
                   (for [k collector-kinds :when (= 0 (get col-c k 0))] (lstrip-colon k))
                   (for [k datatype-kinds :when (= 0 (get dt-c k 0))] (lstrip-colon k)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (conj! L "\n---\n_aburi 炙り · ADR-2606161630 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-tracker-exposure.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "aburi coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
