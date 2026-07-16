(ns yamabiko.cells.final-assembly.state-machine
  "1:1 port of cells/final_assembly/state_machine.py — ADR-2605252600 L5a.

  Carbody + bogie + interior + traction electrical marriage + cab + livery.
  ≥2 robot witness on critical fasteners (G4).")

(defn- final-state
  "FinalState defaults merged with any existing \"final_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "final_state" {})
        trainset-id (or (get existing "trainsetId")
                        (get state "trainsetId")
                        "YAMABIKO-TRAINSET-0001")]
    (merge {"phase" "init"
            "trainsetId" trainset-id
            "completionPct" 0
            "inputs" nil
            "marriage" nil
            "livery" nil
            "robotSignatures" nil}
           existing)))

(defn transition-to-inputs-verified
  "Verify input attestations (carbody, bogie, interior, traction CIDs)."
  [state]
  (let [s (final-state state)]
    {"final_state" (assoc s
                          "inputs" {"carbodyCids" ["bafkreicar1..." "bafkreicar2..." "bafkreicar3..." "bafkreicar4..."]
                                    "bogieCids" ["bafkreibog1..." "bafkreibog2..." "bafkreibog3..." "bafkreibog4..." "bafkreibog5..." "bafkreibog6..." "bafkreibog7..." "bafkreibog8..."]
                                    "interiorCids" ["bafkreiint1..." "bafkreiint2..." "bafkreiint3..." "bafkreiint4..."]
                                    "tractionCid" "bafkreitr..."}
                          "phase" "inputs_verified"
                          "completionPct" 15)
     "next_node" "marriage"}))

(defn transition-to-bogie-carbody-married
  "Marry bogie to carbody (G4: ≥2 robot witness on fasteners)."
  [state]
  (let [s (final-state state)]
    {"final_state" (assoc s
                          "marriage" {"carCount" 4
                                      "bogiesPerCar" 2
                                      "marriageFastenerTorqueNm" 850
                                      "marriageFastenerSpecNm" 850}
                          "phase" "bogie_carbody_married"
                          "completionPct" 50)
     "next_node" "cab"}))

(defn transition-to-cab-interior-installed
  "Install cab and interior (seating, HVAC, PIS)."
  [state]
  (let [s (final-state state)]
    {"final_state" (assoc s
                          "phase" "cab_interior_installed"
                          "completionPct" 75)
     "next_node" "livery"}))

(defn transition-to-livery-applied
  "Apply livery (N6: no ads, single-scheme, VOC compliance)."
  [state]
  (let [s (final-state state)]
    {"final_state" (assoc s
                          "livery" {"scheme" "OEM-default-white-with-route-band"
                                    "n6AdvertisingFreeAccept" true
                                    "vocGPerL" 88}
                          "phase" "livery_applied"
                          "completionPct" 90)
     "next_node" "attestation"}))

(defn transition-to-attestation-emitted
  "Emit final assembly attestation with robot signatures (G4: ≥2 robots)."
  [state]
  (let [s (final-state state)
        robot-sigs [{"robotDid" "did:web:etzhayyim.com:otete-heavy-unit-1"
                     "role" "marriage_lead"
                     "timestamp" "2026-05-26T16:00:00Z"
                     "signature" "..."}
                    {"robotDid" "did:web:etzhayyim.com:mimi-precision-unit-1"
                     "role" "alignment"
                     "timestamp" "2026-05-26T16:00:05Z"
                     "signature" "..."}]
        s-updated (assoc s
                         "robotSignatures" robot-sigs
                         "phase" "attestation_emitted"
                         "completionPct" 100)]
    {"final_state" s-updated
     "final_assembly_attestation" {"$type" "com.etzhayyim.yamabiko.finalAssemblyAttestation"
                                   "trainsetId" (get s "trainsetId")
                                   "inputs" (get s "inputs")
                                   "marriage" (get s "marriage")
                                   "livery" (get s "livery")
                                   "attestingRobots" robot-sigs
                                   "recordedAt" "2026-05-26T16:00:10Z"}
     "next_node" "end"}))
