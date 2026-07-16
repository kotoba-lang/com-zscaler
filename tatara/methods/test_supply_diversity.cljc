#!/usr/bin/env bb
;; tatara 鑪 — tests for the per-sector supply-diversity (effective source-country count).
;; Run:  bb --classpath 20-actors 20-actors/tatara/methods/test_supply_diversity.cljc
(ns tatara.methods.test-supply-diversity
  "Tests for sector-supply-diversity — the EFFECTIVE number of independent source countries per
  sector (1/HHI, inverse-Simpson), the resilience benchmark complementing the chokepoint risk-lens.
  Aggregate-first (sector↔country-diversity, no facility/worker detail, G4); a diversification map,
  never a target-list (G2)."
  (:require [tatara.methods.analyze :as az]
            [clojure.test :refer [deftest is run-tests]]))

(defn- plant [id sector country] {:plant/id id :plant/sector sector :plant/country country})

(def ^:private result
  (az/analyze
   (az/classify
    (concat (for [i (range 4)] (plant (str "el" i) :electronics (str "C" i)))   ; 4 distinct countries
            [(plant "st0" :steel "CN") (plant "st1" :steel "CN") (plant "st2" :steel "CN")] ; single-source
            [(plant "au0" :autos "DE") (plant "au1" :autos "JP")]))))            ; 2 countries

(deftest effective-sources-is-one-over-hhi
  (let [by-sector (into {} (map (fn [[s eff _ _]] [s eff]) (az/sector-supply-diversity result)))]
    (is (< (Math/abs (- 4.0 (:electronics by-sector))) 1e-9) "4 equal source countries → 4.0 effective sources")
    (is (< (Math/abs (- 2.0 (:autos by-sector))) 1e-9) "2 equal → 2.0")
    (is (< (Math/abs (- 1.0 (:steel by-sector))) 1e-9) "single-source → 1.0")))

(deftest ranked-most-diversified-first
  (is (= [:electronics :autos :steel] (mapv first (az/sector-supply-diversity result)))
      "most-diversified (highest effective sources) first; the single-source sector last"))

(deftest row-is-sector-effective-hhi-countries-aggregate-only
  (let [row (first (az/sector-supply-diversity result))]
    (is (= 4 (count row)) "[sector effective-sources hhi countries] — nothing facility-level (G4)")
    (is (keyword? (first row)) "a bare sector keyword, no plant/worker data")))

(deftest limit-caps-the-list
  (is (<= (count (az/sector-supply-diversity result 1)) 1)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-supply-diversity)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
