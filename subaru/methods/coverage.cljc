(ns subaru.methods.coverage
  "subaru 昴 — coverage as §1.16 Social Security reach (G3; cash≡0 in-kind). 1:1 Clojure port
  of methods/coverage.py (ADR-2606162355).

  Reach = Σ incident :serves coverage-pct over service-areas that are :area/unconnected or
  :area/disaster — NOT a market map."
  (:require [clojure.string :as str]
            [subaru.methods.link-budget :as core]))

(defn coverage [nodes edges]
  (core/check-g1 nodes)
  (let [areas (into {} (filter (fn [[_ n]] (= ":service-area" (get n ":organism/kind"))) nodes))
        served (reduce (fn [m e]
                         (if (and (= ":serves" (get e ":en/kind")) (contains? areas (get e ":en/to")))
                           (assoc m (get e ":en/to")
                                  (let [v (get e ":en/coverage-pct")] (if (or (nil? v) (false? v)) 0.0 (double v))))
                           m))
                       {} edges)
        rows0 (mapv (fn [[aid a]]
                      (let [cov (get served aid 0.0)
                            priority (or (boolean (get a ":area/unconnected"))
                                         (boolean (get a ":area/disaster")))]
                        {:area aid :label (get a ":organism/label") :region (get a ":area/region")
                         :coverage_pct cov :ss_priority priority
                         :unconnected (boolean (get a ":area/unconnected"))
                         :disaster (boolean (get a ":area/disaster"))}))
                    areas)
        reach (reduce + 0.0 (map :coverage_pct (filter :ss_priority rows0)))
        rows (vec (sort-by (fn [r] [(- (if (:ss_priority r) 1 0)) (- (:coverage_pct r))]) rows0))
        n-priority (count (filter :ss_priority rows))]
    {:areas rows :ss_reach reach :n_priority n-priority}))

(defn- pct0 [v] (format "%.0f" (* 100.0 (double v))))
(defn- fmt2 [v] (format "%.2f" (double v)))

(defn report-md [_nodes _edges res]
  (let [L (transient [])]
    (conj! L "# subaru 昴 — coverage as §1.16 Social Security reach\n")
    (conj! L (str "> **G3 — non-profit / no-ads / cash≡0.** Connectivity is §1.16 in-kind social "
                  "security (covenantal-universal) — coverage is REACH to the unconnected + disaster "
                  "zones, NOT a market/ARPU map. Service keyed to aggregate region (G1).\n"))
    (conj! L "\n| service area (region) | coverage | §1.16 priority |")
    (conj! L "|---|---:|:--:|")
    (doseq [r (:areas res)]
      (let [tag (cond (:unconnected r) "unconnected" (:disaster r) "disaster" :else "baseline")]
        (conj! L (str "| " (:region r) " (" tag ") | " (pct0 (:coverage_pct r)) "% | "
                      (if (:ss_priority r) "✅" "—") " |"))))
    (conj! L (str "\n**§1.16 reach (Σ coverage over " (:n_priority res) " priority areas): "
                  (fmt2 (:ss_reach res)) "**\n"))
    (conj! L "\n---\n_subaru 昴 · ADR-2606162355 · §1.16 in-kind · cash≡0 · no-ads._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (clojure.java.io/file (or (System/getenv "SUBARU_ACTOR_DIR") "20-actors/subaru"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-constellation.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (core/load-file* seed)
           res (coverage nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report-md nodes edges res))
       (println (str "subaru coverage: " (:n_priority res) " §1.16-priority areas, reach "
                     (fmt2 (:ss_reach res)) " → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
