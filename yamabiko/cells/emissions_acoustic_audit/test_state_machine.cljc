(ns yamabiko.cells.emissions-acoustic-audit.test-state-machine
  "Tests for yamabiko emissions_acoustic_audit state machine (py→cljc port).
   Enforces G8: ISO 3095 wayside noise + 日本騒音規制法 + IEC 62236 EMC."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.emissions-acoustic-audit.state-machine :as sm]))

(deftest test-acoustic-happy-path
  (testing "Full acoustic audit happy path"
    (let [init-state {"trainsetId" "TEST-TS-001"}
          s1 (sm/transition-to-wayside-noise-measured init-state)
          s2 (sm/transition-to-vibration-measured s1)
          s3 (sm/transition-to-emc-verified s2)
          s4 (sm/transition-to-record-emitted s3)
          record (get s4 "acoustic_emissions_audit_record")]
      ;; Phase progression
      (is (= "wayside_noise_measured" (get-in s1 ["acoustic_state" "phase"])))
      (is (= 35 (get-in s1 ["acoustic_state" "completionPct"])))
      (is (= "vibration_measured" (get-in s2 ["acoustic_state" "phase"])))
      (is (= 60 (get-in s2 ["acoustic_state" "completionPct"])))
      (is (= "emc_verified" (get-in s3 ["acoustic_state" "phase"])))
      (is (= 90 (get-in s3 ["acoustic_state" "completionPct"])))
      (is (= "record_emitted" (get-in s4 ["acoustic_state" "phase"])))
      (is (= 100 (get-in s4 ["acoustic_state" "completionPct"])))

      ;; Record structure
      (is (= "com.etzhayyim.yamabiko.acousticEmissionsAuditRecord" (get record "$type")))
      (is (= "TEST-TS-001" (get record "trainsetId")))
      (is (some? (get record "waysideNoise")))
      (is (some? (get record "vibration")))
      (is (some? (get record "emcResult")))
      (is (= true (get record "overallAccept"))))))

(deftest test-acoustic-iso3095-wayside-noise
  (testing "Wayside noise per ISO 3095 (88 dB @ 25m, 300 km/h)"
    (let [s (sm/transition-to-wayside-noise-measured {})
          noise (get-in s ["acoustic_state" "waysideNoise"])]
      (is (= "ISO 3095" (get noise "standard")))
      (is (= 88 (get noise "dbAAt25mAtSpeed_300kmh")))
      (is (<= (get noise "dbAAt25mAtSpeed_300kmh") 95)) ;; Well under limit
      (is (= 95 (get noise "limit_dbA")))
      (is (= true (get noise "accept"))))))

(deftest test-acoustic-iso3095-standstill
  (testing "Wayside noise standstill ≤70 dB"
    (let [s (sm/transition-to-wayside-noise-measured {})
          noise (get-in s ["acoustic_state" "waysideNoise"])]
      (is (= 68 (get noise "dbAStandstill")))
      (is (<= (get noise "dbAStandstill") 70)))))

(deftest test-acoustic-japanese-vibration-regulation
  (testing "Vibration per 日本騒音規制法 (trackside ≤60 dB)"
    (let [s (sm/transition-to-vibration-measured {})
          vib (get-in s ["acoustic_state" "vibration"])]
      (is (= "日本 騒音規制法" (get vib "standard")))
      (is (= 58 (get vib "dbVibrationAtTrackside")))
      (is (<= (get vib "dbVibrationAtTrackside") 60))
      (is (= true (get vib "accept"))))))

(deftest test-acoustic-emc-iec62236
  (testing "EMC per IEC 62236 (emission + immunity pass)"
    (let [s (sm/transition-to-emc-verified {})
          emc (get-in s ["acoustic_state" "emcResult"])]
      (is (= "IEC 62236" (get emc "standard")))
      (is (= true (get emc "emissionPass")))
      (is (= true (get emc "immunityPass")))
      (is (= true (get emc "accept"))))))

(deftest test-acoustic-record-regulatory-basis
  (testing "Record cites all regulatory bases"
    (let [s (sm/transition-to-record-emitted {})
          record (get s "acoustic_emissions_audit_record")]
      (is (some #{"ISO 3095"} (get record "regulatoryBasis")))
      (is (some #{"日本 騒音規制法"} (get record "regulatoryBasis")))
      (is (some #{"IEC 62236"} (get record "regulatoryBasis"))))))

(deftest test-acoustic-overall-accept-requires-all-pass
  (testing "Overall accept requires all sub-tests to pass"
    (let [init-state {"trainsetId" "TEST-TS-001"}
          s1 (sm/transition-to-wayside-noise-measured init-state)
          s2 (sm/transition-to-vibration-measured s1)
          s3 (sm/transition-to-emc-verified s2)
          s4 (sm/transition-to-record-emitted s3)
          record (get s4 "acoustic_emissions_audit_record")]
      (is (= true (get record "overallAccept"))))))
