#!/usr/bin/env bb
;; kamado 竈 — tests for the analytical purge-time (entry-wait modelling estimate).
;; Run:  bb --classpath 20-actors 20-actors/kamado/methods/test_purge_time.cljc
(ns kamado.methods.test-purge-time
  "Tests for purge-time — the linear-dilution time a hazardous-gas zone needs to reach the safe-entry
  target under a constant purge flow. Pins the analytical value against the gas-step! sim, and the
  UNVENTABLE (nil) refuse-signal when the leak overwhelms the purge. A modelling estimate, never a
  certified clearance (G11)."
  (:require [kamado.methods.decommission-robot :as d]
            [clojure.test :refer [deftest is run-tests]]))

(deftest is-the-linear-dilution-time
  (is (< (Math/abs (- (/ (- 100.0 10.0) (- (* 0.5 10.0) 0.1))
                      (d/purge-time {:k-purge 0.5 :leak 0.1} 10.0 100.0 10.0))) 1e-9)
      "t = (c0 − target)/(k·flow − leak) = 90/4.9 ≈ 18.37 s"))

(deftest matches-the-gas-step-sim
  ;; step gas-step! at a constant flow until the zone reaches target; the elapsed time matches
  (let [k 0.5 leak 0.1 flow 10.0 c0 100.0 target 10.0 dt 0.01
        zone (d/->gas-concentration-plant {:k-purge k :leak leak :c c0})
        analytical (d/purge-time {:k-purge k :leak leak} flow c0 target)]
    (loop [t 0.0]
      (if (<= (d/gas-measure zone) target)
        (is (< (Math/abs (- t analytical)) 0.02) "the simulated purge time matches the analytical value")
        (do (d/gas-step! zone flow dt) (recur (+ t dt)))))))

(deftest an-unventable-zone-returns-nil-the-refuse-signal
  (is (nil? (d/purge-time {:k-purge 0.5 :leak 6.0} 10.0 100.0 10.0))
      "leak 6 > k·flow 5 — the purge never wins; the entry gate must refuse (unventable)")
  (is (nil? (d/purge-time {:k-purge 0.5 :leak 5.0} 10.0 100.0 10.0))
      "leak exactly equals the purge rate — still never reaches target"))

(deftest already-safe-is-zero-and-more-flow-is-faster
  (is (= 0.0 (d/purge-time {:k-purge 0.5 :leak 0.1} 10.0 5.0 10.0)) "already below target → 0 wait")
  (is (< (d/purge-time {:k-purge 0.5 :leak 0.1} 20.0 100.0 10.0)
         (d/purge-time {:k-purge 0.5 :leak 0.1} 10.0 100.0 10.0))
      "doubling the purge flow shortens the wait"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kamado.methods.test-purge-time)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
