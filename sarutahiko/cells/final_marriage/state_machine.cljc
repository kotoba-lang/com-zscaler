(ns sarutahiko.cells.final-marriage.state-machine
  "Final-marriage state machine — ADR-2605252500 L4. 1:1 cljc port of
  `cells/final_marriage/state_machine.py`. Chassis lower + cab drop + powertrain
  mount + harness connect; ≥2-robot witness on critical fastener torque (G4).
  criticalTorques accumulate across transitions. String keys mirror the Python
  __dict__.")

(def phases
  {:init "init" :inputs-verified "inputs_verified" :chassis-lowered "chassis_lowered"
   :cab-dropped "cab_dropped" :powertrain-mounted "powertrain_mounted"
   :harness-connected "harness_connected" :attestation-emitted "attestation_emitted"})

(defn init [state]
  {"marriage_state" {"phase" (:init phases)
                     "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                     "completionPct" 0}})

(defn transition-to-inputs-verified [state]
  (let [s (-> (get state "marriage_state" {})
              (assoc "inputs" {"frameAttestationCid" "bafkreiframeatt..."
                               "powertrainAttestationCid" "bafkreiptatt..."
                               "cabBodyAttestationCid" "bafkreicabatt..."}
                     "phase" (:inputs-verified phases) "completionPct" 15))]
    {"marriage_state" s "next_node" "lower"}))

(defn transition-to-chassis-lowered [state]
  (let [s (-> (get state "marriage_state" {})
              (assoc "phase" (:chassis-lowered phases) "completionPct" 35))]
    {"marriage_state" s "next_node" "cab"}))

(defn transition-to-cab-dropped [state]
  (let [s (-> (get state "marriage_state" {})
              (assoc "criticalTorques"
                     [{"fastener" "cab_mount_1" "torqueNm" 320 "specNm" 320 "tolerancePct" 5}
                      {"fastener" "cab_mount_2" "torqueNm" 315 "specNm" 320 "tolerancePct" 5}
                      {"fastener" "cab_mount_3" "torqueNm" 322 "specNm" 320 "tolerancePct" 5}
                      {"fastener" "cab_mount_4" "torqueNm" 318 "specNm" 320 "tolerancePct" 5}]
                     "phase" (:cab-dropped phases) "completionPct" 55))]
    {"marriage_state" s "next_node" "powertrain"}))

(defn transition-to-powertrain-mounted [state]
  (let [s0 (get state "marriage_state" {})
        extra [{"fastener" "engine_mount_left" "torqueNm" 450 "specNm" 450 "tolerancePct" 5}
               {"fastener" "engine_mount_right" "torqueNm" 448 "specNm" 450 "tolerancePct" 5}
               {"fastener" "transmission_mount" "torqueNm" 280 "specNm" 280 "tolerancePct" 5}]
        s (assoc s0 "criticalTorques" (into (vec (get s0 "criticalTorques" [])) extra)
                 "phase" (:powertrain-mounted phases) "completionPct" 75)]
    {"marriage_state" s "next_node" "harness"}))

(defn transition-to-harness-connected [state]
  (let [s (-> (get state "marriage_state" {})
              (assoc "phase" (:harness-connected phases) "completionPct" 90))]
    {"marriage_state" s "next_node" "attestation"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:otete-heavy-unit-1" "role" "marriage_lead"
    "timestamp" "2026-05-26T13:00:00Z" "signature" "..."}
   {"robotDid" "did:web:etzhayyim.com:mimi-precision-unit-1" "role" "alignment_witness"
    "timestamp" "2026-05-26T13:00:05Z" "signature" "..."}])

(defn transition-to-attestation-emitted [state]
  (let [s (-> (get state "marriage_state" {})
              (assoc "robotSignatures" robot-sigs "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.sarutahiko.marriageAttestation"
                "chassisId" (get s "chassisId")
                "inputs" (get s "inputs")
                "criticalTorques" (get s "criticalTorques")
                "attestingRobots" robot-sigs
                "recordedAt" "2026-05-26T13:00:10Z"}]
    {"marriage_state" s "marriage_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-inputs-verified transition-to-chassis-lowered
           transition-to-cab-dropped transition-to-powertrain-mounted
           transition-to-harness-connected transition-to-attestation-emitted]))
