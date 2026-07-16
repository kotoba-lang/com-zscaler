(ns futawa.methods.test-agent
  "futawa 二輪 — agent gate tests. 1:1 port of py/test_agent.py (custom harness → clojure.test).
  Offline: capacity caps (G11), ABS-mandatory (G7), sound limit (G6), anti-surveillance (G8), USDC +
  tithe settlement (G16/G17/G18)."
  (:require [clojure.test :refer [deftest is]]
            [futawa.methods.agent :as agent]))

(deftest test-g11-displacement-cap
  (is (= false (get (agent/g11-capacity-caps 300 12.0 150.0) "ok"))))

(deftest test-g11-power-cap
  (is (= false (get (agent/g11-capacity-caps 200 16.0 150.0) "ok"))))

(deftest test-g11-weight-cap
  (is (= false (get (agent/g11-capacity-caps 200 12.0 250.0) "ok"))))

(deftest test-g11-within-caps
  (is (= true (get (agent/g11-capacity-caps 200 12.0 150.0) "ok"))))

(deftest test-g7-abs-mandatory-low-cc
  (is (= false (get (agent/g7-abs-mandatory 100 8.0) "abs_mandatory"))))

(deftest test-g7-abs-mandatory-high-cc
  (is (= true (get (agent/g7-abs-mandatory 150 8.0) "abs_mandatory"))))

(deftest test-g6-sound-under-limit
  (is (= true (get (agent/g6-sound-check 75.0) "ok"))))

(deftest test-g6-sound-over-limit
  (is (= false (get (agent/g6-sound-check 85.0) "ok"))))

(deftest test-g8-surveillance-clean
  (is (= true (get (agent/g8-surveillance-check ["harness" "ecu" "battery"]) "ok"))))

(deftest test-g8-surveillance-gps
  (is (= false (get (agent/g8-surveillance-check ["harness" "GPS module" "ecu"]) "ok"))))

(deftest test-g8-surveillance-v2x
  (is (= false (get (agent/g8-surveillance-check ["harness" "v2x modem"]) "ok"))))

(deftest test-engine-assembly-blocked-on-caps
  (let [out (agent/handle-engine-assembly {"engine_id" "e1" "displacement_cc" 300 "power_kw" 12.0 "dry_weight_kg" 150.0})]
    (is (= true (get-in out ["engine_attestation" "blocked"])))))

(deftest test-engine-assembly-passes-caps
  (let [out (agent/handle-engine-assembly {"engine_id" "e1" "displacement_cc" 200 "power_kw" 12.0 "dry_weight_kg" 150.0})]
    (is (= true (get-in out ["engine_attestation" "g11_cap_verified"])))))

(deftest test-electrical-harness-blocks-surveillance
  (let [out (agent/handle-electrical-harness {"bom_items" ["harness" "telematics modem"]})]
    (is (= true (get-in out ["electrical_attestation" "blocked"])))))

(deftest test-suspension-brake-abs-check
  (let [out (agent/handle-suspension-brake {"displacement_cc" 150 "power_kw" 10.0 "suspension_brake_id" "sb1"})]
    (is (= true (get-in out ["suspension_brake_attestation" "abs_mandatory_certified"])))))

(deftest test-test-dyno-sound-limit
  (let [out (agent/handle-test-dyno-road {"sound_db" 85.0 "test_id" "t1"})]
    (is (= true (get-in out ["test_record" "blocked"])))))

(deftest test-test-dyno-passes
  (let [out (agent/handle-test-dyno-road {"sound_db" 75.0 "dyno_power_kw" 12.0 "dyno_torque_nm" 50.0 "abs_pass" true "test_id" "t1"})]
    (is (= "pass" (get-in out ["test_record" "result"])))))

(deftest test-settlement-tithe-split
  (let [s (agent/build-settlement-intent 20000000)]
    (is (= 2000000 (get s "titheMinor")))
    (is (= "intent" (get s "state")))
    (is (= "usdc-base-l2" (get s "rail")))))

(deftest test-settlement-executed-with-sig
  (is (= "executed" (get (agent/build-settlement-intent 10000000 "0xsig") "state"))))
