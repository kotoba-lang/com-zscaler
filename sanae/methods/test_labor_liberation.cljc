(ns sanae.methods.test-labor-liberation
  "sanae 早苗 — LPS ranking method tests (ADR-2606032100).
  1:1 Clojure port of methods/test_labor_liberation.py.

  Verifies the empirical Liberation Priority Score invariants empirically:
    - the N1-excluded sector (mining, charter-fit 0.0) scores exactly zero
    - a covered peer (tatekata, coverage-gap 0.5) ranks below an uncovered peer
      (hataori, coverage-gap 1.0)
    - sanae ranks first due to largest headcount × misery, with near-zero coverage
    - the wave opens with sanae, hataori, and kiyome all in the top band
    - freed labour-hours scale linearly with the three inputs
    - displacement cohort size is rounded correctly
    - higher misery raises the score all else equal"
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [sanae.methods.labor-liberation :as ll]))

(deftest test-n1-excluded-sector-scores-zero
  (let [mining (first (filter #(str/includes? (:name %) "MINING") ll/seed-gaps))]
    (is (= 0.0 (ll/lps mining))))) ; charter_fit=0 zeroes the score (N1 gate proof)

(deftest test-covered-sector-scores-below-uncovered-peer
  ;; construction (coverage_gap 0.5, tatekata exists) vs garment (coverage_gap 1.0)
  (let [constr (first (filter #(str/includes? (:name %) "tatekata") ll/seed-gaps))
        garment (first (filter #(str/includes? (:name %) "hataori") ll/seed-gaps))]
    (is (> (ll/lps garment) (ll/lps constr)))))

(deftest test-sanae-ranks-first
  (is (str/includes? (first (first (ll/ranked-seed))) "sanae"))) ; largest headcount×misery, near-zero coverage

(deftest test-wave-actors-in-top-band
  ;; The wave OPENS sanae/hataori/kiyome (highest headcount×misery, zero coverage).
  ;; Note: raw automatability-weighting lifts kuramori (warehouse) into the top band too —
  ;; it is the #4/#5 roadmap item precisely because it is the most automatable of the
  ;; un-covered toil; the wave still prioritizes the zero-coverage sweatshop/cleaning first.
  (let [top5 (set (map first (take 5 (ll/ranked-seed))))]
    (is (some #(str/includes? % "sanae") top5))
    (is (some #(str/includes? % "hataori") top5))
    (is (some #(str/includes? % "kiyome") top5))))

(deftest test-freed-hours-scales-linearly
  (is (= 1000000.0 (ll/freed-labor-hours 1000 2000 0.5))))

(deftest test-cohort-size-rounds
  (is (= 250000 (ll/displacement-cohort-size 1.0e6 0.25))))

(deftest test-higher-misery-raises-score-all-else-equal
  (let [base {:name "x" :isic "" :isco "" :unspsc "" :headcount 1e7 :misery 2.0 :automatability 0.5 :charter-fit 1.0 :coverage-gap 1.0}
        worse {:name "y" :isic "" :isco "" :unspsc "" :headcount 1e7 :misery 3.0 :automatability 0.5 :charter-fit 1.0 :coverage-gap 1.0}]
    (is (> (ll/lps worse) (ll/lps base)))))
