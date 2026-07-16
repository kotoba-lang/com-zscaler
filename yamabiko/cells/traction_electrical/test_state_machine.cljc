(ns yamabiko.cells.traction-electrical.test-state-machine
  "Tests for yamabiko traction_electrical state machine (py→cljc port).
   Enforces G1 + N5 (Apache 2.0 + Charter Rider) + G7 propulsion guard + N7 (no GoA 4)."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.traction-electrical.state-machine :as sm]))

(deftest test-traction-happy-path
  (testing "Full traction electrical assembly happy path"
    (let [init-state {"trainsetId" "TEST-TS-001" "propulsionType" "overhead-25kV-AC"}
          s1 (sm/transition-to-propulsion-guard-checked init-state)
          s2 (sm/transition-to-pantograph-installed s1)
          s3 (sm/transition-to-inverter-installed s2)
          s4 (sm/transition-to-atp-ato-flashed s3)
          s5 (sm/transition-to-open-source-verified s4)
          s6 (sm/transition-to-attestation-emitted s5)
          attestation (get s6 "traction_electrical_attestation")]
      ;; Phase progression
      (is (= "propulsion_guard_checked" (get-in s1 ["traction_state" "phase"])))
      (is (= 15 (get-in s1 ["traction_state" "completionPct"])))
      (is (= "pantograph_installed" (get-in s2 ["traction_state" "phase"])))
      (is (= 35 (get-in s2 ["traction_state" "completionPct"])))
      (is (= "inverter_installed" (get-in s3 ["traction_state" "phase"])))
      (is (= 55 (get-in s3 ["traction_state" "completionPct"])))
      (is (= "atp_ato_flashed" (get-in s4 ["traction_state" "phase"])))
      (is (= 75 (get-in s4 ["traction_state" "completionPct"])))
      (is (= "open_source_verified" (get-in s5 ["traction_state" "phase"])))
      (is (= 92 (get-in s5 ["traction_state" "completionPct"])))
      (is (= "attestation_emitted" (get-in s6 ["traction_state" "phase"])))
      (is (= 100 (get-in s6 ["traction_state" "completionPct"])))

      ;; Attestation structure
      (is (= "com.etzhayyim.yamabiko.tractionElectricalAttestation" (get attestation "$type")))
      (is (= "TEST-TS-001" (get attestation "trainsetId")))
      (is (= "overhead-25kV-AC" (get attestation "propulsionType")))
      (is (some? (get attestation "propulsionGuard")))
      (is (some? (get attestation "pantograph")))
      (is (some? (get attestation "inverter")))
      (is (some? (get attestation "atpAtoFirmware")))
      (is (some? (get attestation "openSourceVerification"))))))

(deftest test-traction-g7-propulsion-guard-allowed-r0r1
  (testing "G7 propulsion guard allows R0/R1 types (25kV, 1500V, BEMU, H2)"
    (let [s (sm/transition-to-propulsion-guard-checked {"propulsionType" "overhead-25kV-AC"})
          guard (get-in s ["traction_state" "propulsionGuard"])]
      (is (= true (get guard "accept")))
      (is (some #{"overhead-25kV-AC"} (get guard "allowedR0R1"))))))

(deftest test-traction-g7-propulsion-guard-r2plus-extends-nh3
  (testing "G7 propulsion guard R2+ extends to NH3-fuel-cell"
    (let [s (sm/transition-to-propulsion-guard-checked {"propulsionType" "overhead-25kV-AC"})
          guard (get-in s ["traction_state" "propulsionGuard"])]
      (is (some #{"NH3-fuel-cell-hybrid"} (get guard "allowedR2Plus")))
      (is (not (some #{"NH3-fuel-cell-hybrid"} (get guard "allowedR0R1")))))))

(deftest test-traction-g1-n5-apache-charter-rider
  (testing "G1 + N5 enforcement: ATP/ATO firmware Apache 2.0 + Charter Rider (no NDA)"
    (let [s1 (sm/transition-to-atp-ato-flashed {})
          s2 (sm/transition-to-open-source-verified s1)
          verify (get-in s2 ["traction_state" "openSourceVerification"])]
      (is (= "active" (get verify "g1Enforcement")))
      (is (= "active" (get verify "n5Enforcement")))
      (is (= true (get verify "containsApache2")))
      (is (= true (get verify "containsCharterRider")))
      (is (= false (get verify "proprietaryNdaPresent")))
      (is (= true (get verify "accept"))))))

(deftest test-traction-pantograph-25kv
  (testing "Pantograph rated for 25 kV AC, 1000 A"
    (let [s (sm/transition-to-pantograph-installed {})
          panto (get-in s ["traction_state" "pantograph"])]
      (is (= "wing" (get panto "type")))
      (is (= 25000 (get panto "ratedVoltageV")))
      (is (= 1000 (get panto "currentA"))))))

(deftest test-traction-inverter-sic-mosfet
  (testing "Inverter is SiC-MOSFET with high efficiency"
    (let [s (sm/transition-to-inverter-installed {})
          inv (get-in s ["traction_state" "inverter"])]
      (is (= "SiC-MOSFET" (get inv "type")))
      (is (>= (get inv "efficiencyPct") 98)))))

(deftest test-traction-atp-ato-etcs-goa3
  (testing "ATP/ATO firmware: ETCS-Level-2, GoA-3 (N7: no GoA 4 Wave 1)"
    (let [s (sm/transition-to-atp-ato-flashed {})
          fw (get-in s ["traction_state" "atpAtoFirmware"])]
      (is (= "ETCS-Level-2" (get fw "atpStandard")))
      (is (= "GoA-3" (get fw "atoLevel")))
      (is (= 3 (get fw "atoMaxLevel")))
      (is (clojure.string/includes? (get fw "n7Note") "N7")))))
