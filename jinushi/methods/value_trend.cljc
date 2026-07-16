(ns jinushi.methods.value-trend
  "jinushi 地主 — property-VALUE as-of trajectory (Wellbecoming = trajectory, not snapshot).

  DVF gives a value snapshot per year; the SIGNAL is the change over time. This computes, per
  commune, the apartment median €/m² + transaction volume for each year and the year-over-year
  delta — the value-side of the diff (差分) the append-only Datom log records. Reuses
  dvf-values/analyze*; pure over {year → lines}. No owner identity (DVF), aggregate-only (G2)."
  (:require [clojure.string :as str]
            [jinushi.methods.dvf-values :as dvf]
            #?(:clj [clojure.java.io :as io])))

(defn trend
  "year→lines (parsed DVF records) → {commune → {:by-year {year {:eur-m2 :mutations}} :yoy [{:from :to :pct :vol-pct}]}}."
  [year->lines]
  (let [years (sort (keys year->lines))
        per (into {} (map (fn [[y lines]] [y (:by-commune (dvf/analyze* lines))]) year->lines))
        communes (sort (distinct (mapcat (comp keys val) per)))]
    (into (sorted-map)
          (for [c communes]
            [c (let [by-year (into (sorted-map)
                                   (for [y years :let [m (get-in per [y c])] :when m]
                                     [y {:eur-m2 (:appt-median-eur-m2 m) :mutations (:mutations m)}]))
                     yoy (vec (keep (fn [[a b]]
                                      (let [va (get-in by-year [a :eur-m2]) vb (get-in by-year [b :eur-m2])
                                            ma (get-in by-year [a :mutations]) mb (get-in by-year [b :mutations])]
                                        (when (and va vb (pos? va))
                                          {:from a :to b
                                           :pct (* 100.0 (/ (- vb va) va))
                                           :vol-pct (when (and ma mb (pos? ma)) (* 100.0 (/ (- mb ma) ma)))})))
                                    (partition 2 1 years)))]
                 {:by-year by-year :yoy yoy})]))))

#?(:clj
   (defn load-years
     "Map of year→parsed-lines from explicitly year-tagged DVF csvs in the data dir.
     2023 = fr-dvf-75105.raw.csv; other years = fr-dvf-<commune>-<year>.timeseries.csv (kept out of
     dvf-values/load-all so the current-year snapshot is not blended)."
     [dir]
     (let [y23 (io/file dir "fr-dvf-75105.raw.csv")
           ts (->> (.listFiles dir) (filter #(re-matches #"fr-dvf-\d+-(\d{4})\.timeseries\.csv" (.getName %))))]
       (cond-> {}
         (.exists y23) (assoc "2023" (dvf/parse-csv (slurp y23)))
         true (merge (into {} (map (fn [f]
                                     [(second (re-matches #"fr-dvf-\d+-(\d{4})\.timeseries\.csv" (.getName f)))
                                      (dvf/parse-csv (slurp f))]) ts)))))))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           t (trend (load-years dir))]
       (doseq [[c v] t]
         (println (format "commune %s — apartment median €/m² trajectory:" c))
         (doseq [[y m] (:by-year v)] (println (format "    %s: €%,.0f (%d mutations)" y (or (:eur-m2 m) 0.0) (:mutations m))))
         (doseq [d (:yoy v)] (println (format "    %s→%s: %+.1f%% €/m² (vol %+.1f%%)" (:from d) (:to d) (:pct d) (or (:vol-pct d) 0.0))))))
     0))
