(ns maps.methods.coverage-report
  "maps — substrate coverage-gap report (ADR-2606064500). stdlib only.
  New file (ADR-2606064500 R2).

  Honest coverage: by label, by sourcing, by transit presence, with a gap map
  naming thin/missing buckets. Coverage of the real world is ~0 by design (bounded
  :representative seed); this makes the covered backbone measurable and names
  the next wave. Pure fns; reuses analyze. Portable .cljc."
  (:require [clojure.string :as str]
            [maps.methods.analyze :as analyze]))

(def label-expected
  [":admin-area" ":place" ":station" ":building" ":road" ":railway"
   ":river" ":lake" ":coastline" ":mountain" ":port" ":airport"
   ":bus-stop" ":bus-route" ":sea-route" ":air-route"
   ":legal-entity" ":registry" ":satellite-scene"])

(def thin-threshold 2)

(defn- counter [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn report
  "Render the coverage-gap report markdown."
  [features rels aliases]
  (let [lab-c  (counter (map #(get % ":feature/label") (vals features)))
        src-c  (counter (map #(get % ":feature/sourcing") (vals features)))
        has-transit? (some #(str/starts-with? (str %) "transit.") (keys features))
        L (transient [])]
    (conj! L "# maps — substrate coverage-gap report (ADR-2606064500)")
    (conj! L "")
    (conj! L (str "> Honest denominator: coverage of the real world is ~0 by design (bounded "
                  ":representative seed, Tokyo Station anchor). This names the backbone covered "
                  "and the next-wave ingest gaps. G3: absence = not-yet-ingested, never 'not present'."))
    (conj! L "")
    (conj! L (str "**Seed**: " (count features) " features · "
                  (count rels) " topology edges · " (count aliases) " geo-aliases"))
    (conj! L "")

    (conj! L "## Label coverage (what the substrate holds vs expected)")
    (conj! L "")
    (conj! L "| label | count | status |")
    (conj! L "|---|---:|---|")
    (doseq [lab label-expected]
      (let [n (get lab-c lab 0)]
        (conj! L (str "| `" lab "` | " n " | "
                      (cond
                        (zero? n)           "❌ **gap** — not yet ingested"
                        (< n thin-threshold) "⚠️ thin"
                        :else               "✅")
                      " |"))))

    (conj! L "")
    (conj! L "## Sourcing (G3 honesty)")
    (conj! L "")
    (conj! L "| sourcing | count |")
    (conj! L "|---|---:|")
    (doseq [[s n] (sort-by (comp -) (vals src-c) (keys src-c))]
      (when n (conj! L (str "| `" s "` | " n " |"))))

    (conj! L "")
    (conj! L "## Transit coverage")
    (conj! L "")
    (conj! L (if has-transit?
               "✅ Transit entities (:transit.trip/\\* + :transit.stop-time/\\*) present."
               "❌ **gap** — GTFS transit data not yet ingested."))

    (conj! L "")
    (conj! L "## Gap summary (next ingest wave)")
    (conj! L "")
    (let [gaps (filter #(zero? (get lab-c % 0)) label-expected)]
      (if (seq gaps)
        (do (conj! L "Labels with **zero** features (highest-priority backfill):")
            (conj! L "")
            (doseq [g gaps] (conj! L (str "- `" g "`"))))
        (conj! L "All expected labels represented in the seed.")))

    (conj! L "")
    (conj! L (str "---\n_maps · ADR-2606064500 · RisingWave → kotoba Datom log migration · "
                  "G3 honest coverage · G9 feature = placed thing, never person._"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main [& _argv]
     (let [here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (clojure.java.io/file here "data" "seed-spatial-graph.kotoba.edn")
           outdir (clojure.java.io/file here "out")
           [features rels aliases] (analyze/classify (analyze/load-edn seed))]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-gap-report.md")
             (report features rels aliases))
       (println "maps coverage-gap report written.")
       0)))
