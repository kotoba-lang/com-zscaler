(ns itonami.methods.test-trend
  "itonami 営み — R7 KPI trend / drift tests (ADR-2606082300).
  1:1 Clojure port of tests/test_trend.py (every assertion). load-history takes EDN text
  (I/O at the #?(:clj) edge), so the G2 worker-series rejection is exercised on text directly
  rather than via a temp file."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [itonami.methods.trend :as trend]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def hist (io/file actor-dir "data" "seed-ops-history.kotoba.edn"))

(defn- load-history [] (trend/load-history (slurp hist)))
(defn- trends [] (trend/analyze-trends (load-history)))

(deftest test-history-loads
  (let [recs (load-history)]
    (is (= 10 (count recs)))   ; 5 days × 2 scopes
    (is (= #{":line.sarutahiko-a" ":st.cab-weld"}
           (set (map #(get % ":opsday/scope") recs))))))

(deftest test-oee-degradation-detected
  (let [t (trends)
        oee (get-in t [":line.sarutahiko-a" ":opsday/oee"])]
    (is (= ":degrading" (get oee "direction")))   ; 0.62 → 0.50
    (is (true? (get oee "regression")))
    (is (< (get oee "slope") 0))))               ; least-squares slope is negative

(deftest test-scrap-rise-is-degrading-lower-better-polarity
  ;; scrap-rate is lower-better → a RISING series is degrading (polarity handled).
  (let [t (trends)
        scrap (get-in t [":line.sarutahiko-a" ":opsday/scrap-rate"])]
    (is (> (get scrap "last") (get scrap "first")))   ; rising
    (is (= ":degrading" (get scrap "direction")))     ; rising is bad for a lower-better KPI
    (is (true? (get scrap "regression")))))

(deftest test-flat-series-not-flagged
  ;; energy-per-good is essentially flat → not a regression.
  (let [t (trends)
        epg (get-in t [":line.sarutahiko-a" ":opsday/energy-per-good"])]
    (is (= ":flat" (get epg "direction")))
    (is (false? (get epg "regression")))))

(deftest test-station-scrap-is-top-regression
  (let [t (trends)
        regs (trend/regressions t)]
    (is (seq regs) "expected degrading series")
    ;; cab-weld scrap (0.10 → 0.22, +120%) is the largest relative regression
    (let [top (first regs)]
      (is (and (= ":st.cab-weld" (nth top 0)) (= ":opsday/scrap-rate" (nth top 1)))))))

(deftest test-g2-rejects-worker-series
  ;; G2: load-history must refuse an ops history that carries a person/worker series.
  (let [bad "[{:opsday/day 0 :opsday/scope :line.a :worker/id \"w1\" :opsday/oee 0.5}]"]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2" (trend/load-history bad)))))

(deftest test-emit-transient-only
  (let [t (trends)
        out (trend/emit t 3)]
    (is (str/includes? out ":trend/oee-direction"))
    (is (str/includes? out ":trend/scrap-rate-regression true"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":trend/"))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))
    (is (not (str/includes? out ":add]")))))

(deftest test-determinism
  (let [a (trend/emit (trends) 1)
        b (trend/emit (trends) 1)]
    (is (= a b))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'itonami.methods.test-trend)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
