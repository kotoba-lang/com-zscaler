(ns itonami.tests.test-alert
  "itonami 営み — R9 operational-alert tests (ADR-2606082300).
  1:1 Clojure port of tests/test_alert.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [itonami.methods.analyze :as analyze]
            [itonami.methods.alert :as alert]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private ops (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))

(defn- eval-seed []
  (let [{:keys [stations ticks]} (analyze/load-file* ops)]
    [stations (alert/evaluate stations (analyze/analyze stations ticks))]))

(defn- find-alert [alerts scope kpi]
  (first (filter #(and (= (get % "scope") scope) (= (get % "kpi") kpi)) alerts)))

(deftest test-cab-weld-scrap-is-critical
  (let [[_ alerts] (eval-seed)
        a (find-alert alerts ":st.cab-weld" "scrap_rate")]
    (is (and (some? a) (= (get a "severity") "critical")))))

(deftest test-line-oee-is-warn
  (let [[_ alerts] (eval-seed)
        a (find-alert alerts ":line.sarutahiko-a" "oee")]
    (is (and (some? a) (= (get a "severity") "warn")))))

(deftest test-healthy-station-has-no-alert
  (let [[_ alerts] (eval-seed)]
    (is (every? #(not= (get % "scope") ":st.marriage") alerts))))

(deftest test-paint-energy-and-idle-warn
  (let [[_ alerts] (eval-seed)]
    (is (= "warn" (get (find-alert alerts ":st.paint" "energy_per_good") "severity")))
    (is (= "warn" (get (find-alert alerts ":st.paint" "idle_energy_frac") "severity")))))

(deftest test-severity-grading-boundaries
  (is (= "critical" (alert/severity 0.40 (get alert/DEFAULT-THRESHOLDS "oee"))))
  (is (= "warn" (alert/severity 0.55 (get alert/DEFAULT-THRESHOLDS "oee"))))
  (is (nil? (alert/severity 0.70 (get alert/DEFAULT-THRESHOLDS "oee"))))
  (is (= "critical" (alert/severity 0.20 (get alert/DEFAULT-THRESHOLDS "scrap_rate"))))
  (is (= "warn" (alert/severity 0.08 (get alert/DEFAULT-THRESHOLDS "scrap_rate"))))
  (is (nil? (alert/severity 0.01 (get alert/DEFAULT-THRESHOLDS "scrap_rate")))))

(deftest test-thresholds-are-overridable
  (let [{:keys [stations ticks]} (analyze/load-file* ops)
        res (analyze/analyze stations ticks)
        strict {"oee" {"polarity" true "warn" 0.99 "critical" 0.90}}
        alerts (alert/evaluate stations res strict)]
    (is (>= (count alerts) 8))))

(deftest test-no-actuation-token-anywhere
  (let [[_ alerts] (eval-seed)
        blob (str/lower-case (str (alert/report-md alerts) (alert/emit alerts)))]
    (doseq [forbidden ["e-stop" "estop" "halt" "trip" "shutdown" "actuat" ":write"]]
      (is (not (str/includes? blob forbidden))
          (str "alert output leaked an actuation token: " forbidden)))))

(deftest test-emit-transient-only
  (let [[_ alerts] (eval-seed)
        out (alert/emit alerts 4)]
    (is (str/includes? out ":alert/scrap-rate :critical"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":alert/"))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))
    (is (not (str/includes? out ":add]")))))

(deftest test-determinism
  (let [[_ a] (eval-seed)
        [_ b] (eval-seed)]
    (is (= (alert/emit a) (alert/emit b)))))
