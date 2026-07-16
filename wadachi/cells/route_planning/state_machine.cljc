(ns wadachi.cells.route-planning.state-machine
  "Route-planning state machine — ADR-2605242000. 1:1 cljc port of
  `cells/route_planning/state_machine.py`. Validate destination → map obstacles →
  compute path → plan trajectory → ≥2-robot witness. String keys mirror the Python
  dataclass __dict__ so the emitted trajectory_record is byte-identical.")

(def phases
  {:init "init" :destination-validated "destination_validated" :obstacles-mapped "obstacles_mapped"
   :path-computed "path_computed" :trajectory-planned "trajectory_planned"})

(defn init [state]
  {"route_state" {"phase" (:init phases)
                  "missionId" (get state "missionId" "MISSION-2026-0001")
                  "completionPct" 0}})

(defn transition-to-destination-validated [state]
  (let [rs (-> (get state "route_state" {})
               (assoc "destination" {"latitude" 35.6895 "longitude" 139.6917 "altitude" 0
                                     "location_type" "construction_site" "access_restricted" false}
                      "phase" (:destination-validated phases) "completionPct" 20))]
    {"route_state" rs "next_node" "map_obstacles"}))

(defn transition-to-obstacles-mapped [state]
  (let [rs (-> (get state "route_state" {})
               (assoc "obstacles" [{"type" "building" "latitude" 35.6890 "longitude" 139.6910 "radius_m" 15}
                                   {"type" "construction_zone" "latitude" 35.6900 "longitude" 139.6920 "radius_m" 20}
                                   {"type" "pedestrian_area" "latitude" 35.6898 "longitude" 139.6915 "radius_m" 10}]
                      "phase" (:obstacles-mapped phases) "completionPct" 40))]
    {"route_state" rs "next_node" "compute_path"}))

(defn transition-to-path-computed [state]
  (let [rs (-> (get state "route_state" {})
               (assoc "pathWaypoints" [{"latitude" 35.6865 "longitude" 139.6900 "order" 1}
                                       {"latitude" 35.6875 "longitude" 139.6905 "order" 2}
                                       {"latitude" 35.6885 "longitude" 139.6913 "order" 3}
                                       {"latitude" 35.6895 "longitude" 139.6917 "order" 4}]
                      "phase" (:path-computed phases) "completionPct" 60))]
    {"route_state" rs "next_node" "plan_trajectory"}))

(defn transition-to-trajectory-planned [state]
  (let [distance-m 350
        max-speed-ms 5
        duration-s (/ (double distance-m) max-speed-ms)   ;; 70.0 (float division)
        rs (-> (get state "route_state" {})
               (assoc "trajectoryPlan" {"total_distance_m" distance-m
                                        "total_duration_seconds" duration-s
                                        "max_speed_ms" max-speed-ms "safety_margin_m" 2.0
                                        "waypoint_count" 4 "terrain_type" "urban" "traffic_class" "low"}
                      "estimatedDuration" duration-s "safetyMargin" 2.0
                      "phase" (:trajectory-planned phases) "completionPct" 80))]
    {"route_state" rs "next_node" "witness"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:sora-unit-1" "role" "route_planner"
    "timestamp" "2026-05-26T10:15:30Z" "signature" "aA1bB2cC3dD4eE5f..."}
   {"robotDid" "did:web:etzhayyim.com:wadachi-unit-1" "role" "execution_verifier"
    "timestamp" "2026-05-26T10:15:35Z" "signature" "gG6hH7iI8jJ9kK0l..."}])

(defn transition-to-witness-attestation [state]
  (let [rs (-> (get state "route_state" {})
               (assoc "robotSignatures" robot-sigs "completionPct" 100))]   ;; phase unchanged (trajectory_planned)
    {"route_state" rs
     "trajectory_record" {"missionId" (get rs "missionId")
                          "destination" (get rs "destination")
                          "waypoints" (get rs "pathWaypoints")
                          "trajectory" (get rs "trajectoryPlan")
                          "attestingRobots" robot-sigs}
     "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-destination-validated transition-to-obstacles-mapped
           transition-to-path-computed transition-to-trajectory-planned
           transition-to-witness-attestation]))
