#!/usr/bin/env bb
;; funamori 舫 — tests for the salinity-gradient design feasibility-margin lens.
;; Run:  bb --classpath 20-actors 20-actors/funamori/methods/test_design_margins.cljc
(ns funamori.methods.test-design-margins
  "Tests for design-margins — feasibility headroom vs the three §1 gates (Δsalinity ≥30 g/L,
  power-density ≥1 W/m², rated ≤50 kW) + the binding constraint. A descriptive design-analysis that
  only READS the gate constants (never relaxes them); a negative margin marks a violated gate."
  (:require [funamori.methods.salinity-gradient :as sg]
            [clojure.test :refer [deftest is run-tests]]))

(deftest reports-headroom-vs-each-gate
  (let [m (sg/design-margins {:salinity-diff-g-l 35.0 :power-density-w-m2 1.2 :rated-kw 45.0})]
    (is (= 5.0 (get-in m [:salinity :margin])) "35 − 30 = 5 g/L of salinity headroom")
    (is (< (Math/abs (- 0.2 (get-in m [:power-density :margin]))) 1e-9) "1.2 − 1.0 = 0.2 W/m²")
    (is (= 5.0 (get-in m [:power :margin])) "50 − 45 = 5 kW below the cap")
    (is (:feasible m) "all three gates satisfied")))

(deftest binding-constraint-is-the-tightest-relative-margin
  ;; rated 45 kW (rel 0.10) is closer to its cap than salinity (rel 0.167) or power-density (rel 0.20)
  (is (= :power (:binding-constraint
                 (sg/design-margins {:salinity-diff-g-l 35.0 :power-density-w-m2 1.2 :rated-kw 45.0}))))
  ;; drop power-density to just above its floor → it becomes the binding gate
  (is (= :power-density (:binding-constraint
                         (sg/design-margins {:salinity-diff-g-l 34.5 :power-density-w-m2 1.05 :rated-kw 10.0})))))

(deftest an-infeasible-design-shows-a-negative-margin-on-the-violated-gate
  (let [m (sg/design-margins {:salinity-diff-g-l 25.0 :power-density-w-m2 1.2 :rated-kw 10.0})]
    (is (not (:feasible m)) "Δsalinity 25 < 30 → infeasible")
    (is (neg? (get-in m [:salinity :margin])) "the violated salinity gate carries a negative margin")
    (is (= :salinity (:binding-constraint m)) "and it is the binding constraint")))

(deftest relative-margin-is-margin-over-the-limit
  ;; the normalization that lets the three different-unit gates be compared on one scale
  (let [m (sg/design-margins {:salinity-diff-g-l 60.0 :power-density-w-m2 2.0 :rated-kw 25.0})]
    (is (< (Math/abs (- 1.0 (get-in m [:salinity :relative]))) 1e-9) "(60−30)/30 = 1.0")
    (is (< (Math/abs (- 1.0 (get-in m [:power-density :relative]))) 1e-9) "(2−1)/1 = 1.0")
    (is (< (Math/abs (- 0.5 (get-in m [:power :relative]))) 1e-9) "(50−25)/50 = 0.5")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'funamori.methods.test-design-margins)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
