(ns tatekata.cells.structural-assembly.state-machine
  "Structural-assembly state machine — ADR-2605250715 §2 (Phase 2). 1:1 cljc port
  of `cells/structural_assembly/state_machine.py`. Load BIM → validate foundations
  → coordinate robots → execute → metrology-check → {witness → emit | HALT}. The
  cell.py graph wires a CONDITIONAL edge at metrology (route on next_node). The
  default mock passes metrology (max plumb 12 mm / level 8 mm ≤ 25 mm spec) →
  witness → structural_auth_record. String keys mirror the Python dataclass
  __dict__ so the emitted record is byte-identical.")

(def phases
  {:init "init" :bim-loaded "bim_loaded" :foundation-validated "foundation_validated"
   :robot-coordinated "robot_coordinated" :execution "execution" :metrology-check "metrology_check"
   :witness-wait "witness_wait" :anomaly-halt "anomaly_halt" :complete "complete"})

(defn init
  "cell.py `_initialize_state`: fresh structural_state + next_node \"load_bim\"."
  [state]
  {"structural_state" {"phase" (:init phases)
                       "projectId" (get state "projectId" "unknown")
                       "completionPct" 0}
   "next_node" "load_bim"})

(defn transition-to-bim-loaded [state]
  (let [ss (-> (get state "structural_state" {})
               (assoc "bimModelCid" "QmBimModelFreeCAD_Step_OpenCascade"
                      "designAssumptions"
                      {"model_format" "STEP (ISO 10303-21)" "foundation_footprint_m2" 850
                       "design_loads" {"dead_load_kN" 2400 "live_load_kN" 600 "wind_load_kPa" 1.2 "seismic_acceleration" 0.3}
                       "structural_elements" {"steel_columns" 8 "steel_beams" 24 "concrete_deck" 1 "shoring_posts" 12}
                       "material_specs" {"steel_grade" "SS400 (Japan) / ASTM A36 (US)" "concrete_target_mpa" 30 "bolt_grade" "8.8 (ISO 898-1)"}}
                      "phase" (:bim-loaded phases) "completionPct" 5))]
    {"structural_state" ss "next_node" "validate_foundations"}))

(defn transition-to-foundation-validated [state]
  (let [ss (-> (get state "structural_state" {})
               (assoc "foundationSurvey"
                      {"survey_bearing_capacity_kpa" 200 "design_bearing_requirement_kpa" 180
                       "validation_result" "pass" "soil_settlement_estimate_mm" 12
                       "settlement_acceptability" "within_limits" "shoring_design_matches_survey" true
                       "utility_clearances_verified" true}
                      "phase" (:foundation-validated phases) "completionPct" 15))]
    {"structural_state" ss "next_node" "coordinate_robots"}))

(defn transition-to-robot-coordinated [state]
  (let [ss (-> (get state "structural_state" {})
               (assoc "robotTrajectory"
                      {"giemon_shoring_plan" {"phase_duration_hours" 8 "shoring_posts_to_place" 12
                                              "post_spacing_m" 3.0 "max_post_load_kN" 200}
                       "otete_assembly_plan" {"steel_connections_phase_1" {"column_base_plates" 8 "column_beam_connections" 24
                                                                           "torque_spec_nm" [450 450 450 450] "estimated_duration_hours" 12}
                                              "concrete_deck_phase_2" {"concrete_volume_m3" 120 "placement_rate_m3_per_hour" 10
                                                                       "vibration_frequency_hz" 60 "slump_target_mm" 150}}
                       "safety_constraints" {"minimum_shoring_capacity_kN" 250 "plumb_tolerance_mm" 25 "level_tolerance_mm" 25}
                       "sequence_gates" ["foundation_curing_7_days" "shoring_placement_complete" "column_base_inspection_pass"
                                         "beam_connections_torqued_verified" "concrete_placement_complete" "deck_curing_7_days"]}
                      "phase" (:robot-coordinated phases) "completionPct" 25))]
    {"structural_state" ss "next_node" "execute_assembly"}))

(defn transition-to-execution [state]
  (let [ss (-> (get state "structural_state" {})
               (assoc "executionMetrics"
                      {"shoring_placed" 12 "shoring_load_test_passed" true "steel_columns_erected" 8
                       "column_base_connections" {"bolted" 8 "torque_verified_nm" [450 450 450 450 450 450 450 450] "all_within_spec" true}
                       "beam_connections" {"total_connections" 24 "field_welded" 12 "high_strength_bolted" 12 "inspection_pass_rate_pct" 100}
                       "concrete_placement" {"volume_placed_m3" 120 "slump_measured_mm" 145 "placement_duration_hours" 11.5
                                             "vibration_complete" true "air_content_pct" 6.5}
                       "photoCid_phases" ["QmStructuralPhase1ShoringPhotos" "QmStructuralPhase2SteelPhotos" "QmStructuralPhase3ConcretePhotos"]}
                      "phase" (:execution phases) "completionPct" 60
                      "photoCid" "QmCombined3PhaseStructuralPhotos.tar.gz"))]
    {"structural_state" ss "next_node" "metrology_check"}))

(defn transition-to-metrology-check
  "EXECUTION → METROLOGY_CHECK | ANOMALY_HALT. Port of `transition_to_metrology_check`:
  Mimi plumb/level scan; HALT if max plumb or level deviation > 25 mm (mock 12/8 pass)."
  [state]
  (let [ss0 (get state "structural_state" {})
        metrology {"column_plumb_deviations_mm" [5 8 3 12 6 4 7 9] "max_plumb_deviation_mm" 12 "plumb_spec_mm" 25 "plumb_pass" true
                   "deck_level_deviations_mm" [-8 6 -4 5 3 -2 4 -6] "max_level_deviation_mm" 8 "level_spec_mm" 25 "level_pass" true
                   "concrete_surface_flatness_f_number" 20 "flatness_pass" true "as_built_cid" "QmAsBuiltPointCloudLAS"}
        ss (assoc ss0 "metrologySurvey" metrology "phase" (:metrology-check phases) "completionPct" 75)]
    (if (or (> (get metrology "max_plumb_deviation_mm") 25) (> (get metrology "max_level_deviation_mm") 25))
      {"structural_state" (assoc ss "phase" (:anomaly-halt phases)
                                 "anomalyFlags" (if (> (get metrology "max_plumb_deviation_mm") 25) ["plumb_overshoot"] ["level_overshoot"])
                                 "errorMsg" (str "Metrology out of spec: plumb " (get metrology "max_plumb_deviation_mm")
                                                 "mm, level " (get metrology "max_level_deviation_mm") "mm"))
       "next_node" "halt"}
      {"structural_state" ss "next_node" "witness"})))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:giemon-unit-1" "role" "shoring_executor" "timestamp" "2026-05-26T14:30:15Z" "signature" "aBc9xY2zQ7mN5pK..."}
   {"robotDid" "did:web:etzhayyim.com:otete-unit-2" "role" "assembly_executor" "timestamp" "2026-05-26T14:30:20Z" "signature" "xL9zN4bR6jD8fG..."}
   {"robotDid" "did:web:etzhayyim.com:mimi-unit-3" "role" "metrology_witness" "timestamp" "2026-05-26T14:30:25Z" "signature" "mP2rT5vW8aB3cH..."}])

(defn transition-to-witness-attestation [state]
  (let [ss (-> (get state "structural_state" {})
               (assoc "robotSignatures" robot-sigs "phase" (:witness-wait phases) "completionPct" 85))]
    {"structural_state" ss "next_node" "emit_record"}))

(defn emit-structural-auth-record [state]
  (let [ss0 (get state "structural_state" {})
        metrology (get ss0 "metrologySurvey")
        record {"projectId" (get ss0 "projectId")
                "phase" "structural_assembly"
                "completionPct" (get ss0 "completionPct")
                "recordedDate" "2026-05-26T14:31:00Z"
                "photoCid" (get ss0 "photoCid")
                "asBuiltCid" (when metrology (get metrology "as_built_cid"))
                "metrologyResults" (get ss0 "metrologySurvey")
                "executionSummary" (get ss0 "executionMetrics")
                "anomalyFlags" (or (get ss0 "anomalyFlags") [])
                "attestingRobots" (mapv #(select-keys % ["robotDid" "role" "timestamp" "signature"])
                                        (get ss0 "robotSignatures" []))}
        ss (assoc ss0 "phase" (:complete phases) "completionPct" 100)]
    {"structural_state" ss "structural_auth_record" record "next_node" "end"}))

(defn halt-on-metrology-anomaly [state]
  (let [ss (get state "structural_state" {})
        alert {"event" "structural_halt" "reason" "metrology_anomaly"
               "anomalies" (get ss "anomalyFlags") "timestamp" "2026-05-26T14:30:40Z"
               "escalation" "human_structural_engineer_review_required"
               "corrective_action" "Shoring load increase or column re-plumb required"}]
    {"structural_state" ss "alert_record" alert "next_node" "end"}))

(defn run-chain
  "Reproduce the cell.py graph: init→load_bim→validate→coordinate→execute→metrology
  →{witness→emit | halt}, following the conditional on next_node. With the default
  mock metrology passes → witness → structural_auth_record."
  [input-state]
  (let [s (-> (merge input-state (init input-state))
              transition-to-bim-loaded transition-to-foundation-validated
              transition-to-robot-coordinated transition-to-execution
              transition-to-metrology-check)]
    (if (= "halt" (get s "next_node"))
      (halt-on-metrology-anomaly s)
      (-> s transition-to-witness-attestation emit-structural-auth-record))))
