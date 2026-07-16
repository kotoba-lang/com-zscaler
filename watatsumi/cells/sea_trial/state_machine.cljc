(ns watatsumi.cells.sea-trial.state-machine
  "Sea-trial state machine — ADR-2605252200 L5c. 1:1 cljc port of
  `cells/sea_trial/state_machine.py`. Dock trial → harbor dive → deep-water class
  trial (IMCA D-001 equivalent); surveyor SBT-gated (G11). Deterministic constant
  transitions; string keys mirror the Python dataclass __dict__ so the emitted
  seaTrialRecord is byte-identical.")

(def phases
  {:init "init" :dock-trial "dock_trial" :harbor-dive "harbor_dive"
   :deep-water-trial "deep_water_trial" :record-emitted "record_emitted"})

(defn init
  "Fresh sea_trial_state. Port of cell.py `_init`."
  [state]
  {"sea_trial_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "completionPct" 0}})

(defn transition-to-dock-trial
  "Dock trial: surface systems, hatch seal, ballast static."
  [state]
  (let [st (-> (get state "sea_trial_state" {})
               (assoc "dockTrialResults"
                      {"hatchSealTest" "PASS"
                       "ballastStaticTest" "PASS"
                       "shorePowerOff_BatteryUp" "PASS"
                       "co2ScrubberDryRun" "PASS"
                       "passiveSonarSelfNoise" {"dbRe1uPa" 78 "spec" 85 "accept" true}
                       "videoCid" "bafkreidocktrial..."}
                      "phase" (:dock-trial phases) "completionPct" 25))]
    {"sea_trial_state" st "next_node" "harbor"}))

(defn transition-to-harbor-dive
  "Harbor dive: 0–30 m repeated dives, dynamic ballast, comms check."
  [state]
  (let [st (-> (get state "sea_trial_state" {})
               (assoc "harborDiveResults"
                      {"diveDepthsM" [10 20 30 30 20 10]
                       "ballastDynamicTest" "PASS"
                       "acousticModemRangeTest" {"rangeMeters" 5200 "spec" 5000 "accept" true}
                       "rfSurfaceFallbackTest" "PASS"
                       "emergencyAscent" "PASS"
                       "videoCid" "bafkreiharbordive..."}
                      "phase" (:harbor-dive phases) "completionPct" 55))]
    {"sea_trial_state" st "next_node" "deep_water"}))

(defn transition-to-deep-water-trial
  "Deep-water trial: incremental depth to design, all systems."
  [state]
  (let [st (-> (get state "sea_trial_state" {})
               (assoc "deepWaterTrialResults"
                      {"incrementalDepthsM" [500 1500 3000 5000 6500]
                       "atDesignDepth" {"lifeSupportContinuousHours" 12
                                        "hullStrainGaugePeakMicrostrain" 720
                                        "hullStrainGaugeLimit" 1000
                                        "accept" true}
                       "g8ActiveSonarCheck" {"maxDbRe1uPaAt1m" 175 "limit" 180 "accept" true}
                       "videoCid" "bafkreideepwater..."}
                      "surveyorAttestationDid" "did:web:etzhayyim.com:surveyor:abs-001"
                      "phase" (:deep-water-trial phases) "completionPct" 90))]
    {"sea_trial_state" st "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [st (-> (get state "sea_trial_state" {})
               (assoc "overallAccept" true
                      "phase" (:record-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.watatsumi.seaTrialRecord"
                "craftId" (get st "craftId")
                "dockTrialResults" (get st "dockTrialResults")
                "harborDiveResults" (get st "harborDiveResults")
                "deepWaterTrialResults" (get st "deepWaterTrialResults")
                "surveyorAttestationDid" (get st "surveyorAttestationDid")
                "overallAccept" (get st "overallAccept")
                "protocol" "IMCA D-001 equivalent"
                "recordedAt" "2026-05-27T10:00:00Z"}]
    {"sea_trial_state" st "sea_trial_record" record "next_node" "end"}))

(defn run-chain
  "Run the full L5c chain init→dock→harbor→deep_water→record. Merges input+init so
  top-level keys thread through (langgraph state-merge parity)."
  [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-dock-trial transition-to-harbor-dive
           transition-to-deep-water-trial transition-to-record-emitted]))
