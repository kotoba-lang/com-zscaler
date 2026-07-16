(ns yamabiko.cells.bogie-assembly.test-state-machine
  "Tests for yamabiko bogie_assembly state machine (py→cljc port).
   Tests cover the full happy path: frame → wheel → motor → brake → air → attestation.
   Verifies completion % progression, phase transitions, and final attestation structure."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.bogie-assembly.state-machine :as sm]))

(deftest test-bogie-happy-path
  (testing "Full bogie assembly happy path"
    (let [init-state {"trainsetId" "TEST-TS-001" "bogieIndex" 1}
          ;; Transition 1: frame prepared
          s1 (sm/transition-to-frame-prepared init-state)
          state-1 (get s1 "bogie_state")
          ;; Transition 2: wheel set mounted
          s2 (sm/transition-to-wheel-set-mounted s1)
          state-2 (get s2 "bogie_state")
          ;; Transition 3: motor installed
          s3 (sm/transition-to-motor-installed s2)
          state-3 (get s3 "bogie_state")
          ;; Transition 4: brake integrated
          s4 (sm/transition-to-brake-integrated s3)
          state-4 (get s4 "bogie_state")
          ;; Transition 5: air spring installed
          s5 (sm/transition-to-air-spring-installed s4)
          state-5 (get s5 "bogie_state")
          ;; Transition 6: attestation emitted
          s6 (sm/transition-to-attestation-emitted s5)
          state-6 (get s6 "bogie_state")
          attestation (get s6 "bogie_attestation")]
      ;; Verify phase progression
      (is (= "frame_prepared" (get state-1 "phase")))
      (is (= 15 (get state-1 "completionPct")))
      (is (= "wheel_set_mounted" (get state-2 "phase")))
      (is (= 35 (get state-2 "completionPct")))
      (is (= "motor_installed" (get state-3 "phase")))
      (is (= 60 (get state-3 "completionPct")))
      (is (= "brake_integrated" (get state-4 "phase")))
      (is (= 78 (get state-4 "completionPct")))
      (is (= "air_spring_installed" (get state-5 "phase")))
      (is (= 90 (get state-5 "completionPct")))
      (is (= "attestation_emitted" (get state-6 "phase")))
      (is (= 100 (get state-6 "completionPct")))

      ;; Verify attestation record structure
      (is (= "com.etzhayyim.yamabiko.bogieAttestation" (get attestation "$type")))
      (is (= "TEST-TS-001" (get attestation "trainsetId")))
      (is (= 1 (get attestation "bogieIndex")))
      (is (some? (get attestation "frameLot")))
      (is (some? (get attestation "wheelSetLot")))
      (is (some? (get attestation "motorLot")))
      (is (some? (get attestation "brakeSystem")))
      (is (some? (get attestation "airSpring")))
      (is (some? (get attestation "attestingRobots")))
      (is (>= (count (get attestation "attestingRobots")) 2)) ;; G4: ≥2 robots
      (is (= "wheel" (get s1 "next_node")))
      (is (= "end" (get s6 "next_node"))))))

(deftest test-bogie-frame-lot-details
  (testing "Frame lot contains R3+ source info"
    (let [s (sm/transition-to-frame-prepared {})
          frame-lot (get-in s ["bogie_state" "frameLot"])]
      (is (= "external-cast-steel-R1" (get frame-lot "source")))
      (is (= "R3+ source from igata Wave 2" (get frame-lot "note")))
      (is (= "BOGIE-FRAME-0011" (get frame-lot "lotId"))))))

(deftest test-bogie-motor-specs
  (testing "Motor has correct electrical specs (PMSM)"
    (let [s (sm/transition-to-motor-installed {})
          motor-lot (get-in s ["bogie_state" "motorLot"])]
      (is (= "PMSM" (get motor-lot "type")))
      (is (= 305 (get motor-lot "powerKw")))
      (is (= 1100 (get motor-lot "ratedVoltageV"))))))

(deftest test-bogie-brake-regenerative
  (testing "Brake system allows regeneration"
    (let [s (sm/transition-to-brake-integrated {})
          brake (get-in s ["bogie_state" "brakeSystem"])]
      (is (= true (get brake "regenerativeAllowed")))
      (is (= "tread-disc-hybrid" (get brake "type"))))))

(deftest test-bogie-air-spring-control
  (testing "Air spring has leveling control"
    (let [s (sm/transition-to-air-spring-installed {})
          air-spring (get-in s ["bogie_state" "airSpring"])]
      (is (= true (get air-spring "levelingControl")))
      (is (= "coil" (get air-spring "primary")))
      (is (= "air-bellows" (get air-spring "secondary"))))))

(deftest test-bogie-robot-witness
  (testing "Attestation has ≥2 robot witness signatures (G4)"
    (let [s (sm/transition-to-attestation-emitted {})
          robot-sigs (get-in s ["bogie_attestation" "attestingRobots"])]
      (is (>= (count robot-sigs) 2))
      (is (every? #(and (contains? % "robotDid")
                        (contains? % "role")
                        (contains? % "timestamp")
                        (contains? % "signature"))
                  robot-sigs)))))
