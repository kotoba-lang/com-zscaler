#!/usr/bin/env bb
;; hakoniwa 箱庭 — tests for distribution-entropy (effective number of possible futures).
;; Run:  bb -cp "20-actors:20-actors/kotodama/src" 20-actors/hakoniwa/tests/test_distribution_entropy.cljc
(ns hakoniwa.tests.test-distribution-entropy
  "Tests for distribution-entropy — the Shannon entropy + effective number of outcomes (perplexity)
  of the forecast histogram: 1.0 for consensus, ≈2.0 for a bimodal/two-futures forecast, the full
  bin count for maximal uncertainty. Distribution-shape only (no point, G2; non-steering, G3)."
  (:require [hakoniwa.methods.distribution :as d]
            [clojure.test :refer [deftest is run-tests]]))

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-6))

(deftest consensus-is-one-effective-outcome
  (let [r (d/distribution-entropy [0 0 0 0 6 0 0 0 0 0])]   ; all mass in one bin
    (is (= 0.0 (:entropy r)) "no entropy when the forecast is certain")
    (is (= 1.0 (:effective-outcomes r)) "one effective future")
    (is (= 0.0 (:normalized r)))))

(deftest a-bimodal-forecast-is-about-two-effective-outcomes
  (let [r (d/distribution-entropy [3 0 0 0 0 0 0 0 0 3])]   ; two equal modes
    (is (close? (Math/log 2) (:entropy r)) "H = ln 2")
    (is (close? 2.0 (:effective-outcomes r)) "two likely futures — prepare for both")))

(deftest a-flat-forecast-is-maximal-uncertainty
  (let [r (d/distribution-entropy [2 2 2 2 2 2 2 2 2 2])]   ; uniform over 10 bins
    (is (close? 10.0 (:effective-outcomes r)) "all ten bins equally likely")
    (is (close? 1.0 (:normalized r)) "fraction of maximal uncertainty = 1")))

(deftest effective-outcomes-is-perplexity-exp-entropy
  (let [r (d/distribution-entropy [5 3 1 0 0 0 0 0 0 1])]
    (is (close? (Math/exp (:entropy r)) (:effective-outcomes r)) "effective-outcomes = exp(entropy)")))

(deftest empty-or-single-bin-is-degenerate
  (is (= {:entropy 0.0 :effective-outcomes 0.0 :normalized 0.0} (d/distribution-entropy [0 0 0]))
      "an empty histogram has no outcomes")
  (is (= 1.0 (:effective-outcomes (d/distribution-entropy [7]))) "a single bin is one certain outcome"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'hakoniwa.tests.test-distribution-entropy)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
