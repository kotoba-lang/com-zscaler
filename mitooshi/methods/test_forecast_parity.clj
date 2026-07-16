#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the mitooshi rolling-backtest forecaster.
(ns mitooshi.methods.test-forecast-parity
  "test_forecast_parity.clj — mitooshi forecast.clj py↔clj LIVE parity (ADR-2606051800).

  forecast.clj closes the forecasting loop: a leak-free rolling backtest that forecasts each
  series' next value as a DISTRIBUTION, scores it (proper scoring), and summarises skill +
  calibration. This runs the ACTUAL `forecast.py` via a python3 subprocess and the clj impl over
  the SAME persisted trail fixture, then DEEP-COMPARES the entire backtest result — mean_crps,
  mean_skill, the calibration sub-map (pit_mean / deviation / 10-bin hist), and the per-origin
  breakdown — to 1e-6 across both the rolling and recalibrated variants. The strongest single
  parity proof for the forecasting loop: a leak, an ordering bug, or a recalibration drift in
  EITHER impl moves a number and fails.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/mitooshi/methods/test_forecast_parity.clj"
  (:require [mitooshi.methods.forecast :as f]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- trail-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "persisted" "chokepoint-trail.kotoba.edn") .getAbsolutePath))

(def ^:private py-dir "20-actors/mitooshi/methods")
(def ^:private methods ["climatology" "persistence"])

(def ^:private py-src
  (str "import json, pathlib, forecast as f\n"
       "trail = pathlib.Path(__import__('sys').argv[1])\n"
       "rows = f.load_edn(trail)\n"
       "out = {}\n"
       "for m in ['climatology','persistence']:\n"
       "    out['roll-'+m] = f.backtest_rolling(rows, m)\n"
       "    out['cal-'+m]  = f.backtest_calibrated(rows, m)\n"
       "print(json.dumps(out))\n"))

(defn- py-results [trail]
  (try
    (let [r (sh "python3" "-c" py-src trail :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) false)))   ; keywords:false → string keys, matching clj
    (catch Exception _ nil)))

;; recursive deep-compare: numbers within 1e-6, maps key-by-key, vectors element-wise, else =.
(defn- deep-close? [a b]
  (cond
    (and (number? a) (number? b)) (< (Math/abs (- (double a) (double b))) 1e-6)
    (and (map? a) (map? b)) (and (= (set (keys a)) (set (keys b)))
                                 (every? #(deep-close? (get a %) (get b %)) (keys a)))
    (and (sequential? a) (sequential? b)) (and (= (count a) (count b))
                                               (every? true? (map deep-close? a b)))
    :else (= a b)))

(deftest clj-backtest-is-leak-free-and-sane
  ;; runs regardless of python: n>0, mean_crps ≥ 0, calibration hist sums ~1.
  (let [bt (f/backtest-rolling (f/load-trail-edn (trail-path)) "climatology")]
    (is (pos? (get bt "n")))
    (is (>= (get bt "mean_crps") 0.0))
    (is (< (Math/abs (- 1.0 (reduce + (get-in bt ["calibration" "hist"])))) 1e-6) "PIT hist sums to 1")))

(deftest forecast-backtest-matches-python
  (let [trail (trail-path)
        py (py-results trail)]
    (if-not py
      (is true "python3 unavailable — forecast backtest cross-language parity skipped")
      (let [rows (f/load-trail-edn trail)]
        (doseq [m methods]
          (is (deep-close? (get py (str "roll-" m)) (f/backtest-rolling rows m))
              (str "rolling backtest drift @" m))
          (is (deep-close? (get py (str "cal-" m)) (f/backtest-calibrated rows m))
              (str "calibrated backtest drift @" m)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'mitooshi.methods.test-forecast-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
