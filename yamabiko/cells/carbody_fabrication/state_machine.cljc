(ns yamabiko.cells.carbody-fabrication.state-machine
  "1:1 port of cells/carbody_fabrication/state_machine.py — ADR-2605252600 L1.

  FSW (Friction Stir Welding) Al 6N01 / A6005C double-skin extrusion carbody.
  ≥2 robot witness (G4). Hitachi A-Train class methodology.")

(defn- carbody-state
  "CarbodyState defaults merged with any existing \"carbody_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "carbody_state" {})
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
            "extrusionLot" nil
            "fswSeams" nil
            "spotWelds" nil
            "dimensionalQa" nil
            "robotSignatures" nil}
           existing)))

(defn transition-to-extrusion-verified
  "Verify extrusion lot (Al-6N01 double-skin)."
  [state]
  (let [s (carbody-state state)]
    {"carbody_state" (assoc s
                            "extrusionLot" {"alloy" "Al-6N01"
                                            "lotId" "AL6N01-2026-05-LOT-0042"
                                            "doubleSkin" true
                                            "thicknessMm" 2.5
                                            "certCid" "bafkreialextrude..."}
                            "phase" "extrusion_verified"
                            "completionPct" 15)
     "next_node" "fsw"}))

(defn transition-to-fsw-seams-complete
  "Complete FSW seams on all four sides."
  [state]
  (let [s (carbody-state state)]
    {"carbody_state" (assoc s
                            "fswSeams" [{"seam" "side-floor"
                                         "lengthM" 24.5
                                         "tool_rpm" 800
                                         "feed_mm_per_min" 600
                                         "videoCid" "bafkreifsw1..."}
                                        {"seam" "side-roof"
                                         "lengthM" 24.5
                                         "tool_rpm" 800
                                         "feed_mm_per_min" 600
                                         "videoCid" "bafkreifsw2..."}
                                        {"seam" "end-front"
                                         "lengthM" 3.2
                                         "tool_rpm" 750
                                         "feed_mm_per_min" 550
                                         "videoCid" "bafkreifsw3..."}
                                        {"seam" "end-rear"
                                         "lengthM" 3.2
                                         "tool_rpm" 750
                                         "feed_mm_per_min" 550
                                         "videoCid" "bafkreifsw4..."}]
                            "phase" "fsw_seams_complete"
                            "completionPct" 50)
     "next_node" "spot"}))

(defn transition-to-spot-welds-complete
  "Complete spot welds."
  [state]
  (let [s (carbody-state state)]
    {"carbody_state" (assoc s
                            "spotWelds" {"totalSpots" 1800
                                         "robotPasses" 3
                                         "videoCid" "bafkreispot..."}
                            "phase" "spot_welds_complete"
                            "completionPct" 70)
     "next_node" "qa"}))

(defn transition-to-dimensional-qa-passed
  "Pass dimensional QA (length, width, height within spec)."
  [state]
  (let [s (carbody-state state)]
    {"carbody_state" (assoc s
                            "dimensionalQa" {"lengthMm" 25000
                                             "lengthSpecMm" 25000
                                             "lengthTolMm" 5
                                             "widthMm" 3380
                                             "widthSpecMm" 3380
                                             "widthTolMm" 3
                                             "heightMm" 3650
                                             "heightSpecMm" 3650
                                             "heightTolMm" 3
                                             "accept" true}
                            "phase" "dimensional_qa_passed"
                            "completionPct" 90)
     "next_node" "attestation"}))

(defn transition-to-attestation-emitted
  "Emit final carbody attestation with robot signatures (G4: ≥2 robots)."
  [state]
  (let [s (carbody-state state)
        robot-sigs [{"robotDid" "did:web:etzhayyim.com:tsugite-unit-1"
                     "role" "fsw_lead"
                     "timestamp" "2026-05-26T08:00:00Z"
                     "signature" "..."}
                    {"robotDid" "did:web:etzhayyim.com:mimi-precision-unit-1"
                     "role" "metrology"
                     "timestamp" "2026-05-26T08:00:05Z"
                     "signature" "..."}]
        s-updated (assoc s
                         "robotSignatures" robot-sigs
                         "phase" "attestation_emitted"
                         "completionPct" 100)]
    {"carbody_state" s-updated
     "carbody_attestation" {"$type" "com.etzhayyim.yamabiko.carbodyAttestation"
                            "trainsetId" (get s "trainsetId")
                            "carIndex" (get s "carIndex")
                            "extrusionLot" (get s "extrusionLot")
                            "fswSeams" (get s "fswSeams")
                            "spotWelds" (get s "spotWelds")
                            "dimensionalQa" (get s "dimensionalQa")
                            "attestingRobots" robot-sigs
                            "recordedAt" "2026-05-26T08:00:10Z"}
     "next_node" "end"}))
