(ns sanae.methods.labor-liberation
  "sanae 早苗 — empirical Liberation Priority Score (LPS) + freed-labour-hours.
  1:1 Clojure port of `methods/labor_liberation.py` (ADR-2606032100).

  Pure Clojure (clojure.core + Math/log10 only). Two computations:

    1. `lps`            — the ranking score used to prioritize which toil to automate.
    2. `freed-labor-hours` / `displacement-cohort-size` — given a deployment that
       automates a fraction of an occupation's tasks, how many human labour-hours/year
       are freed, and how large the displacement cohort is (feeds ADR-2606032130
       pool sizing).

  SectorGap is represented as an immutable map with keys:
    :name :isic :isco :unspsc :headcount :misery :automatability :charter-fit :coverage-gap.

  Everything is order-of-magnitude and `:representative` (G8). The point is the SHAPE
  of the priority, not a sourced dataset; R2 replaces the seed with measured ISCO-occupation
  gap data."
  (:require [clojure.string :as str]))

(defn lps
  "Liberation Priority Score = log10(headcount) × misery × automatability × charter-fit × coverage-gap.

  headcount is logged so a 0.8B sector does not swamp everything else by 10^4."
  [g]
  (* (Math/log10 (max (:headcount g) 1.0))
     (:misery g)
     (:automatability g)
     (:charter-fit g)
     (:coverage-gap g)))

(defn freed-labor-hours
  "Human labour-hours/year removed by automating `task-automation-fraction` of the work."
  [headcount hours-per-worker-yr task-automation-fraction]
  (* headcount hours-per-worker-yr task-automation-fraction))

(defn displacement-cohort-size
  "Approx number of workers whose role is displaced (and thus owed the dividend)."
  [headcount task-automation-fraction]
  (long (Math/round (* headcount task-automation-fraction))))

;; `:representative` seed for the ADR-2606032100 ranking (order-of-magnitude).
(def seed-gaps
  [{:name "sanae (field agriculture)" :isic "A01" :isco "6111/9211" :unspsc "70" :headcount 7.0e8  :misery 2.6 :automatability 0.55 :charter-fit 1.0 :coverage-gap 0.85}
   {:name "hataori (garment/apparel)" :isic "C13-14" :isco "7531/8219" :unspsc "53" :headcount 6.5e7 :misery 2.9 :automatability 0.45 :charter-fit 1.0 :coverage-gap 1.0}
   {:name "kiyome (domestic/cleaning)" :isic "T/N81" :isco "9111/9112" :unspsc "76" :headcount 1.1e8 :misery 2.4 :automatability 0.55 :charter-fit 1.0 :coverage-gap 1.0}
   {:name "kamado (food service)" :isic "I56" :isco "5120/9412" :unspsc "90" :headcount 1.0e8 :misery 2.1 :automatability 0.45 :charter-fit 1.0 :coverage-gap 1.0}
   {:name "kuramori (warehouse)" :isic "H52" :isco "9333/8219" :unspsc "24" :headcount 4.0e7 :misery 2.2 :automatability 0.70 :charter-fit 1.0 :coverage-gap 0.9}
   {:name "tatekata (construction)" :isic "F" :isco "7119/9313" :unspsc "72" :headcount 2.6e8 :misery 2.6 :automatability 0.45 :charter-fit 1.0 :coverage-gap 0.5}
   {:name "hofuri (meat processing)" :isic "C10" :isco "7511/9211" :unspsc "23" :headcount 3.0e7 :misery 2.9 :automatability 0.50 :charter-fit 1.0 :coverage-gap 0.9}
   {:name "soma (forestry)" :isic "A02" :isco "6210/9215" :unspsc "70" :headcount 5.0e6 :misery 3.0 :automatability 0.40 :charter-fit 1.0 :coverage-gap 1.0}
   {:name "ama (fishing/aquaculture)" :isic "A03" :isco "6222/9216" :unspsc "70" :headcount 3.8e7 :misery 2.9 :automatability 0.35 :charter-fit 1.0 :coverage-gap 0.9}
   ;; excluded by N1 — charter-fit 0 → LPS 0, proving the gate zeroes it out
   {:name "MINING (excluded N1)" :isic "B" :isco "8111/9311" :unspsc "20" :headcount 2.5e7 :misery 3.0 :automatability 0.6 :charter-fit 0.0 :coverage-gap 1.0}])

(defn ranked-seed
  "Returns a sequence of `[name lps]` sorted by LPS descending."
  []
  (sort-by second (comp - compare)
           (map (fn [g] [(:name g) (lps g)]) seed-gaps)))

(defn -main
  "CLI entry: print the seed ranking and an illustrative freed-hours example."
  [& _argv]
  (println "Liberation Priority Score ranking (:representative seed):")
  (doseq [[i [name score]] (map-indexed vector (ranked-seed))]
    (println (format "  %2d. %-34s LPS=%5.2f" (inc i) name score)))
  (let [fh (freed-labor-hours 7.0e8 2000 0.3)
        cohort (displacement-cohort-size 7.0e8 0.3)]
    (println (str "\nsanae illustrative: automating 30% of field-labour tasks frees "
                  (format "%.1f" (/ fh 1.0e9)) "B labour-hours/yr; cohort ≈ "
                  (format "%.0f" (/ cohort 1.0e6)) "M workers."))))
