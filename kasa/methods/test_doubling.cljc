#!/usr/bin/env bb
;; kasa 嵩 — tests for the doubling-period restatement of a measured CAGR.
;; Run:  bb --classpath 20-actors 20-actors/kasa/methods/test_doubling.cljc
(ns kasa.methods.test-doubling
  "Tests for doubling-period / series-doubling-periods — the doubling-time restatement of a MEASURED
  CAGR (the canonical compute-growth reading). Verifies it is a PURE transform of the measured rate
  (G4: no dated future value is projected; nil at non-positive growth) and the per-series companion
  to CAGR (fastest-doubling measured series first)."
  (:require [kasa.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-3))

(deftest doubling-period-restates-the-measured-rate
  (is (close? 1.0 (a/doubling-period 1.0)) "a 100%/yr CAGR ≡ a 1-year doubling")
  (is (close? 6.116 (a/doubling-period 0.12)) "12% ≡ ~6.12-year doubling (ln2/ln1.12)")
  (is (close? 1.505 (a/doubling-period 0.585)) "59% (AI-compute-scale) ≡ ~1.5-year doubling"))

(deftest no-doubling-at-zero-or-negative-growth
  (is (nil? (a/doubling-period 0.0)) "zero growth never doubles")
  (is (nil? (a/doubling-period -0.1)) "negative growth never doubles")
  (is (nil? (a/doubling-period nil)) "a missing CAGR yields nil"))

(deftest doubling-period-is-monotone-decreasing-in-the-rate
  ;; a higher measured rate ⇒ a shorter doubling period (a pure function of the rate, not a forecast)
  (is (< (a/doubling-period 0.6) (a/doubling-period 0.3) (a/doubling-period 0.1))))

(deftest series-doubling-periods-pairs-each-cagr-with-its-doubling-fastest-first
  (let [rows [{":compute.growth/kind" ":cagr" ":compute.growth/series" "gpu"     ":compute.growth/value" 0.585}
              {":compute.growth/kind" ":cagr" ":compute.growth/series" "storage" ":compute.growth/value" 0.12}
              {":compute.growth/kind" ":yoy"  ":compute.growth/series" "gpu"     ":compute.growth/value" 0.7}   ; not a CAGR row
              {":compute.growth/kind" ":cagr" ":compute.growth/series" "flat"    ":compute.growth/value" 0.0}]  ; never doubles
        out (a/series-doubling-periods rows)]
    (is (= ["gpu" "storage"] (mapv :series out)) "only :cagr rows; non-doubling dropped; fastest first")
    (is (close? 1.505 (:doubling-years (first out))) "gpu (59% CAGR) doubles in ~1.5 yr")
    (is (= 2 (count out)) "the :yoy row is ignored and the 0% CAGR series drops out (never doubles)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kasa.methods.test-doubling)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
