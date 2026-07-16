(ns wadachi.cells.obstacle-avoidance.state-machine
  "Obstacle-avoidance state machine — ADR-2605242000. 1:1 cljc port of
  `cells/obstacle_avoidance/state_machine.py`. LIDAR scan → detect objects +
  collision risk → course correction → ≥2-robot witness. String keys mirror the
  Python dataclass __dict__.")

(def phases
  {:init "init" :lidar-scanning "lidar_scanning" :obstacles-detected "obstacles_detected"
   :course-correction "course_correction" :avoidance-complete "avoidance_complete"})

(defn init [state]
  {"obstacle_state" {"phase" (:init phases)
                     "missionId" (get state "missionId" "MISSION-2026-0001")
                     "completionPct" 0}})

(defn transition-to-lidar-scanning [state]
  (let [os (-> (get state "obstacle_state" {})
               (assoc "lidarScan" {"range_m" 50 "angular_resolution_deg" 0.1 "scan_points" 3600
                                   "min_distance_m" 0.5 "max_distance_m" 49.8}
                      "phase" (:lidar-scanning phases) "completionPct" 20))]
    {"obstacle_state" os "next_node" "detect_objects"}))

(defn transition-to-obstacles-detected [state]
  (let [os (-> (get state "obstacle_state" {})
               (assoc "detectedObjects"
                      [{"object_id" "OBS-001" "distance_m" 8.5 "angle_deg" 15 "size_m" 1.2 "type" "pedestrian_alert" "moving" true}
                       {"object_id" "OBS-002" "distance_m" 12.3 "angle_deg" -10 "size_m" 0.8 "type" "static_debris" "moving" false}
                       {"object_id" "OBS-003" "distance_m" 25.0 "angle_deg" 0 "size_m" 2.5 "type" "vehicle" "moving" true}]
                      "collisionRisk"
                      [{"object_id" "OBS-001" "collision_time_s" 7.0 "risk_level" "medium" "required_action" "divert_right"}
                       {"object_id" "OBS-002" "collision_time_s" 10.0 "risk_level" "low" "required_action" "monitor"}]
                      "phase" (:obstacles-detected phases) "completionPct" 45))]
    {"obstacle_state" os "next_node" "apply_correction"}))

(defn transition-to-course-correction [state]
  (let [os (-> (get state "obstacle_state" {})
               (assoc "newTrajectory" {"original_bearing_deg" 45 "new_bearing_deg" 52
                                       "steering_adjustment_deg" 7 "speed_reduction_pct" 15 "new_speed_ms" 1.02}
                      "correctionApplied" true "phase" (:course-correction phases) "completionPct" 70))]
    {"obstacle_state" os "next_node" "witness"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:wadachi-unit-1" "role" "obstacle_handler"
    "timestamp" "2026-05-26T10:18:30Z" "signature" "yY1zZ2aA3bB4cC5d..."}
   {"robotDid" "did:web:etzhayyim.com:sora-unit-1" "role" "lidar_monitor"
    "timestamp" "2026-05-26T10:18:35Z" "signature" "eE6fF7gG8hH9iI0j..."}])

(defn transition-to-avoidance-complete [state]
  (let [os (-> (get state "obstacle_state" {})
               (assoc "phase" (:avoidance-complete phases) "robotSignatures" robot-sigs "completionPct" 100))]
    {"obstacle_state" os
     "avoidance_record" {"missionId" (get os "missionId")
                         "detectedObjects" (get os "detectedObjects")
                         "collisionRisks" (get os "collisionRisk")
                         "correctionApplied" (get os "correctionApplied")
                         "newTrajectory" (get os "newTrajectory")
                         "attestingRobots" robot-sigs}
     "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-lidar-scanning transition-to-obstacles-detected
           transition-to-course-correction transition-to-avoidance-complete]))
