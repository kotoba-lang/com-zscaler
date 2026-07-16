(ns mizuho.methods.test-water-supply
  "Tests for mizuho water-supply operational loop (methods/water_supply.cljc).
  1:1 port of methods/test_water_supply.py.

    bb --classpath 20-actors -e \"(require 'mizuho.methods.test-water-supply 'clojure.test) \\
       (clojure.test/run-tests 'mizuho.methods.test-water-supply)\""
  (:require [clojure.test :refer [deftest is]]
            [mizuho.methods.substrate :as sub]
            [mizuho.methods.water-supply :as ws]))

(defn- approx?
  "pytest.approx(expected, abs=tol)."
  [actual expected tol]
  (<= (Math/abs (- (double actual) (double expected))) tol))

(deftest test-supply-restores-level-after-demand-step
  (let [res (ws/commission-water-supply :demand-step-lps 20.0)]
    (is (:level-restored res))
    (is (approx? (:final-level-m res) 3.0 1e-2)) ;; back to service setpoint
    (is (> (:settling-seconds res) 0))
    (is (> (:final-pressure-bar res) 0))))       ;; service pressure restored

(deftest test-supply-restores-for-large-demand-step
  ;; A bigger demand (more taps open) is also rejected back to the setpoint.
  (let [res (ws/commission-water-supply :demand-step-lps 80.0 :service-population 1500)]
    (is (:level-restored res))
    (is (approx? (:final-level-m res) 3.0 1e-2))))

(deftest test-non-civilian-use-refused
  (doseq [use ["weapon" "fire-control" "interdiction" "flood"]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (ws/commission-water-supply :demand-step-lps 20.0 :use use)))
    (is (try (ws/commission-water-supply :demand-step-lps 20.0 :use use)
             false
             (catch clojure.lang.ExceptionInfo e (sub/safety-error? e))))))

(deftest test-community-scale-cap-enforced-g3
  ;; A service population above the community-scale cap is N1 (a municipal
  ;; utility) and is structurally refused before any run.
  (is (thrown? clojure.lang.ExceptionInfo
               (ws/commission-water-supply
                :demand-step-lps 20.0
                :service-population (inc ws/max-service-population)))))

(deftest test-at-cap-is-allowed
  (let [res (ws/commission-water-supply
             :demand-step-lps 20.0 :service-population ws/max-service-population)]
    (is (= (:service-population res) ws/max-service-population))
    (is (:level-restored res))))

(deftest test-reservoir-self-regulates
  ;; No pump command: a gravity-fed tank with a head-dependent leak drains
  ;; toward a lower equilibrium (real first-order dynamics, not free fall to 0).
  (let [tank (ws/reservoir-plant :area-m2 20.0 :level-m 3.0 :demand-lps 10.0)
        start (sub/measure tank)]
    (dotimes [_ 100] (sub/plant-step! tank 0.0 1.0))
    (is (< (sub/measure tank) start))
    (is (>= (sub/measure tank) 0.0))))

(deftest test-datoms-are-aggregate-dry-run-no-server-key
  (let [res (ws/commission-water-supply :demand-step-lps 20.0)
        d (ws/to-datoms res "spring-001")]
    (is (= (get d ":water.supply/dry-run") true))
    (is (= (get d ":water.supply/server-held-key") false))
    (is (= (get d ":water.supply/representative") true))
    (is (= (get d ":water.supply/level-restored") true))
    (is (<= (get d ":water.supply/service-population") ws/max-service-population))))
