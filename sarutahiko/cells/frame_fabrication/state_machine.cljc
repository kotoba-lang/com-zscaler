(ns sarutahiko.cells.frame-fabrication.state-machine
  "Frame-fabrication state machine — ADR-2605252500 L1. 1:1 cljc port of
  `cells/frame_fabrication/state_machine.py`. HSLA-590/780 ladder-frame MIG/MAG
  welding, straightness <1 mm/m, ≥2-robot witness (G4). String keys mirror the
  Python dataclass __dict__.")

(def phases
  {:init "init" :steel-lot-verified "steel_lot_verified" :rails-positioned "rails_positioned"
   :cross-members-welded "cross_members_welded" :straightness-qa-passed "straightness_qa_passed"
   :attestation-emitted "attestation_emitted"})

(defn init [state]
  {"frame_state" {"phase" (:init phases)
                  "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                  "completionPct" 0}})

(defn transition-to-steel-lot-verified [state]
  (let [s (-> (get state "frame_state" {})
              (assoc "steelLot" {"grade" "HSLA-780" "lotId" "HSLA780-2026-05-LOT-0042"
                                 "certCid" "bafkreihsla..." "yieldStrengthMpa" 780 "tensileStrengthMpa" 850}
                     "phase" (:steel-lot-verified phases) "completionPct" 15))]
    {"frame_state" s "next_node" "position"}))

(defn transition-to-rails-positioned [state]
  (let [s (-> (get state "frame_state" {})
              (assoc "railPositions" [{"rail" "left_long" "lengthMm" 9500 "offsetMm" 0}
                                      {"rail" "right_long" "lengthMm" 9500 "offsetMm" 1100}]
                     "phase" (:rails-positioned phases) "completionPct" 35))]
    {"frame_state" s "next_node" "weld"}))

(defn transition-to-cross-members-welded [state]
  (let [s (-> (get state "frame_state" {})
              (assoc "weldPasses" [{"crossMemberIdx" 0 "process" "MIG-multi-pass" "passes" 3 "ipfsCid" "bafkreiweld0..."}
                                   {"crossMemberIdx" 1 "process" "MIG-multi-pass" "passes" 3 "ipfsCid" "bafkreiweld1..."}
                                   {"crossMemberIdx" 2 "process" "MAG-multi-pass" "passes" 3 "ipfsCid" "bafkreiweld2..."}]
                     "phase" (:cross-members-welded phases) "completionPct" 70))]
    {"frame_state" s "next_node" "qa"}))

(defn transition-to-straightness-qa-passed [state]
  (let [s (-> (get state "frame_state" {})
              (assoc "straightnessMmPerM" 0.6 "phase" (:straightness-qa-passed phases) "completionPct" 90))]
    {"frame_state" s "next_node" "attestation"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:kasane-unit-1" "role" "weld_lead"
    "timestamp" "2026-05-26T08:00:00Z" "signature" "..."}
   {"robotDid" "did:web:etzhayyim.com:mimi-precision-unit-1" "role" "metrology"
    "timestamp" "2026-05-26T08:00:05Z" "signature" "..."}])

(defn transition-to-attestation-emitted [state]
  (let [s (-> (get state "frame_state" {})
              (assoc "robotSignatures" robot-sigs "phase" (:attestation-emitted phases) "completionPct" 100))
        straightness (or (get s "straightnessMmPerM") 0)
        record {"$type" "com.etzhayyim.sarutahiko.frameAttestation"
                "chassisId" (get s "chassisId")
                "steelLot" (get s "steelLot")
                "railPositions" (get s "railPositions")
                "weldPasses" (get s "weldPasses")
                "straightnessMmPerM" (get s "straightnessMmPerM")
                "specStraightnessLimitMmPerM" 1.0
                "accept" (< straightness 1.0)
                "attestingRobots" robot-sigs
                "recordedAt" "2026-05-26T08:00:10Z"}]
    {"frame_state" s "frame_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-steel-lot-verified transition-to-rails-positioned
           transition-to-cross-members-welded transition-to-straightness-qa-passed
           transition-to-attestation-emitted]))
