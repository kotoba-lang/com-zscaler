(ns yamabiko.cells.emissions-acoustic-audit.state-machine
  "1:1 port of cells/emissions_acoustic_audit/state_machine.py — ADR-2605252600 G8 cross-cutting.

  ISO 3095 wayside noise + 日本騒音規制法 + IEC 62236 EMC.")

(defn- acoustic-state
  "AcousticState defaults merged with any existing \"acoustic_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "acoustic_state" {})
        trainset-id (or (get existing "trainsetId")
                        (get state "trainsetId")
                        "YAMABIKO-TRAINSET-0001")]
    (merge {"phase" "init"
            "trainsetId" trainset-id
            "completionPct" 0
            "waysideNoise" nil
            "vibration" nil
            "emcResult" nil
            "overallAccept" nil}
           existing)))

(defn transition-to-wayside-noise-measured
  "Measure wayside noise per ISO 3095 (max 88 dB @ 25m, 300 km/h; standstill ≤70 dB)."
  [state]
  (let [s (acoustic-state state)]
    {"acoustic_state" (assoc s
                             "waysideNoise" {"standard" "ISO 3095"
                                             "dbAAt25mAtSpeed_300kmh" 88
                                             "limit_dbA" 95
                                             "dbAStandstill" 68
                                             "limitStandstill_dbA" 70
                                             "accept" true}
                             "phase" "wayside_noise_measured"
                             "completionPct" 35)
     "next_node" "vibration"}))

(defn transition-to-vibration-measured
  "Measure vibration per 日本騒音規制法 (trackside ≤60 dB)."
  [state]
  (let [s (acoustic-state state)]
    {"acoustic_state" (assoc s
                             "vibration" {"standard" "日本 騒音規制法"
                                          "dbVibrationAtTrackside" 58
                                          "limit_dbVibration" 60
                                          "accept" true}
                             "phase" "vibration_measured"
                             "completionPct" 60)
     "next_node" "emc"}))

(defn transition-to-emc-verified
  "Verify EMC per IEC 62236 (emission + immunity)."
  [state]
  (let [s (acoustic-state state)]
    {"acoustic_state" (assoc s
                             "emcResult" {"standard" "IEC 62236"
                                          "emissionPass" true
                                          "immunityPass" true
                                          "accept" true}
                             "phase" "emc_verified"
                             "completionPct" 90)
     "next_node" "record"}))

(defn transition-to-record-emitted
  "Emit final acoustic emissions audit record."
  [state]
  (let [s (acoustic-state state)
        ;; Python's: (s.waysideNoise or {}).get("accept") is True
        wayside-ok (identical? true (get (or (get s "waysideNoise") {}) "accept"))
        vibration-ok (identical? true (get (or (get s "vibration") {}) "accept"))
        emc-ok (identical? true (get (or (get s "emcResult") {}) "accept"))
        overall (and wayside-ok vibration-ok emc-ok)]
    {"acoustic_state" (assoc s
                             "overallAccept" overall
                             "phase" "record_emitted"
                             "completionPct" 100)
     "acoustic_emissions_audit_record" {"$type" "com.etzhayyim.yamabiko.acousticEmissionsAuditRecord"
                                        "trainsetId" (get s "trainsetId")
                                        "waysideNoise" (get s "waysideNoise")
                                        "vibration" (get s "vibration")
                                        "emcResult" (get s "emcResult")
                                        "overallAccept" overall
                                        "regulatoryBasis" ["ISO 3095" "日本 騒音規制法" "IEC 62236"]
                                        "recordedAt" "2026-05-27T15:00:00Z"}
     "next_node" "end"}))
