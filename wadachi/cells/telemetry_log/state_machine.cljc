(ns wadachi.cells.telemetry-log.state-machine
  "Telemetry-log state machine — ADR-2605242000. 1:1 cljc port of
  `cells/telemetry_log/state_machine.py`. Collect → process (telemetryData
  dict-merge) → verify → log (IPFS CID) + ≥2-robot witness. String keys mirror the
  Python dataclass __dict__.")

(def phases
  {:init "init" :data-collected "data_collected" :data-processed "data_processed"
   :records-verified "records_verified" :logged "logged"})

(defn init [state]
  {"telemetry_state" {"phase" (:init phases)
                      "missionId" (get state "missionId" "MISSION-2026-0001")
                      "completionPct" 0}})

(defn transition-to-data-collected [state]
  (let [ts (-> (get state "telemetry_state" {})
               (assoc "startTime" "2026-05-26T10:15:30Z" "endTime" "2026-05-26T10:18:45Z"
                      "totalDuration" 195.0
                      "telemetryData" {"mission_start" "2026-05-26T10:15:30Z" "mission_end" "2026-05-26T10:18:45Z"
                                       "duration_seconds" 195 "total_distance_m" 350 "average_speed_ms" 1.18
                                       "max_speed_ms" 1.50 "battery_consumed_pct" 8 "battery_start_pct" 95
                                       "battery_end_pct" 87 "data_points_collected" 1950 "gps_fixes" 195 "lidar_scans" 195}
                      "phase" (:data-collected phases) "completionPct" 20))]
    {"telemetry_state" ts "next_node" "process_data"}))

(defn transition-to-data-processed [state]
  (let [ts0 (get state "telemetry_state" {})
        mock-processed {"valid_gps_fixes" 193 "gps_fix_rate_pct" 98.9 "lidar_scan_quality" "excellent"
                        "data_corruption_detected" false "outlier_removal_applied" true "outliers_removed" 5
                        "smoothing_filter" "kalman" "processing_duration_s" 2.3}
        ts (assoc ts0 "telemetryData" (merge (get ts0 "telemetryData" {}) mock-processed)
                  "phase" (:data-processed phases) "completionPct" 45)]
    {"telemetry_state" ts "next_node" "verify_records"}))

(defn transition-to-records-verified [state]
  (let [ts (-> (get state "telemetry_state" {})
               (assoc "missionSummary" {"mission_status" "success" "destination_reached" true
                                        "payload_delivered" true "safety_incidents" 0
                                        "autonomous_decisions_made" 2 "human_interventions" 0
                                        "total_anomalies_detected" 0 "verification_passed" true}
                      "phase" (:records-verified phases) "completionPct" 75))]
    {"telemetry_state" ts "next_node" "log_records"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:wadachi-unit-1" "role" "mission_executor"
    "timestamp" "2026-05-26T10:19:45Z" "signature" "wW1xX2yY3zZ4aA5b..."}
   {"robotDid" "did:web:etzhayyim.com:sora-unit-1" "role" "telemetry_auditor"
    "timestamp" "2026-05-26T10:19:50Z" "signature" "cC6dD7eE8fF9gG0h..."}])

(defn transition-to-logged [state]
  (let [ts (-> (get state "telemetry_state" {})
               (assoc "phase" (:logged phases)
                      "ipfsCid" "QmWadachiMissionTelemetry20260526101945"
                      "robotSignatures" robot-sigs "completionPct" 100))]
    {"telemetry_state" ts
     "mission_complete_record" {"missionId" (get ts "missionId")
                                "startTime" (get ts "startTime")
                                "endTime" (get ts "endTime")
                                "totalDuration" (get ts "totalDuration")
                                "telemetryData" (get ts "telemetryData")
                                "missionSummary" (get ts "missionSummary")
                                "ipfsCid" (get ts "ipfsCid")
                                "attestingRobots" robot-sigs}
     "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-data-collected transition-to-data-processed
           transition-to-records-verified transition-to-logged]))
