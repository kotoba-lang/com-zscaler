(ns tsumugi.methods.coverage-scale
  "tsumugi 紡ぎ — scale+banner COVERAGE report.
  Clojure port of methods/coverage_scale.py (1:1). ADR-2606092000.

  HONEST FRAMING: real-world coverage is ~0 BY DESIGN (bounded :representative sample);
  this measures the *exercised structure* and *names gaps*, it is NOT real-world coverage.
  Aggregate-only, no per-person data. Mirror, non-adjudicating.

  stdlib only (reuses analyze-scale's EDN loader + closed vocabs); file I/O at the
  #?(:clj) edge. `compute` is pure over already-loaded records."
  (:require [clojure.string :as str]
            [tsumugi.methods.analyze-scale :as a]
            #?(:clj [clojure.java.io :as io])))

;; a DENOMINATORS table as clearly-labelled APPROXIMATE constants (array-map keeps order).
(def DENOMINATORS
  (array-map
   "Countries (~sovereign states)" 195
   "JP basic municipalities" 1741
   "World municipalities (order of magnitude)" 100000
   "JP corporations (approx)" 3800000
   "World listed companies (approx)" 50000
   "Universities (approx)" 25000))

#?(:clj
   (defn load-data
     "Read the scale + banner seeds, splitting into [nodes ties banners ents flies]
     (no validation here — that is analyze-scale/load-graph's job)."
     ([] (load-data "20-actors/tsumugi/data/seed-scale-power.kotoba.edn"
                    "20-actors/tsumugi/data/seed-banner.kotoba.edn"))
     ([scale-seed banner-seed]
      (let [scale-records  (a/read-edn (slurp (io/file (str scale-seed))))
            banner-records (a/read-edn (slurp (io/file (str banner-seed))))
            [nodes ties] (reduce (fn [[ns ts] r]
                                   (cond
                                     (not (map? r)) [ns ts]
                                     (contains? r ":pwr/id") [(assoc ns (get r ":pwr/id") r) ts]
                                     (contains? r ":tie/id") [ns (conj ts r)]
                                     :else [ns ts]))
                                 [{} []] scale-records)
            [banners ents flies] (reduce (fn [[bs es fs] r]
                                           (cond
                                             (not (map? r)) [bs es fs]
                                             (contains? r ":banner/id") [(assoc bs (get r ":banner/id") r) es fs]
                                             (contains? r ":ent/id") [bs (assoc es (get r ":ent/id") r) fs]
                                             (contains? r ":flies/id") [bs es (conj fs r)]
                                             :else [bs es fs]))
                                         [{} {} []] banner-records)]
        [nodes ties banners ents flies]))))

(defn compute
  "Aggregate-only coverage report over the loaded records. Pure + deterministic."
  [nodes ties banners ents flies]
  (let [agg (reduce
             (fn [acc n]
               (let [sec (get n ":pwr/sector")
                     loc (get n ":pwr/locality" "")
                     acc (if-not (str/blank? loc)
                           (-> acc
                               (update :localities conj loc)
                               (update :countries conj (first (str/split loc #"\.")))
                               (cond-> (and (str/starts-with? loc "jp.")
                                            (>= (count (str/split loc #"\.")) 3))
                                 (update :num-jp-muni inc)))
                           acc)
                     acc (if sec (update-in acc [:sector-counts sec] (fnil inc 0)) acc)
                     acc (if-let [sc (get n ":pwr/scale")] (update-in acc [:scale-counts sc] (fnil inc 0)) acc)
                     acc (if-let [ck (get n ":pwr/collective-kind")] (update-in acc [:kind-counts ck] (fnil inc 0)) acc)]
                 (cond
                   (= sec ":san") (-> acc (update :num-world-listed inc)
                                      (cond-> (str/starts-with? loc "jp") (update :num-jp-corp inc)))
                   (= sec ":gaku") (update acc :num-univ inc)
                   :else acc)))
             {:localities #{} :countries #{} :scale-counts {} :kind-counts {} :sector-counts {}
              :num-jp-muni 0 :num-jp-corp 0 :num-world-listed 0 :num-univ 0}
             (vals nodes))
        localities (:localities agg)
        countries  (:countries agg)
        jp-muni-distinct (count (filter (fn [loc] (and (str/starts-with? loc "jp.")
                                                       (>= (count (str/split loc #"\.")) 3)))
                                        localities))
        coverage-stats {"Countries (~sovereign states)" (count countries)
                        "JP basic municipalities" jp-muni-distinct
                        "World municipalities (order of magnitude)" (count localities)
                        "JP corporations (approx)" (:num-jp-corp agg)
                        "World listed companies (approx)" (:num-world-listed agg)
                        "Universities (approx)" (:num-univ agg)}
        coverages (reduce (fn [m [k denom]]
                            (let [num (get coverage-stats k 0)]
                              (assoc m k {"numerator" num "denominator" denom
                                          "percent" (if (and denom (not (zero? denom)))
                                                      (* (/ (double num) denom) 100) 0)})))
                          (array-map) DENOMINATORS)
        exercised? (fn [counts s] (> (get counts s 0) 0))]
    {"raw" {"nodes" (count nodes) "ties" (count ties)
            "distinct_localities" (count localities)
            "banners" (count banners) "ents" (count ents) "flies" (count flies)}
     "per_scale" {"exercised" (vec (filter #(exercised? (:scale-counts agg) %) a/SCALES))
                  "missing" (vec (remove #(exercised? (:scale-counts agg) %) a/SCALES))}
     "per_kind" {"exercised" (vec (filter #(exercised? (:kind-counts agg) %) a/COLLECTIVE-KINDS))
                 "missing" (vec (remove #(exercised? (:kind-counts agg) %) a/COLLECTIVE-KINDS))}
     "per_sector" {"exercised" (vec (filter #(exercised? (:sector-counts agg) %) a/SECTORS))
                   "missing" (vec (remove #(exercised? (:sector-counts agg) %) a/SECTORS))}
     "per_country" {"countries" (vec (sort countries))}
     "coverages" coverages}))

(defn- fmt4 [x]
  #?(:clj (format "%.4f" (double x)) :cljs (.toFixed (double x) 4)))

(defn render
  "Markdown coverage report (mirror of render)."
  [c]
  (let [raw (get c "raw")
        join* (fn [xs] (str/join ", " xs))
        none-or (fn [xs] (if (seq xs) (join* xs) "None"))
        lines (concat
               ["# tsumugi 紡ぎ — Scale & Banner Coverage Report" ""
                "> HONEST FRAMING: real-world coverage is ~0 BY DESIGN (bounded `:representative`"
                "> sample); this measures the *exercised structure* and *names gaps*, it is NOT"
                "> real-world coverage. Aggregate-only, no per-person data. Mirror, non-adjudicating." ""
                "## Raw Counts"
                (str "- Power nodes: " (get raw "nodes"))
                (str "- Power ties: " (get raw "ties"))
                (str "- Distinct localities: " (get raw "distinct_localities"))
                (str "- Banners: " (get raw "banners"))
                (str "- Entities: " (get raw "ents"))
                (str "- Flies (alignments): " (get raw "flies")) ""
                "## Structural Dimensions (Gap Map)"
                (str "- **Scales Exercised**: " (join* (get-in c ["per_scale" "exercised"])))
                (str "- **Scales MISSING**: " (let [m (get-in c ["per_scale" "missing"])]
                                                (if (seq m) (join* m) "None (All exercised)")))
                (str "- **Collective Kinds Exercised**: " (join* (get-in c ["per_kind" "exercised"])))
                (str "- **Collective Kinds MISSING**: " (none-or (get-in c ["per_kind" "missing"])))
                (str "- **Sectors Exercised**: " (join* (get-in c ["per_sector" "exercised"])))
                (str "- **Sectors MISSING**: " (none-or (get-in c ["per_sector" "missing"]))) ""
                "## Geographic Coverage"
                (str "- **Countries Exercised**: " (join* (get-in c ["per_country" "countries"]))) ""
                "## Denominators & Contextual Coverage"]
               (map (fn [[k v]]
                      (str "- " k ": " (get v "numerator") " / ~" (get v "denominator")
                           " (" (fmt4 (get v "percent")) "%)"))
                    (get c "coverages")))]
    (str (str/join "\n" lines) "\n")))

#?(:clj
   (defn -main
     "CLI entry: mirrors coverage_scale.main — [--out OUTDIR]. ADR-2606261200."
     [& argv]
     (let [argv (vec argv)
           here (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
           out  (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           [nodes ties banners ents flies] (load-data)
           c    (compute nodes ties banners ents flies)]
       (.mkdirs out)
       (spit (io/file out "coverage-report.md") (render c))
       (println (str "[tsumugi/coverage-scale] " (count nodes) " nodes · " (count banners)
                     " banners → " (.getPath (io/file out "coverage-report.md")))))))
