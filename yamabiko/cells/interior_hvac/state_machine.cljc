(ns yamabiko.cells.interior-hvac.state-machine
  "1:1 port of cells/interior_hvac/state_machine.py — ADR-2605252600 L3.

  Al-honeycomb floor + fire-retardant seating + wheelchair-accessible toilets +
  HEPA HVAC + multilingual passenger information system. N6 enforcement: no
  third-party advertising; route + safety info only.")

(defn- interior-state
  "InteriorState defaults merged with any existing \"interior_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "interior_state" {})
        trainset-id (or (get existing "trainsetId")
                        (get state "trainsetId")
                        "YAMABIKO-TRAINSET-0001")
        car-index (or (get existing "carIndex")
                      (get state "carIndex")
                      0)]
    (merge {"phase" "init"
            "trainsetId" trainset-id
            "carIndex" car-index
            "completionPct" 0
            "floor" nil
            "seating" nil
            "accessibility" nil
            "hvac" nil
            "pisConfig" nil}
           existing)))

(defn transition-to-floor-installed
  "Install Al-honeycomb floor."
  [state]
  (let [s (interior-state state)]
    {"interior_state" (assoc s
                             "floor" {"material" "Al-honeycomb-with-vinyl"
                                      "thicknessMm" 35
                                      "fireClass" "EN 45545 R1 HL2"}
                             "phase" "floor_installed"
                             "completionPct" 18)
     "next_node" "seating"}))

(defn transition-to-seating-installed
  "Install fire-retardant seating (EN 45545 R1, single-class per N10)."
  [state]
  (let [s (interior-state state)]
    {"interior_state" (assoc s
                             "seating" {"type" "fire-retardant-fabric-EN 45545 R1"
                                        "pitch_mm" 990
                                        "rowCount" 17
                                        "wheelchairBays" 2
                                        "n10Note" "Wave 1 single-class only (N10 luxury-only excluded)"}
                             "phase" "seating_installed"
                             "completionPct" 38)
     "next_node" "accessibility"}))

(defn transition-to-accessibility-verified
  "Verify accessibility (wheelchair-accessible toilets, ramps, tactile marking)."
  [state]
  (let [s (interior-state state)]
    {"interior_state" (assoc s
                             "accessibility" {"wheelchairAccessibleToiletM2" 2.4
                                              "rampsCount" 2
                                              "tactileMarkingPath" "full"
                                              "vacuumWasteSystem" true}
                             "phase" "accessibility_verified"
                             "completionPct" 55)
     "next_node" "hvac"}))

(defn transition-to-hvac-installed
  "Install HEPA HVAC system."
  [state]
  (let [s (interior-state state)]
    {"interior_state" (assoc s
                             "hvac" {"type" "heat-pump"
                                     "hepaFilter" "H13"
                                     "freshAirM3PerHourPerPax" 30
                                     "co2SensorActive" true}
                             "phase" "hvac_installed"
                             "completionPct" 75)
     "next_node" "pis"}))

(defn transition-to-pis-configured
  "Configure passenger information system (G5 trilingual, N6 no ads, N8 no face recognition)."
  [state]
  (let [s (interior-state state)]
    {"interior_state" (assoc s
                             "pisConfig" {"languages" ["ja" "en" "local"]
                                          "g5Trilingual" true
                                          "contentTypes" ["route-info" "safety-info" "next-station" "emergency"]
                                          "n6AdvertisingPresent" false
                                          "n8FaceRecognitionPresent" false
                                          "accept" true}
                             "phase" "pis_configured"
                             "completionPct" 90)
     "next_node" "attestation"}))

(defn transition-to-attestation-emitted
  "Emit final interior attestation."
  [state]
  (let [s (interior-state state)]
    {"interior_state" (assoc s
                             "phase" "attestation_emitted"
                             "completionPct" 100)
     "interior_attestation" {"$type" "com.etzhayyim.yamabiko.interiorAttestation"
                             "trainsetId" (get s "trainsetId")
                             "carIndex" (get s "carIndex")
                             "floor" (get s "floor")
                             "seating" (get s "seating")
                             "accessibility" (get s "accessibility")
                             "hvac" (get s "hvac")
                             "pisConfig" (get s "pisConfig")
                             "recordedAt" "2026-05-26T12:00:00Z"}
     "next_node" "end"}))
