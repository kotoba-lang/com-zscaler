(ns tatekata.cells.foundation-excavation.state-machine
  "Foundation-excavation state machine — ADR-2605250715 §3 (Phase 1). 1:1 cljc port
  of `cells/foundation_excavation/state_machine.py`. Survey → plan → execute →
  anomaly-check → {witness → emit | HALT}. The cell.py graph wires a CONDITIONAL
  edge at anomaly_check (route on next_node). The default mock trips an anomaly
  (vibration 2.1 g > 2.0 g) → ANOMALY_HALT → alert_record. String keys mirror the
  Python dataclass __dict__ so the emitted record is byte-identical.

  NOTE: the graph's `survey` node is `_survey_utilities` (a no-op returning state);
  `transition_to_survey` is dead in cell.py (mapped to a method that is not a graph
  node). The run-chain reproduces the EXECUTED node sequence faithfully (survey is
  a no-op, so it is skipped)."
  (:require [clojure.string :as str]))

(def phases
  {:init "init" :survey "survey" :planning "planning" :execution "execution"
   :witness-wait "witness_wait" :anomaly-halt "anomaly_halt"
   :progress-record "progress_record" :complete "complete"})

(defn init
  "cell.py `_initialize_state`: fresh foundation_state + next_node \"survey\"."
  [state]
  {"foundation_state" {"phase" (:init phases)
                       "siteId" (get state "siteId" "unknown")
                       "completionPct" 0}
   "next_node" "survey"})

(defn transition-to-survey
  "INIT → SURVEY (dead in cell.py — kept for faithful 1:1; never called in the graph)."
  [state]
  (let [fs (-> (get state "foundation_state" {})
               (assoc "surveyData" {"site_coordinates" {"lat" 35.6762 "lon" 139.7674}
                                    "soil_classification" "N_value_20_clay"
                                    "existing_utilities" ["electrical_primary_3phase" "water_main_50mm" "gas_medium_pressure"]
                                    "hazards" ["power_line_20kv_northeast" "old_fuel_tank_subsurface"]
                                    "estimated_volume_m3" 1200}
                      "phase" (:survey phases) "completionPct" 10))]
    {"foundation_state" fs "next_node" "planning"}))

(defn transition-to-planning [state]
  (let [fs (-> (get state "foundation_state" {})
               (assoc "excavationPlan"
                      {"giemon_trajectory" {"num_passes" 5
                                            "pass_duration_seconds" [1200 1200 1200 1200 900]
                                            "arm_speed_mm_per_sec" 50 "max_depth_mm" 1200
                                            "bucket_capacity_m3" 0.15
                                            "estimated_completion_hours" (/ (double (* 5 1200)) 3600)}
                       "safety_zones" [{"type" "utilities_buffer" "distance_mm" 2000 "direction" "north"}
                                       {"type" "power_line_buffer" "distance_mm" 5000 "direction" "northeast"}]
                       "volume_check_against_survey" {"survey_m3" 1200 "plan_m3" 1200 "match" true}}
                      "phase" (:planning phases) "completionPct" 25))]
    {"foundation_state" fs "next_node" "execution"}))

(defn transition-to-execution [state]
  (let [fs (-> (get state "foundation_state" {})
               (assoc "phase" (:execution phases) "completionPct" 75
                      "photoCid" "QmCombined5PassPhotos.tar.gz"
                      "depthMapCid" "QmDepthMap32bitFloatGeoTIFF"))]
    {"foundation_state" fs "next_node" "anomaly_check"}))

(defn check-for-anomalies
  "EXECUTION → ANOMALY_HALT | WITNESS_WAIT. Port of `check_for_anomalies`: depth
  overshoot (5 mm, within spec) + vibration (2.1 g > 2.0 g → flagged)."
  [state]
  (let [fs0 (get state "foundation_state" {})
        final-depth 1195 spec-depth 1200
        overshoot-mm (- spec-depth final-depth)        ;; 5
        max-vibration-g 2.1
        anomalies (cond-> []
                    (< overshoot-mm -50) (conj (str "depth_overshoot_" overshoot-mm "mm"))
                    (> max-vibration-g 2.0) (conj (str "vibration_spike_" max-vibration-g "g")))
        fs (assoc fs0 "anomalyFlags" anomalies)]
    (if (pos? (count anomalies))
      {"foundation_state" (assoc fs "phase" (:anomaly-halt phases)
                                 "errorMsg" (str "Critical anomalies detected: "
                                                 (str/join "; " anomalies)))
       "next_node" "halt"}
      {"foundation_state" (assoc fs "phase" (:witness-wait phases) "completionPct" 80)
       "next_node" "witness_attestation"})))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:giemon-unit-1" "timestamp" "2026-05-26T09:45:32Z" "signature" "gSP5y7w8vK2mQ..."}
   {"robotDid" "did:web:etzhayyim.com:otete-unit-2" "timestamp" "2026-05-26T09:45:35Z" "signature" "xL9zN4bR6jD..."}])

(defn wait-for-witness-sigs [state]
  (let [fs (-> (get state "foundation_state" {})
               (assoc "robotSignatures" robot-sigs "phase" (:progress-record phases) "completionPct" 90))]
    {"foundation_state" fs "next_node" "emit_record"}))

(defn emit-progress-record [state]
  (let [fs0 (get state "foundation_state" {})
        record {"projectId" (get fs0 "siteId")
                "phase" "foundation_excavation"
                "completionPct" (get fs0 "completionPct")
                "recordedDate" "2026-05-26T09:46:00Z"
                "photoCid" (get fs0 "photoCid")
                "depthMapCid" (get fs0 "depthMapCid")
                "anomalyFlags" (or (get fs0 "anomalyFlags") [])
                "attestingRobots" (mapv #(get % "robotDid") (get fs0 "robotSignatures" []))}
        fs (assoc fs0 "phase" (:complete phases))]
    {"foundation_state" fs "constructed_record" record "next_node" "end"}))

(defn halt-on-anomaly [state]
  (let [fs (get state "foundation_state" {})
        alert {"event" "construction_halt"
               "reason" "anomaly_detected"
               "anomalies" (get fs "anomalyFlags")
               "timestamp" "2026-05-26T09:45:40Z"
               "escalation" "human_review_required"}]
    {"foundation_state" fs "alert_record" alert "next_node" "end"}))

(defn run-chain
  "Reproduce the cell.py graph: init→survey(no-op)→plan→execute→anomaly_check→
  {witness→emit | halt}, following the conditional on next_node. With the default
  mock the vibration anomaly fires → halt → alert_record."
  [input-state]
  (let [s0 (merge input-state (init input-state))   ;; survey node is a no-op (skipped)
        s3 (-> s0 transition-to-planning transition-to-execution check-for-anomalies)]
    (if (= "halt" (get s3 "next_node"))
      (halt-on-anomaly s3)
      (-> s3 wait-for-witness-sigs emit-progress-record))))
