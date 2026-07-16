(ns yamabiko.cells.final-assembly.test-state-machine
  "Tests for yamabiko final_assembly state machine (py→cljc port).
   Covers final assembly layer L5a: marriage + livery (N6 no ads).
   Enforces G4 ≥2 robot witness on critical fasteners."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.final-assembly.state-machine :as sm]))

(deftest test-final-assembly-happy-path
  (testing "Full final assembly happy path"
    (let [init-state {"trainsetId" "TEST-TS-001"}
          s1 (sm/transition-to-inputs-verified init-state)
          s2 (sm/transition-to-bogie-carbody-married s1)
          s3 (sm/transition-to-cab-interior-installed s2)
          s4 (sm/transition-to-livery-applied s3)
          s5 (sm/transition-to-attestation-emitted s4)
          attestation (get s5 "final_assembly_attestation")]
      ;; Phase progression
      (is (= "inputs_verified" (get-in s1 ["final_state" "phase"])))
      (is (= 15 (get-in s1 ["final_state" "completionPct"])))
      (is (= "bogie_carbody_married" (get-in s2 ["final_state" "phase"])))
      (is (= 50 (get-in s2 ["final_state" "completionPct"])))
      (is (= "cab_interior_installed" (get-in s3 ["final_state" "phase"])))
      (is (= 75 (get-in s3 ["final_state" "completionPct"])))
      (is (= "livery_applied" (get-in s4 ["final_state" "phase"])))
      (is (= 90 (get-in s4 ["final_state" "completionPct"])))
      (is (= "attestation_emitted" (get-in s5 ["final_state" "phase"])))
      (is (= 100 (get-in s5 ["final_state" "completionPct"])))

      ;; Attestation structure
      (is (= "com.etzhayyim.yamabiko.finalAssemblyAttestation" (get attestation "$type")))
      (is (= "TEST-TS-001" (get attestation "trainsetId")))
      (is (some? (get attestation "inputs")))
      (is (some? (get attestation "marriage")))
      (is (some? (get attestation "livery")))
      (is (some? (get attestation "attestingRobots")))
      (is (>= (count (get attestation "attestingRobots")) 2))))) ;; G4

(deftest test-final-assembly-inputs-cids
  (testing "Inputs have carbody, bogie, interior, traction CIDs"
    (let [s (sm/transition-to-inputs-verified {})
          inputs (get-in s ["final_state" "inputs"])]
      (is (= 4 (count (get inputs "carbodyCids"))))
      (is (= 8 (count (get inputs "bogieCids"))))
      (is (= 4 (count (get inputs "interiorCids"))))
      (is (some? (get inputs "tractionCid"))))))

(deftest test-final-assembly-marriage-fastener-torque
  (testing "Bogie-carbody marriage fastener torque is spec'd (850 Nm)"
    (let [s (sm/transition-to-bogie-carbody-married {})
          marriage (get-in s ["final_state" "marriage"])]
      (is (= 850 (get marriage "marriageFastenerTorqueNm")))
      (is (= 850 (get marriage "marriageFastenerSpecNm")))
      (is (= 4 (get marriage "carCount")))
      (is (= 2 (get marriage "bogiesPerCar"))))))

(deftest test-final-assembly-livery-n6-no-ads
  (testing "Livery enforces N6: no advertising"
    (let [s (sm/transition-to-livery-applied {})
          livery (get-in s ["final_state" "livery"])]
      (is (= true (get livery "n6AdvertisingFreeAccept")))
      (is (= "OEM-default-white-with-route-band" (get livery "scheme")))
      (is (< (get livery "vocGPerL") 100))))) ;; VOC compliance

(deftest test-final-assembly-robot-witness
  (testing "Final assembly attestation has otete (heavy) and mimi (precision) witness (G4)"
    (let [s (sm/transition-to-attestation-emitted {})
          robots (get-in s ["final_assembly_attestation" "attestingRobots"])
          roles (mapv #(get % "role") robots)]
      (is (>= (count robots) 2))
      (is (some #{"marriage_lead"} roles))
      (is (some #{"alignment"} roles)))))
