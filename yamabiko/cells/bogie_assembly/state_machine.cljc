(ns yamabiko.cells.bogie-assembly.state-machine
  "1:1 port of cells/bogie_assembly/state_machine.py — ADR-2605252600 L2.

  Cast steel bogie frame (igata Wave 2 R3+ source) + air spring + tread brake +
  axle + wheel set + traction motor (PMSM / IM). ≥2 robot witness on assembly (G4).
  Pure, deterministic transitions.")

(defn- bogie-state
  "BogieState defaults merged with any existing \"bogie_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "bogie_state" {})
        trainset-id (or (get existing "trainsetId")
                        (get state "trainsetId")
                        "YAMABIKO-TRAINSET-0001")
        bogie-index (or (get existing "bogieIndex")
                        (get state "bogieIndex")
                        0)]
    (merge {"phase" "init"
            "trainsetId" trainset-id
            "bogieIndex" bogie-index
            "completionPct" 0
            "frameLot" nil
            "wheelSetLot" nil
            "motorLot" nil
            "brakeSystem" nil
            "airSpring" nil
            "robotSignatures" nil}
           existing)))

(defn transition-to-frame-prepared
  "Prepare bogie frame lot (external cast steel R1, R3+ from igata Wave 2)."
  [state]
  (let [s (bogie-state state)]
    {"bogie_state" (assoc s
                          "frameLot" {"source" "external-cast-steel-R1"
                                      "note" "R3+ source from igata Wave 2"
                                      "lotId" "BOGIE-FRAME-0011"}
                          "phase" "frame_prepared"
                          "completionPct" 15)
     "next_node" "wheel"}))

(defn transition-to-wheel-set-mounted
  "Mount wheel set."
  [state]
  (let [s (bogie-state state)]
    {"bogie_state" (assoc s
                          "wheelSetLot" {"lotId" "WHEELSET-0011"
                                         "wheelDiameterMm" 860
                                         "axleLoadT" 17}
                          "phase" "wheel_set_mounted"
                          "completionPct" 35)
     "next_node" "motor"}))

(defn transition-to-motor-installed
  "Install traction motor (PMSM)."
  [state]
  (let [s (bogie-state state)]
    {"bogie_state" (assoc s
                          "motorLot" {"type" "PMSM"
                                      "powerKw" 305
                                      "ratedVoltageV" 1100
                                      "lotId" "TRACTION-MOTOR-0011"}
                          "phase" "motor_installed"
                          "completionPct" 60)
     "next_node" "brake"}))

(defn transition-to-brake-integrated
  "Integrate brake system (tread-disc hybrid with regenerative capability)."
  [state]
  (let [s (bogie-state state)]
    {"bogie_state" (assoc s
                          "brakeSystem" {"type" "tread-disc-hybrid"
                                         "regenerativeAllowed" true
                                         "emergencyDecelMsps" 1.3}
                          "phase" "brake_integrated"
                          "completionPct" 78)
     "next_node" "air"}))

(defn transition-to-air-spring-installed
  "Install air suspension spring system."
  [state]
  (let [s (bogie-state state)]
    {"bogie_state" (assoc s
                          "airSpring" {"primary" "coil"
                                       "secondary" "air-bellows"
                                       "levelingControl" true}
                          "phase" "air_spring_installed"
                          "completionPct" 90)
     "next_node" "attestation"}))

(defn transition-to-attestation-emitted
  "Emit final bogie attestation with robot signatures (G4: ≥2 robots)."
  [state]
  (let [s (bogie-state state)
        robot-sigs [{"robotDid" "did:web:etzhayyim.com:wadasa-unit-1"
                     "role" "bogie_lead"
                     "timestamp" "2026-05-26T10:00:00Z"
                     "signature" "..."}
                    {"robotDid" "did:web:etzhayyim.com:mimi-precision-unit-1"
                     "role" "alignment"
                     "timestamp" "2026-05-26T10:00:05Z"
                     "signature" "..."}]
        s-updated (assoc s
                         "robotSignatures" robot-sigs
                         "phase" "attestation_emitted"
                         "completionPct" 100)]
    {"bogie_state" s-updated
     "bogie_attestation" {"$type" "com.etzhayyim.yamabiko.bogieAttestation"
                          "trainsetId" (get s "trainsetId")
                          "bogieIndex" (get s "bogieIndex")
                          "frameLot" (get s "frameLot")
                          "wheelSetLot" (get s "wheelSetLot")
                          "motorLot" (get s "motorLot")
                          "brakeSystem" (get s "brakeSystem")
                          "airSpring" (get s "airSpring")
                          "attestingRobots" robot-sigs
                          "recordedAt" "2026-05-26T10:00:10Z"}
     "next_node" "end"}))
