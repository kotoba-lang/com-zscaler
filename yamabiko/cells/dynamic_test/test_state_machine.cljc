(ns yamabiko.cells.dynamic-test.test-state-machine
  "Tests for yamabiko dynamic_test state machine (py→cljc port).
   Covers dynamic testing layer L5b: static tests → G12 KPI verification → dynamic run.
   Enforces G12: max speed ≤320 km/h, trainset ≤450 m, GoA ≤3."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.dynamic-test.state-machine :as sm]))

(deftest test-dynamic-test-happy-path
  (testing "Full dynamic test happy path"
    (let [init-state {"trainsetId" "TEST-TS-001"}
          s1 (sm/transition-to-static-test-passed init-state)
          s2 (sm/transition-to-g12-kpi-verified s1)
          s3 (sm/transition-to-dynamic-run-complete s2)
          s4 (sm/transition-to-record-emitted s3)
          record (get s4 "dynamic_test_record")]
      ;; Phase progression
      (is (= "static_test_passed" (get-in s1 ["dynamic_state" "phase"])))
      (is (= 25 (get-in s1 ["dynamic_state" "completionPct"])))
      (is (= "g12_kpi_verified" (get-in s2 ["dynamic_state" "phase"])))
      (is (= 50 (get-in s2 ["dynamic_state" "completionPct"])))
      (is (= "dynamic_run_complete" (get-in s3 ["dynamic_state" "phase"])))
      (is (= 92 (get-in s3 ["dynamic_state" "completionPct"])))
      (is (= "record_emitted" (get-in s4 ["dynamic_state" "phase"])))
      (is (= 100 (get-in s4 ["dynamic_state" "completionPct"])))

      ;; Record structure
      (is (= "com.etzhayyim.yamabiko.dynamicTestRecord" (get record "$type")))
      (is (= "TEST-TS-001" (get record "trainsetId")))
      (is (some? (get record "staticTestResult")))
      (is (some? (get record "g12KpiCheck")))
      (is (some? (get record "dynamicRunResult")))
      (is (= true (get record "overallAccept"))))))

(deftest test-dynamic-static-tests-pass
  (testing "Static tests cover all systems"
    (let [s (sm/transition-to-static-test-passed {})
          static (get-in s ["dynamic_state" "staticTestResult"])]
      (is (= "PASS" (get static "weightDistribution")))
      (is (= "PASS" (get static "pneumaticPressure")))
      (is (= "PASS" (get static "doorOperation")))
      (is (= "PASS" (get static "emergencyBrake")))
      (is (= "PASS" (get static "hvacCalibration"))))))

(deftest test-dynamic-g12-kpi-max-speed-320kmh
  (testing "G12 KPI enforces max speed ≤320 km/h Wave 1"
    (let [s (sm/transition-to-g12-kpi-verified {})
          kpi (get-in s ["dynamic_state" "g12KpiCheck"])]
      (is (= 320 (get kpi "designSpeedKmh")))
      (is (= 320 (get kpi "maxSpeedLimitKmh")))
      (is (<= (get kpi "designSpeedKmh") 320))
      (is (= true (get kpi "accept"))))))

(deftest test-dynamic-g12-kpi-trainset-length-450m
  (testing "G12 KPI enforces trainset length ≤450 m"
    (let [s (sm/transition-to-g12-kpi-verified {})
          kpi (get-in s ["dynamic_state" "g12KpiCheck"])]
      (is (= 100 (get kpi "trainsetLengthM")))
      (is (= 450 (get kpi "maxTrainsetLengthM")))
      (is (<= (get kpi "trainsetLengthM") 450)))))

(deftest test-dynamic-g12-kpi-goa3-no-goa4
  (testing "G12 KPI enforces GoA ≤3 (N7: no GoA 4 Wave 1)"
    (let [s (sm/transition-to-g12-kpi-verified {})
          kpi (get-in s ["dynamic_state" "g12KpiCheck"])]
      (is (= "GoA-3" (get kpi "atoLevel")))
      (is (= 3 (get kpi "atoMaxLevel")))
      (is (<= (get kpi "atoMaxLevel") 3)))))

(deftest test-dynamic-run-high-speed
  (testing "Dynamic run achieves high speed within G12 limit"
    (let [s (sm/transition-to-dynamic-run-complete {})
          result (get-in s ["dynamic_state" "dynamicRunResult"])]
      (is (>= (get result "testTrackLengthKm") 100)) ;; ≥100 km test track
      (is (>= (get result "totalDistanceKm") 1000))
      (is (>= (get result "maxAchievedSpeedKmh") 300))
      (is (<= (get result "maxAchievedSpeedKmh") 320))))) ;; Within G12 limit

(deftest test-dynamic-run-acceleration-deceleration
  (testing "Dynamic run measures acceleration and deceleration"
    (let [s (sm/transition-to-dynamic-run-complete {})
          result (get-in s ["dynamic_state" "dynamicRunResult"])]
      (is (some? (get result "accelerationMsps")))
      (is (some? (get result "decelerationMsps")))
      (is (>= (get result "decelerationMsps") (get result "accelerationMsps"))))))

(deftest test-dynamic-run-ride-quality
  (testing "Dynamic run ride quality within spec"
    (let [s (sm/transition-to-dynamic-run-complete {})
          result (get-in s ["dynamic_state" "dynamicRunResult"])]
      (is (< (get result "rideQualityRMSM") (get result "rideQualitySpecMaxRMSM"))))))
