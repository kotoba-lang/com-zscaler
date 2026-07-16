#!/usr/bin/env bb
;; tatara 鑪 — tests for the per-country cross-sector chokepoint lens.
;; Run:  bb --classpath 20-actors 20-actors/tatara/methods/test_cross_sector.cljc
(ns tatara.methods.test-cross-sector
  "Tests for cross-sector-chokepoints — the per-country cross-sector view that surfaces the country
  whose dominance SPANS the most sectors (the systemic reshoring priority a per-sector HHI ranking
  cannot show). G2 resilience map (redundancy/reshoring, not interdiction); G4 aggregate-only
  (country↔sector counts, never a facility/worker datum)."
  (:require [tatara.methods.analyze :as az]
            [clojure.test :refer [deftest is run-tests]]))

(defn- plant [id sector country] {:plant/id id :plant/sector sector :plant/country country})

(def ^:private graph
  (az/classify
   (concat (for [i (range 3)] (plant (str "se" i) :semis "TW"))    ; TW single-source semis
           (for [i (range 3)] (plant (str "au" i) :autos "TW"))    ; TW single-source autos
           [(plant "st0" :steel "TW") (plant "st1" :steel "TW")    ; TW tops steel @0.4 (not single-source)
            (plant "st2" :steel "JP") (plant "st3" :steel "KR") (plant "st4" :steel "US")]
           (for [i (range 3)] (plant (str "ch" i) :chem "DE")))))  ; DE single-source chem

(def ^:private result (az/analyze graph))

(deftest the-country-spanning-the-most-sectors-leads
  (let [[country dominates single-source sectors] (first (az/cross-sector-chokepoints result))]
    (is (= "TW" country) "TW tops 3 sectors (semis/autos/steel) → leads the cross-sector list")
    (is (= 3 dominates) "TW is the dominant source for 3 sectors")
    (is (= 2 single-source) "TW is the SINGLE source for 2 of them (semis, autos)")
    (is (= [:autos :semis :steel] sectors) "the sectors it dominates, name-sorted")))

(deftest a-single-sector-leader-ranks-below-the-cross-sector-one
  (let [by-country (into {} (map (fn [[c d s _]] [c [d s]]) (az/cross-sector-chokepoints result)))]
    (is (= [1 1] (by-country "DE")) "DE dominates only chem (1 sector, single-source)")
    (is (= "TW" (ffirst (az/cross-sector-chokepoints result)))
        "TW (3 sectors) outranks DE (1) even though both have a single-source sector")))

(deftest aggregate-only-no-facility-or-worker-detail-leaks-g4
  ;; the output is purely [country counts sectors] — no plant id, no per-worker datum (G4)
  (let [row (first (az/cross-sector-chokepoints result))]
    (is (= 4 (count row)) "[country dominates single-source sectors] — nothing facility-level")
    (is (every? keyword? (nth row 3)) "the dominated sectors are bare sector keywords, no plant/worker data")))

(deftest limit-caps-the-list
  (is (<= (count (az/cross-sector-chokepoints result 1)) 1) "the optional limit truncates the ranking"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-cross-sector)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
