(ns yamabiko.cells.carbody-fabrication.test-state-machine
  "Tests for yamabiko carbody_fabrication state machine (py→cljc port).
   Covers FSW seam welding progression: extrusion → FSW → spot welds → QA → attestation."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.carbody-fabrication.state-machine :as sm]))

(deftest test-carbody-happy-path
  (testing "Full carbody fabrication happy path"
    (let [init-state {"trainsetId" "TEST-TS-001" "carIndex" 1}
          s1 (sm/transition-to-extrusion-verified init-state)
          s2 (sm/transition-to-fsw-seams-complete s1)
          s3 (sm/transition-to-spot-welds-complete s2)
          s4 (sm/transition-to-dimensional-qa-passed s3)
          s5 (sm/transition-to-attestation-emitted s4)
          state-final (get s5 "carbody_state")
          attestation (get s5 "carbody_attestation")]
      ;; Phase progression
      (is (= "extrusion_verified" (get-in s1 ["carbody_state" "phase"])))
      (is (= 15 (get-in s1 ["carbody_state" "completionPct"])))
      (is (= "fsw_seams_complete" (get-in s2 ["carbody_state" "phase"])))
      (is (= 50 (get-in s2 ["carbody_state" "completionPct"])))
      (is (= "spot_welds_complete" (get-in s3 ["carbody_state" "phase"])))
      (is (= 70 (get-in s3 ["carbody_state" "completionPct"])))
      (is (= "dimensional_qa_passed" (get-in s4 ["carbody_state" "phase"])))
      (is (= 90 (get-in s4 ["carbody_state" "completionPct"])))
      (is (= "attestation_emitted" (get state-final "phase")))
      (is (= 100 (get state-final "completionPct")))

      ;; Attestation structure
      (is (= "com.etzhayyim.yamabiko.carbodyAttestation" (get attestation "$type")))
      (is (= "TEST-TS-001" (get attestation "trainsetId")))
      (is (= 1 (get attestation "carIndex")))
      (is (some? (get attestation "extrusionLot")))
      (is (some? (get attestation "fswSeams")))
      (is (some? (get attestation "spotWelds")))
      (is (some? (get attestation "dimensionalQa")))
      (is (some? (get attestation "attestingRobots")))
      (is (>= (count (get attestation "attestingRobots")) 2))))) ;; G4

(deftest test-carbody-extrusion-al6n01
  (testing "Extrusion lot is Al-6N01 double-skin"
    (let [s (sm/transition-to-extrusion-verified {})
          ext (get-in s ["carbody_state" "extrusionLot"])]
      (is (= "Al-6N01" (get ext "alloy")))
      (is (= true (get ext "doubleSkin")))
      (is (= 2.5 (get ext "thicknessMm"))))))

(deftest test-carbody-fsw-seams-four-sides
  (testing "FSW seams cover all four sides"
    (let [s (sm/transition-to-fsw-seams-complete {})
          seams (get-in s ["carbody_state" "fswSeams"])]
      (is (= 4 (count seams)))
      (is (every? #(contains? % "seam") seams))
      (is (every? #(contains? % "lengthM") seams))
      (is (every? #(contains? % "videoCid") seams)))))

(deftest test-carbody-dimensional-within-spec
  (testing "Dimensional QA passes within tolerance"
    (let [s (sm/transition-to-dimensional-qa-passed {})
          qa (get-in s ["carbody_state" "dimensionalQa"])]
      (is (= 25000 (get qa "lengthMm")))
      (is (= 25000 (get qa "lengthSpecMm")))
      (is (= 5 (get qa "lengthTolMm")))
      (is (= true (get qa "accept"))))))

(deftest test-carbody-robot-metrology-witness
  (testing "Carbody attestation has tsugite (FSW lead) and mimi (metrology) witness"
    (let [s (sm/transition-to-attestation-emitted {})
          robots (get-in s ["carbody_attestation" "attestingRobots"])
          roles (mapv #(get % "role") robots)]
      (is (some #{"fsw_lead"} roles))
      (is (some #{"metrology"} roles)))))
