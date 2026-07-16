(ns jinushi.methods.dvf-values
  "jinushi 地主 — FR DVF property-VALUE source (6th source; new dimension: transactions/price).

  DVF (Demandes de Valeurs Foncières, DGFiP/Etalab open data, geo-dvf) is the public record of
  French real-estate TRANSACTIONS: sale value, parcel, surface, type — but NO owner identity
  (owner names are restricted in FR; the jurisdiction gate already classifies FR owner-names
  :restricted). So DVF adds a VALUE dimension (€, €/m²) without any owner PII — gate-clean.

  Confidence tier :dvf = authoritative-gov (DGFiP). G1: no owner, no person; street address DROPPED
  (commune + official id_parcelle only — value-intel needs neither). Aggregate-first (G2): medians
  per property type, not a per-parcel valuation product."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; column indices in geo-dvf CSV (verified): id_mutation 1, date 2, nature 4, valeur_fonciere 5,
;; code_commune 11, id_parcelle 16, type_local 31, surface_reelle_bati 32
(defn- col [v i] (nth v (dec i) nil))
(defn- num [s] (when (and s (not (str/blank? s))) (try (Double/parseDouble s) (catch #?(:clj Exception :cljs :default) _ nil))))

(defn parse-csv
  "geo-dvf CSV text → transaction LINE records (no address, no owner — G1)."
  [csv-text]
  (let [lines (str/split-lines csv-text)]
    (->> (rest lines)                     ;; drop header
         (remove str/blank?)
         (map #(str/split % #"," -1))
         (keep (fn [v]
                 (when (col v 16)
                   {:mutation (col v 1) :date (col v 2) :nature (col v 4)
                    :price-eur (num (col v 5)) :parcel/id (col v 16)
                    :type (col v 31) :surface-bati-m2 (num (col v 32))
                    :commune (col v 11) :cc "FR" :source :dvf})))
         vec)))

(defn analyze*
  "Aggregate value-intel: mutation count + total value (deduped by id_mutation) + per-type median
  price and median €/m² (single-lot apartment/house lines where surface>0)."
  [lines]
  (let [median (fn [xs] (let [s (vec (sort xs)) n (count s)]
                          (when (pos? n) (if (odd? n) (nth s (quot n 2))
                                             (/ (+ (nth s (dec (quot n 2))) (nth s (quot n 2))) 2.0)))))
        muts (group-by :mutation lines)
        mutation-values (keep (fn [[_ ls]] (:price-eur (first ls))) muts)
        single-lot (filter (fn [[_ ls]] (= 1 (count ls))) muts)
        per-type (->> lines
                      (filter #(and (:type %) (not (str/blank? (:type %)))
                                    (:price-eur %) (:surface-bati-m2 %) (pos? (:surface-bati-m2 %))))
                      (group-by :type)
                      (map (fn [[t ls]]
                             [t {:lines (count ls)
                                 :median-price (median (map :price-eur ls))
                                 :median-eur-m2 (median (map #(/ (:price-eur %) (:surface-bati-m2 %)) ls))}]))
                      (into (sorted-map)))
        appt-eur-m2 (fn [ls] (median (->> ls (filter #(and (= "Appartement" (:type %)) (:price-eur %)
                                                          (:surface-bati-m2 %) (pos? (:surface-bati-m2 %))))
                                          (map #(/ (:price-eur %) (:surface-bati-m2 %))))))
        by-commune (->> lines (group-by :commune)
                        (map (fn [[c ls]] [c {:mutations (count (group-by :mutation ls))
                                              :appt-median-eur-m2 (appt-eur-m2 ls)}]))
                        (into (sorted-map)))]
    {:lines (count lines)
     :mutations (count muts)
     :single-lot-mutations (count single-lot)
     :total-value-eur (reduce + 0.0 mutation-values)
     :median-mutation-eur (median mutation-values)
     :by-type per-type
     :by-commune by-commune}))

#?(:clj
   (defn load-csv [dir]
     (let [f (io/file dir "fr-dvf-75105.raw.csv")] (when (.exists f) (slurp f)))))

#?(:clj
   (defn load-all [dir]
     "Parse every committed fr-dvf-*.raw.csv (multi-commune) → merged transaction lines."
     (->> (.listFiles (io/file dir))
          (filter #(re-matches #"fr-dvf-.*\.raw\.csv" (.getName %)))
          (sort-by #(.getName %))
          (mapcat #(parse-csv (slurp %)))
          vec)))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           recs (load-all dir)]
       (if (empty? recs)
         (println "no fr-dvf-*.raw.csv — operator fetch first (geo-dvf, open licence)")
         (let [a (analyze* recs)]
           (println (format "DVF: %d lines / %d mutations / total €%,.0f across %d communes"
                            (:lines a) (:mutations a) (:total-value-eur a) (count (:by-commune a))))
           (doseq [[c v] (:by-commune a)]
             (println (format "  commune %s: %d mutations, apartment median €%,.0f/m²"
                              c (:mutations v) (or (:appt-median-eur-m2 v) 0.0)))))))
     0))
