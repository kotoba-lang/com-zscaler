(ns wadachi.cells.safety-monitoring.state-machine
  "Safety-monitoring state machine — ADR-2605242000. 1:1 cljc port of
  `cells/safety_monitoring/state_machine.py`. Sensor check → hazard assess →
  safety protocol + e-stops → ≥2-robot witness. String keys mirror the Python
  dataclass __dict__.")

(def phases
  {:init "init" :sensors-checked "sensors_checked" :hazards-assessed "hazards_assessed"
   :safety-protocol-set "safety_protocol_set" :safety-verified "safety_verified"})

(defn init [state]
  {"safety_state" {"phase" (:init phases)
                   "missionId" (get state "missionId" "MISSION-2026-0001")
                   "completionPct" 0}})

(defn transition-to-sensors-checked [state]
  (let [ss (-> (get state "safety_state" {})
               (assoc "sensorStatus" {"gps_status" "rtk_fixed" "lidar_operational" true
                                      "imu_calibrated" true "camera_working" true
                                      "battery_healthy" true "communication_rssi_dbm" -65}
                      "phase" (:sensors-checked phases) "completionPct" 20))]
    {"safety_state" ss "next_node" "assess_hazards"}))

(defn transition-to-hazards-assessed [state]
  (let [ss (-> (get state "safety_state" {})
               (assoc "hazardAssessment" {"weather_condition" "clear" "visibility_m" 100
                                          "road_surface_condition" "dry" "pedestrian_density" "low"
                                          "active_construction_nearby" true "speed_limit_kmh" 10
                                          "overall_risk_level" "medium"}
                      "phase" (:hazards-assessed phases) "completionPct" 45))]
    {"safety_state" ss "next_node" "set_protocol"}))

(defn transition-to-safety-protocol-set [state]
  (let [ss (-> (get state "safety_state" {})
               (assoc "safetyProtocol" {"max_speed_ms" 1.5 "minimum_stopping_distance_m" 3.0
                                        "obstacle_detection_radius_m" 8 "emergency_stop_enabled" true
                                        "geofence_enforcement" true "communication_heartbeat_hz" 10
                                        "manual_override_enabled" false}
                      "emergencyStops" [{"type" "lidar_proximity" "threshold_m" 0.5 "action" "immediate_halt"}
                                        {"type" "rtk_signal_loss" "threshold_s" 2 "action" "return_to_origin"}
                                        {"type" "battery_critical" "threshold_pct" 10 "action" "return_to_origin"}]
                      "phase" (:safety-protocol-set phases) "completionPct" 70))]
    {"safety_state" ss "next_node" "witness"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:wadachi-unit-1" "role" "safety_monitor"
    "timestamp" "2026-05-26T10:19:15Z" "signature" "kK1lL2mM3nN4oO5p..."}
   {"robotDid" "did:web:etzhayyim.com:sora-unit-1" "role" "safety_auditor"
    "timestamp" "2026-05-26T10:19:20Z" "signature" "qQ6rR7sS8tT9uU0v..."}])

(defn transition-to-safety-verified [state]
  (let [ss (-> (get state "safety_state" {})
               (assoc "phase" (:safety-verified phases) "robotSignatures" robot-sigs "completionPct" 100))]
    {"safety_state" ss
     "safety_record" {"missionId" (get ss "missionId")
                      "sensorStatus" (get ss "sensorStatus")
                      "hazardAssessment" (get ss "hazardAssessment")
                      "safetyProtocol" (get ss "safetyProtocol")
                      "emergencyStops" (get ss "emergencyStops")
                      "attestingRobots" robot-sigs}
     "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-sensors-checked transition-to-hazards-assessed
           transition-to-safety-protocol-set transition-to-safety-verified]))
