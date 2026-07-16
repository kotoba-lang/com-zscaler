(ns wadachi.cells.motion-control.state-machine
  "Motion-control state machine — ADR-2605242000. 1:1 cljc port of
  `cells/motion_control/state_machine.py`. Motors engage → path follow → speed
  regulate (motorMetrics dict-merge) → ≥2-robot witness. String keys mirror the
  Python dataclass __dict__.")

(def phases
  {:init "init" :motors-engaged "motors_engaged" :path-following "path_following"
   :speed-regulated "speed_regulated" :motion-complete "motion_complete"})

(defn init [state]
  {"motion_state" {"phase" (:init phases)
                   "missionId" (get state "missionId" "MISSION-2026-0001")
                   "completionPct" 0}})

(defn transition-to-motors-engaged [state]
  (let [ms (-> (get state "motion_state" {})
               (assoc "motorMetrics" {"left_motor_rpm" 450 "right_motor_rpm" 450 "steering_angle_deg" 0
                                      "torque_nm" [12.5 12.5] "temperature_c" 32}
                      "phase" (:motors-engaged phases) "completionPct" 20))]
    {"motion_state" ms "next_node" "follow_path"}))

(defn transition-to-path-following [state]
  (let [ms (-> (get state "motion_state" {})
               (assoc "gpsTrajectory" [{"latitude" 35.6865 "longitude" 139.6900 "timestamp" "2026-05-26T10:16:00Z"}
                                       {"latitude" 35.6870 "longitude" 139.6902 "timestamp" "2026-05-26T10:16:15Z"}
                                       {"latitude" 35.6875 "longitude" 139.6905 "timestamp" "2026-05-26T10:16:30Z"}]
                      "distanceTraveled" 87.5 "phase" (:path-following phases) "completionPct" 45))]
    {"motion_state" ms "next_node" "regulate_speed"}))

(defn transition-to-speed-regulated [state]
  (let [ms0 (get state "motion_state" {})
        mock-speed {"target_speed_ms" 1.2 "actual_speed_ms" 1.19 "speed_error_pct" 0.8
                    "acceleration_ms2" 0.15 "battery_voltage_v" 24.0 "battery_current_a" 8.5}
        ms (assoc ms0 "targetSpeed" 1.2 "currentSpeed" 1.19
                  "motorMetrics" (merge (get ms0 "motorMetrics" {}) mock-speed)
                  "phase" (:speed-regulated phases) "completionPct" 75)]
    {"motion_state" ms "next_node" "witness"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:wadachi-unit-1" "role" "motion_executor"
    "timestamp" "2026-05-26T10:17:45Z" "signature" "mM1nN2oO3pP4qQ5r..."}
   {"robotDid" "did:web:etzhayyim.com:sora-unit-1" "role" "motion_monitor"
    "timestamp" "2026-05-26T10:17:50Z" "signature" "sS6tT7uU8vV9wW0x..."}])

(defn transition-to-motion-complete [state]
  (let [ms (-> (get state "motion_state" {})
               (assoc "phase" (:motion-complete phases) "robotSignatures" robot-sigs "completionPct" 100))]
    {"motion_state" ms
     "motion_record" {"missionId" (get ms "missionId")
                      "distanceTraveled" (get ms "distanceTraveled")
                      "avgSpeed" (get ms "currentSpeed")
                      "motorMetrics" (get ms "motorMetrics")
                      "gpsTrajectory" (get ms "gpsTrajectory")
                      "attestingRobots" robot-sigs}
     "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-motors-engaged transition-to-path-following
           transition-to-speed-regulated transition-to-motion-complete]))
