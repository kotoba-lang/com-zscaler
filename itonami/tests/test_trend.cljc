(ns itonami.tests.test-trend
  "itonami 営み — R7 KPI trend / drift tests (ADR-2606082300).
  1:1 Clojure port of tests/test_trend.py (pytest → clojure.test).
  NOTE: the Python test_g2_rejects_worker_series writes a temp file then reads via load_history;
  ported here by feeding load-history the same bad EDN text directly (text-arg port)."
  (:require [clojure.test :refer [deftest is run-tests]]
            #?(:clj [clojure.java.io :as io])
            [itonami.methods.trend :as trend]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private hist (io/file actor-dir "data" "seed-ops-history.kotoba.edn"))

(defn- trends [] (trend/analyze-trends (trend/load-history (slurp hist))))

(deftest test-history-loads
  (let [recs (trend/load-history (slurp hist))]
    (is (= 10 (count recs)))
    (is (= #{":line.sarutahiko-a" ":st.cab-weld"} (set (map #(get % ":opsday/scope") recs))))))

(deftest test-oee-degradation-detected
  (let [t (trends)
        oee (get-in t [":line.sarutahiko-a" ":opsday/oee"])]
    (is (= ":degrading" (get oee "direction")))
    (is (true? (get oee "regression")))
    (is (< (get oee "slope") 0))))

(deftest test-scrap-rise-is-degrading-lower-better-polarity
  (let [t (trends)
        scrap (get-in t [":line.sarutahiko-a" ":opsday/scrap-rate"])]
    (is (> (get scrap "last") (get scrap "first")))
    (is (= ":degrading" (get scrap "direction")))
    (is (true? (get scrap "regression")))))

(deftest test-flat-series-not-flagged
  (let [t (trends)
        epg (get-in t [":line.sarutahiko-a" ":opsday/energy-per-good"])]
    (is (= ":flat" (get epg "direction")))
    (is (false? (get epg "regression")))))

(deftest test-station-scrap-is-top-regression
  (let [t (trends)
        regs (trend/regressions t)]
    (is (seq regs))
    (let [top (first regs)]
      (is (and (= ":st.cab-weld" (nth top 0)) (= ":opsday/scrap-rate" (nth top 1)))))))

(deftest test-g2-rejects-worker-series
  (let [bad "[{:opsday/day 0 :opsday/scope :line.a :worker/id \"w1\" :opsday/oee 0.5}]"]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (trend/load-history bad)))))

(deftest test-emit-transient-only
  (let [t (trends)
        out (trend/emit t 3)]
    (is (clojure.string/includes? out ":trend/oee-direction"))
    (is (clojure.string/includes? out ":trend/scrap-rate-regression true"))
    (doseq [line (clojure.string/split-lines out)]
      (when (and (clojure.string/starts-with? line "[") (clojure.string/includes? line ":trend/"))
        (is (and (clojure.string/includes? line ":derived]")
                 (clojure.string/includes? line ":bond/is-transient true")) line)))
    (is (not (clojure.string/includes? out ":add]")))))

(deftest test-determinism
  (is (= (trend/emit (trends) 1) (trend/emit (trends) 1))))
