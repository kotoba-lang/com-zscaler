(ns silicon.cells.mask-lithography.state-machine
  "Mask lithography state machine — 1:1 port of cells/mask_lithography/state_machine.py (ADR-2605242500).
  Deterministic R0 mock-data phase machine (INIT → … → MASK_VERIFIED, completionPct 0→100)."
  (:require [clojure.string]))

(def state-defaults {"phase" "init" "waferId" "WAFER-DEMO-0001" "completionPct" 0})
(defn- cs [state] (merge state-defaults (get state "mask_state" {})))

(defn transition-to-mask-design-loaded [state]
  {"mask_state" (assoc (cs state) "phase" "mask_design_loaded" "designCid" "QmMaskDesign7nm20260526" "completionPct" 15)
   "next_node" "apply_photoresist"})

(defn transition-to-photoresist-applied [state]
  {"mask_state" (assoc (cs state) "phase" "photoresist_applied" "completionPct" 30
                       "photoresistData" {"resist_type" "EUV_chemically_amplified" "film_thickness_nm" 85
                                          "bake_temperature_c" 130 "bake_duration_s" 180 "coverage_uniformity_pct" 98.5})
   "next_node" "exposure"})

(defn transition-to-exposure-complete [state]
  {"mask_state" (assoc (cs state) "phase" "exposure_complete" "completionPct" 50
                       "exposureData" {"light_source" "EUV_13.5nm" "exposure_dose_mj_cm2" 24.5 "exposure_time_s" 15
                                       "focus_offset_nm" 0.8 "best_focus_position_nm" 45 "dose_uniformity_pct" 97.2})
   "next_node" "develop"})

(defn transition-to-development-complete [state]
  {"mask_state" (assoc (cs state) "phase" "development_complete" "completionPct" 70
                       "developmentData" {"developer_chemical" "TMAH_2.38%" "development_time_s" 45
                                          "development_temperature_c" 25 "line_width_nm" 7.2
                                          "line_edge_roughness_nm" 1.8 "feature_uniformity_pct" 96.8})
   "next_node" "verify_mask"})

(def ^:private mock-sigs
  [{"robotDid" "did:web:etzhayyim.com:mimi-unit-1" "role" "lithography_verifier"
    "timestamp" "2026-05-26T14:30:45Z" "signature" "lL1mM2nN3oO4pP5q..."}
   {"robotDid" "did:web:etzhayyim.com:otete-unit-2" "role" "process_monitor"
    "timestamp" "2026-05-26T14:30:50Z" "signature" "rR6sS7tT8uU9vV0w..."}])

(defn transition-to-mask-verified [state]
  (let [metrology {"cd_metrology_nm" 7.15 "cd_uniformity_3_sigma_nm" 0.45 "pattern_placement_nm" 2.5
                   "defect_count" 0 "defect_density_per_mm2" 0 "mask_qualification_pass" true}
        c (assoc (cs state) "phase" "mask_verified" "metrologyScan" metrology "robotSignatures" mock-sigs "completionPct" 100)]
    {"mask_state" c
     "mask_lithography_record" {"waferId" (get c "waferId") "designCid" (get c "designCid")
                                "metrology" (get c "metrologyScan") "attestingRobots" mock-sigs}
     "next_node" "end"}))

(defn solve [_input-state]
  (throw (ex-info "silicon R0 scaffold: activate mask_lithography via Council ADR (post-2605242500 ratification)" {:scaffold true})))
