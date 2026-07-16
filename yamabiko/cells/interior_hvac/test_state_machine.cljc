(ns yamabiko.cells.interior-hvac.test-state-machine
  "Tests for yamabiko interior_hvac state machine (py→cljc port).
   Covers interior assembly: floor → seating → accessibility → HVAC → PIS → attestation.
   Enforces N6 (no ads) and N8 (no face recognition) gates."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.interior-hvac.state-machine :as sm]))

(deftest test-interior-happy-path
  (testing "Full interior assembly happy path"
    (let [init-state {"trainsetId" "TEST-TS-001" "carIndex" 2}
          s1 (sm/transition-to-floor-installed init-state)
          s2 (sm/transition-to-seating-installed s1)
          s3 (sm/transition-to-accessibility-verified s2)
          s4 (sm/transition-to-hvac-installed s3)
          s5 (sm/transition-to-pis-configured s4)
          s6 (sm/transition-to-attestation-emitted s5)
          attestation (get s6 "interior_attestation")]
      ;; Phase progression
      (is (= "floor_installed" (get-in s1 ["interior_state" "phase"])))
      (is (= 18 (get-in s1 ["interior_state" "completionPct"])))
      (is (= "seating_installed" (get-in s2 ["interior_state" "phase"])))
      (is (= 38 (get-in s2 ["interior_state" "completionPct"])))
      (is (= "accessibility_verified" (get-in s3 ["interior_state" "phase"])))
      (is (= 55 (get-in s3 ["interior_state" "completionPct"])))
      (is (= "hvac_installed" (get-in s4 ["interior_state" "phase"])))
      (is (= 75 (get-in s4 ["interior_state" "completionPct"])))
      (is (= "pis_configured" (get-in s5 ["interior_state" "phase"])))
      (is (= 90 (get-in s5 ["interior_state" "completionPct"])))
      (is (= "attestation_emitted" (get-in s6 ["interior_state" "phase"])))
      (is (= 100 (get-in s6 ["interior_state" "completionPct"])))

      ;; Attestation structure
      (is (= "com.etzhayyim.yamabiko.interiorAttestation" (get attestation "$type")))
      (is (= "TEST-TS-001" (get attestation "trainsetId")))
      (is (= 2 (get attestation "carIndex")))
      (is (some? (get attestation "floor")))
      (is (some? (get attestation "seating")))
      (is (some? (get attestation "accessibility")))
      (is (some? (get attestation "hvac")))
      (is (some? (get attestation "pisConfig"))))))

(deftest test-interior-floor-fire-class
  (testing "Floor meets EN 45545 R1 fire class"
    (let [s (sm/transition-to-floor-installed {})
          floor (get-in s ["interior_state" "floor"])]
      (is (= "Al-honeycomb-with-vinyl" (get floor "material")))
      (is (= 35 (get floor "thicknessMm")))
      (is (= "EN 45545 R1 HL2" (get floor "fireClass"))))))

(deftest test-interior-seating-single-class
  (testing "Seating is single-class (N10 luxury-only excluded)"
    (let [s (sm/transition-to-seating-installed {})
          seating (get-in s ["interior_state" "seating"])]
      (is (= "fire-retardant-fabric-EN 45545 R1" (get seating "type")))
      (is (>= (get seating "wheelchairBays") 2))
      (is (clojure.string/includes? (get seating "n10Note") "single-class")))))

(deftest test-interior-accessibility-full
  (testing "Accessibility features are complete"
    (let [s (sm/transition-to-accessibility-verified {})
          a11y (get-in s ["interior_state" "accessibility"])]
      (is (some? (get a11y "wheelchairAccessibleToiletM2")))
      (is (>= (get a11y "rampsCount") 1))
      (is (= "full" (get a11y "tactileMarkingPath")))
      (is (= true (get a11y "vacuumWasteSystem"))))))

(deftest test-interior-hvac-hepa-h13
  (testing "HVAC has H13 HEPA filter"
    (let [s (sm/transition-to-hvac-installed {})
          hvac (get-in s ["interior_state" "hvac"])]
      (is (= "heat-pump" (get hvac "type")))
      (is (= "H13" (get hvac "hepaFilter")))
      (is (= true (get hvac "co2SensorActive"))))))

(deftest test-interior-pis-n6-no-ads
  (testing "PIS enforces N6: no advertising"
    (let [s (sm/transition-to-pis-configured {})
          pis (get-in s ["interior_state" "pisConfig"])]
      (is (>= (count (get pis "languages")) 3)) ;; G5 trilingual
      (is (= true (get pis "g5Trilingual")))
      (is (= false (get pis "n6AdvertisingPresent"))) ;; N6 gate
      (is (= false (get pis "n8FaceRecognitionPresent"))))) ;; N8 gate
      )

(deftest test-interior-pis-content-types
  (testing "PIS content is route/safety/emergency only"
    (let [s (sm/transition-to-pis-configured {})
          content (get-in s ["interior_state" "pisConfig" "contentTypes"])]
      (is (some #{"route-info"} content))
      (is (some #{"safety-info"} content))
      (is (some #{"emergency"} content)))))
