(ns silicon.cells.wafer-processing.state-machine
  "Wafer processing state machine — 1:1 port of cells/wafer_processing/state_machine.py (ADR-2605242500).
  Deterministic R0 mock-data phase machine (INIT → … → WAFER_VERIFIED, completionPct 0→100)."
  (:require [clojure.string]))

(def state-defaults {"phase" "init" "lotId" "LOT-DEMO-0001" "completionPct" 0})
(defn- cs [state] (merge state-defaults (get state "wafer_state" {})))

(defn transition-to-deposition-complete [state]
  {"wafer_state" (assoc (cs state) "phase" "deposition_complete" "completionPct" 20
                        "depositionData" {"material" "SiO2" "thickness_nm" 180 "deposition_method" "PECVD"
                                          "growth_rate_nm_min" 3.2 "uniformity_pct" 97.8 "refractive_index" 1.46})
   "next_node" "etch"})

(defn transition-to-etching-complete [state]
  {"wafer_state" (assoc (cs state) "phase" "etching_complete" "completionPct" 40
                        "etchingData" {"etch_process" "plasma_dry_etch" "etchant_gas" "C4F6_O2_Ar" "etch_depth_nm" 175
                                       "selectivity_ratio" 8.2 "line_edge_roughness_nm" 2.1 "etch_uniformity_pct" 96.5 "undercut_nm" 0.5})
   "next_node" "implant"})

(defn transition-to-implantation-complete [state]
  {"wafer_state" (assoc (cs state) "phase" "implantation_complete" "completionPct" 60
                        "implantData" {"dopant_species" "B+" "implant_energy_kev" 20 "implant_dose_cm2" 1e13
                                       "junction_depth_nm" 85 "sheet_resistance_ohm_sq" 450 "doping_uniformity_pct" 98.1})
   "next_node" "cmp"})

(defn transition-to-cmp-complete [state]
  {"wafer_state" (assoc (cs state) "phase" "cmp_complete" "completionPct" 80
                        "cmpData" {"cmp_process" "chemical_mechanical_polish" "polish_pad" "IC1000_mm"
                                   "slurry_type" "colloidal_silica" "removal_rate_nm_min" 45 "within_wafer_uniformity_pct" 98.3
                                   "polish_time_minutes" 3.8 "residual_thickness_nm" 5})
   "next_node" "verify_wafer"})

(def ^:private mock-sigs
  [{"robotDid" "did:web:etzhayyim.com:mimi-unit-2" "role" "wafer_metrology"
    "timestamp" "2026-05-26T15:45:30Z" "signature" "xX1yY2zZ3aA4bB5c..."}
   {"robotDid" "did:web:etzhayyim.com:otete-unit-3" "role" "process_handler"
    "timestamp" "2026-05-26T15:45:35Z" "signature" "dD6eE7fF8gG9hH0i..."}])

(defn transition-to-wafer-verified [state]
  (let [metrology {"thickness_nm" 5.2 "thickness_uniformity_pct" 98.5 "defect_count" 0
                   "defect_density_per_cm2" 0 "layer_stackup_correct" true "wafer_release_approved" true}
        c (assoc (cs state) "phase" "wafer_verified" "metrologyScan" metrology "robotSignatures" mock-sigs "completionPct" 100)]
    {"wafer_state" c
     "wafer_processing_record" {"lotId" (get c "lotId") "deposition" (get c "depositionData")
                                "etching" (get c "etchingData") "implantation" (get c "implantData")
                                "cmp" (get c "cmpData") "metrology" (get c "metrologyScan") "attestingRobots" mock-sigs}
     "next_node" "end"}))

(defn solve [_input-state]
  (throw (ex-info "silicon R0 scaffold: activate wafer_processing via Council ADR (post-2605242500 ratification)" {:scaffold true})))
