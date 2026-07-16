(ns hodoki.methods.test-agent
  "hodoki 解き — agent gate tests. 1:1 port of py/test_agent.py (custom harness → clojure.test).
  Offline: G5 charter scan, G6 f-gas, G8 ECU wipe + witness, G12 part DID, G13 recovery/ASR, G14
  PGM yield, settlement + 9 handlers."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [hodoki.methods.agent :as agent]))

(deftest test-charter-scan-passes-civilian
  (let [r (agent/scan-charter-compliance "ABC123" "2024 Toyota Corolla")]
    (is (and (= true (get r "ok")) (= "passed" (get r "status"))))))

(deftest test-charter-scan-blocks-military
  (let [r (agent/scan-charter-compliance "XYZ789" "Military armored platform")]
    (is (and (= false (get r "ok")) (= "failed" (get r "status"))))))

(deftest test-charter-scan-blocks-weapon
  (is (= false (get (agent/scan-charter-compliance "XYZ789" "Weapon delivery system") "ok"))))

(deftest test-fgas-capture-below-threshold
  (is (= false (get (agent/check-fgas-compliance 100.0 94.0) "ok"))))

(deftest test-fgas-capture-at-threshold
  (is (= true (get (agent/check-fgas-compliance 100.0 95.0) "ok"))))

(deftest test-ecu-wipe-requires-both
  (let [r (agent/ecu-data-wipe-mandatory false 2)]
    (is (and (= false (get r "ok")) (= true (get r "blocked"))))))

(deftest test-ecu-wipe-requires-witness-quorum
  (let [r (agent/ecu-data-wipe-mandatory true 1)]
    (is (and (= false (get r "ok")) (= true (get r "blocked"))))))

(deftest test-ecu-wipe-with-quorum
  (is (= true (get (agent/ecu-data-wipe-mandatory true 2) "ok"))))

(deftest test-part-did-generation
  (is (str/starts-with? (agent/issue-part-did "VIN123" "Engine Block") "did:web:hodoki.etzhayyim.com:part:")))

(deftest test-pgm-yield-below-threshold
  (let [r (agent/audit-pgm-yield 94.0)]
    (is (and (= false (get r "ok")) (= true (get r "blocked"))))))

(deftest test-pgm-yield-at-threshold
  (is (= true (get (agent/audit-pgm-yield 95.0) "ok"))))

(deftest test-recovery-rate-below-threshold
  (let [r (agent/calc-recovery-rate 700 100 40 200 1040)]
    (is (and (= false (get r "ok")) (= true (get r "blocked"))))))

(deftest test-recovery-rate-at-threshold
  (let [r (agent/calc-recovery-rate 800 150 50 20 1000)]
    (is (and (= true (get r "ok")) (= 100 (get r "recovery_pct"))))))

(deftest test-asr-landfill-cap
  (let [r (agent/calc-recovery-rate 850 150 0 50 1000)]
    (is (and (= false (get r "ok")) (= true (get r "blocked"))))))

(deftest test-asr-landfill-below-cap
  (let [r (agent/calc-recovery-rate 850 150 0 40 1000)]
    (is (and (= true (get r "ok")) (= 4 (get r "asr_landfill_pct"))))))

(deftest test-settlement-tithe-split
  (let [s (agent/build-settlement-intent 10000000)]
    (is (= 1000000 (get s "titheMinor")))
    (is (= "intent" (get s "state")))
    (is (= "usdc-base-l2" (get s "rail")))))

(deftest test-settlement-executed-with-sig
  (is (= "executed" (get (agent/build-settlement-intent 10000000 "0xsig") "state"))))

(deftest test-intake-audit-handler
  (is (= "passed" (get (agent/handle-intake-audit {"vin" "VIN123" "vehicle_desc" "2024 Honda Civic"}) "charter_status"))))

(deftest test-depollution-handler-passes
  (let [out (agent/handle-depollution {"vin" "VIN123" "ecu_wiped" true "witness_count" 2 "fgas_mass_g" 100.0 "fgas_target_pct" 96.0})]
    (is (= "passed" (get out "status")))))

(deftest test-battery-routing-soh-high
  (is (= "hikari-second-life" (get (agent/handle-battery-handling {"vin" "VIN123" "soh_pct" 75}) "routing_decision"))))

(deftest test-battery-routing-soh-medium
  (is (= "cell-recycle" (get (agent/handle-battery-handling {"vin" "VIN123" "soh_pct" 50}) "routing_decision"))))

(deftest test-battery-routing-soh-low
  (is (= "kanayama-reclaim" (get (agent/handle-battery-handling {"vin" "VIN123" "soh_pct" 30}) "routing_decision"))))

(deftest test-parts-harvest-handler
  (is (str/starts-with? (get (agent/handle-parts-harvest {"vin" "VIN123" "part_name" "Engine Block"}) "part_did") "did:web:hodoki.etzhayyim.com")))

(deftest test-catalyst-recovery-handler
  (is (= "passed" (get (agent/handle-catalyst-recovery {"pgm_yield_pct" 96.0}) "status"))))

(deftest test-body-shred-handler
  (is (= 100 (get (agent/handle-body-shred {"ferrous_kg" 800.0 "non_ferrous_kg" 150.0 "copper_kg" 50.0 "asr_kg" 20.0 "input_mass_kg" 1000.0}) "recovery_pct"))))
