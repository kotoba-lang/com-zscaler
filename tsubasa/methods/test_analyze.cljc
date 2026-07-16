#!/usr/bin/env bb
;; tsubasa 翼 — analyze / datoms / coverage tests (+ G1/G3/G5 structural invariants).
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_analyze.cljc
(ns tsubasa.methods.test-analyze
  (:require [tsubasa.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private rows
  [{:type :airport :airport/iata "JFK" :airport/region :north-america}
   {:type :airport :airport/iata "NRT" :airport/region :east-asia}
   {:type :airport :airport/iata "LHR" :airport/region :europe}
   {:type :carrier :carrier/iata "AA"}
   {:type :carrier :carrier/iata "JL"}
   {:type :carrier :carrier/iata "BA"}
   ;; JFK-NRT: 2 carriers, AA cheaper-but-dirtier, JL pricier-but-greener
   {:type :fare :fare/id "f1" :fare/origin "JFK" :fare/destination "NRT" :fare/carrier "AA"
    :fare/duration-min 825 :fare/fare-minor 68000 :fare/baggage-minor 3500 :fare/co2-kg 1080.0 :fare/sourcing :representative}
   {:type :fare :fare/id "f2" :fare/origin "JFK" :fare/destination "NRT" :fare/carrier "JL"
    :fare/duration-min 815 :fare/fare-minor 72000 :fare/baggage-minor 0 :fare/co2-kg 980.0 :fare/sourcing :representative}
   ;; JFK-LHR: single carrier (monopoly)
   {:type :fare :fare/id "f3" :fare/origin "JFK" :fare/destination "LHR" :fare/carrier "BA"
    :fare/duration-min 420 :fare/fare-minor 45000 :fare/baggage-minor 4000 :fare/co2-kg 640.0 :fare/sourcing :representative}])

(deftest total-cost-is-fare-plus-baggage   ; G4 honesty
  (is (= 71500 (a/total-minor {:fare/fare-minor 68000 :fare/baggage-minor 3500})))
  (is (= 72000 (a/total-minor {:fare/fare-minor 72000 :fare/baggage-minor 0})))
  ;; a higher headline fare with no bag fee can beat a lower fare + bag
  (is (< (a/total-minor {:fare/fare-minor 72000 :fare/baggage-minor 0})
         (a/total-minor {:fare/fare-minor 70000 :fare/baggage-minor 3500}))))

(deftest hhi-reflects-carrier-presence
  (is (= 1.0 (a/carrier-hhi [{:fare/carrier "BA"}])))                 ; monopoly
  (is (= 0.5 (a/carrier-hhi [{:fare/carrier "AA"} {:fare/carrier "JL"}]))) ; duopoly even
  (is (< (a/carrier-hhi [{:fare/carrier "AA"} {:fare/carrier "JL"} {:fare/carrier "BA"}])
         0.5)))

(deftest concentration-reading
  (is (= :monopoly (a/concentration 1 1.0)))
  (is (= :concentrated (a/concentration 2 0.5)))
  (is (= :competitive (a/concentration 3 0.333))))

(deftest analyze-route-surfaces-cheapest-greenest-fastest   ; G4
  (let [an (a/analyze rows)
        jfk-nrt (first (filter #(= (get % "route") "JFK-NRT") (get an "routes")))]
    (is (= 2 (get jfk-nrt "carrier_count")))
    ;; cheapest TRUE total = JL (72000+0) < AA (68000+3500=71500)? AA total 71500 < JL 72000 → AA
    (is (= "AA" (get jfk-nrt "cheapest_carrier")))
    (is (= 71500 (get jfk-nrt "cheapest_total_minor")))
    ;; greenest = JL (980 < 1080) — emissions is first-class, not ranked away
    (is (= "JL" (get jfk-nrt "greenest_carrier")))
    (is (= 980.0 (get jfk-nrt "greenest_co2_kg")))
    ;; fastest = JL (815 < 825)
    (is (= "JL" (get jfk-nrt "fastest_carrier")))))

(deftest monopoly-route-flagged-opening
  (let [an (a/analyze rows)
        jfk-lhr (first (filter #(= (get % "route") "JFK-LHR") (get an "routes")))]
    (is (= :monopoly (get jfk-lhr "concentration")))
    (is (= :opening (get jfk-lhr "opening")))))

(deftest carriers-rolled-up
  (let [an (a/analyze rows)
        aa (first (filter #(= (get % "carrier") "AA") (get an "carriers")))]
    (is (= 1 (get aa "route_count")))
    (is (= 1 (get aa "fare_count")))
    (is (= 1080.0 (get aa "mean_co2_kg")))))

(deftest datoms-flag-derived-and-sourcing
  (let [ds (a/datoms (a/analyze rows))]
    (is (pos? (count ds)))
    (is (every? #(= ":db/add" (first %)) ds))
    ;; every route/carrier entity carries a :tsubasa/derived true datom
    (is (seq (filter #(and (= ":tsubasa/derived" (nth % 2)) (true? (nth % 3))) ds)))
    (is (seq (filter #(= ":tsubasa/sourcing" (nth % 2)) ds)))
    ;; co2 surfaced as a derived obs (G4)
    (is (seq (filter #(= ":tsubasa.obs/greenest-co2-kg" (nth % 2)) ds)))))

(deftest g1-g3-g5-no-forbidden-attribute-ever-emitted
  ;; The defining structural invariants: the analysis has no commission / affiliate /
  ;; urgency / scarcity / searcher / person input and emits no such datom — by construction.
  (let [ds (a/datoms (a/analyze rows))
        attrs (map #(str/lower-case (str (nth % 2))) ds)
        forbidden ["commission" "affiliate" "merchant" "sponsored"   ; G1 (no inflow)
                   "urgency" "scarcity" "price-will-rise" "seats-left" ; G3 (anti-dark)
                   "searcher" "person" "profile" "pattern-of-life"]]  ; G5 (no tracking)
    (doseq [bad forbidden]
      (is (not-any? #(str/includes? % bad) attrs)
          (str "G1/G3/G5 violated — a datom attribute contains '" bad "'")))))

(deftest coverage-counts-and-gap
  (let [cov (a/coverage rows)]
    (is (= 3 (get cov "airports_have")))
    (is (= 3 (get cov "carriers_have")))
    (is (= 2 (get cov "routes_have")))         ; JFK-NRT + JFK-LHR
    (is (pos? (get cov "airports_gap")))       ; thin seed → honest gap
    (is (vector? (get cov "by_region")))))

(deftest report-declares-not-a-target-list-and-no-commission
  (let [rep (a/render-report (a/analyze rows) (a/coverage rows))]
    (is (str/includes? rep "NEVER a target-list"))
    (is (str/includes? rep "no commission"))
    (is (str/includes? rep "stateless"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
