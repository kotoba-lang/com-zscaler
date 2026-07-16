(ns watatsumi.cells.hull-ring-fabrication.state-machine
  "Hull-ring-fabrication state machine — ADR-2605252200 L1. 1:1 cljc port of
  `cells/hull_ring_fabrication/state_machine.py`. Material verify (HSLA-80, no
  HY-100 w/o Council) → plate roll → ring-frame weld → roundness QA (<0.5% Ø) →
  ≥2-robot attestation. String keys mirror the Python dataclass __dict__.")

(def phases
  {:init "init" :material-lot-verified "material_lot_verified" :plate-rolled "plate_rolled"
   :ring-frame-welded "ring_frame_welded" :roundness-qa-passed "roundness_qa_passed"
   :attestation-emitted "attestation_emitted"})

(defn init [state]
  {"hull_ring_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "ringIndex" (get state "ringIndex" 0)
    "completionPct" 0}})

(defn transition-to-material-verified [state]
  (let [hs (-> (get state "hull_ring_state" {})
               (assoc "phase" (:material-lot-verified phases)
                      "materialLot" {"alloy" "HSLA-80"
                                     "grade" "ASTM A710 Class 3"
                                     "lotId" "HSLA80-2026-05-LOT-0042"
                                     "certCid" "bafkreigh2akiscaildc..."
                                     "yieldStrengthMpa" 552
                                     "tensileStrengthMpa" 690
                                     "councilAttestation" nil}
                      "completionPct" 15))]
    {"hull_ring_state" hs "next_node" "rolling"}))

(defn transition-to-plate-rolled [state]
  (let [hs (-> (get state "hull_ring_state" {})
               (assoc "phase" (:plate-rolled phases)
                      "rollingTelemetry" {"rollerPasses" 7 "finalDiameterMm" 6500
                                          "finalThicknessMm" 42 "preheat_C" 150}
                      "completionPct" 40))]
    {"hull_ring_state" hs "next_node" "ring_weld"}))

(defn transition-to-ring-frame-welded [state]
  (let [hs (-> (get state "hull_ring_state" {})
               (assoc "phase" (:ring-frame-welded phases)
                      "weldPasses" [{"pass" 1 "process" "GTAW-root" "amp" 180 "ipfsCid" "bafkreipass1..."}
                                    {"pass" 2 "process" "SAW-fill" "amp" 450 "ipfsCid" "bafkreipass2..."}
                                    {"pass" 3 "process" "SAW-fill" "amp" 450 "ipfsCid" "bafkreipass3..."}
                                    {"pass" 4 "process" "GTAW-cap" "amp" 220 "ipfsCid" "bafkreipass4..."}]
                      "completionPct" 70))]
    {"hull_ring_state" hs "next_node" "roundness_qa"}))

(defn transition-to-roundness-qa [state]
  (let [ppm (long (Math/round (/ (* 6.0 1000000) 6500)))   ;; round(6/6500*1e6) = 923 ppm
        hs (-> (get state "hull_ring_state" {})
               (assoc "phase" (:roundness-qa-passed phases)
                      "roundnessMeasurement"
                      {"measurements_mm" [6500 6498 6502 6499 6501 6500 6497 6503]
                       "maxOutOfRoundMm" 6 "diameterMm" 6500
                       "maxOutOfRoundPpm" ppm "limitPpm" 5000 "accept" true}
                      "completionPct" 90))]
    {"hull_ring_state" hs "next_node" "attestation"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:mimi-marine-unit-1" "role" "metrology"
    "timestamp" "2026-05-26T11:00:00Z" "signature" "aA1bB2cC3dD4eE5f..."}
   {"robotDid" "did:web:etzhayyim.com:otete-marine-unit-1" "role" "weld_witness"
    "timestamp" "2026-05-26T11:00:05Z" "signature" "gG6hH7iI8jJ9kK0l..."}])

(defn transition-to-attestation-emitted [state]
  (let [hs (-> (get state "hull_ring_state" {})
               (assoc "robotSignatures" robot-sigs
                      "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.watatsumi.pressureHullAttestation"
                "craftId" (get hs "craftId")
                "ringIndex" (get hs "ringIndex")
                "materialLot" (get hs "materialLot")
                "rolling" (get hs "rollingTelemetry")
                "weldPasses" (get hs "weldPasses")
                "roundness" (get hs "roundnessMeasurement")
                "attestingRobots" robot-sigs
                "recordedAt" "2026-05-26T11:00:10Z"}]
    {"hull_ring_state" hs "pressure_hull_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-material-verified transition-to-plate-rolled
           transition-to-ring-frame-welded transition-to-roundness-qa
           transition-to-attestation-emitted]))
