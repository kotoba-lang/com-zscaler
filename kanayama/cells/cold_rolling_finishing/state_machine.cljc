(ns kanayama.cells.cold-rolling-finishing.state-machine
  "1:1 port of cells/cold_rolling_finishing/state_machine.py (ADR-2605252400 L5b). Cold rolling +
  temper to 0.27 mm can-stock coil + Migaki surface inspection: hot_band_loaded → cold_passes_complete
  → temper_complete → surface_inspection_complete → coil_qualified → record_emitted. ColdRollingState
  dataclass → string-keyed map under \"cold_rolling_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "cold_rolling_state" {}))

(defn transition-to-hot-band-loaded [state]
  {"cold_rolling_state" (assoc (s* state)
                               "inputHotBandCoilId" "KANAYAMA-HBC-2026-05-26-0001"
                               "phase" "hot_band_loaded" "completionPct" 10)
   "next_node" "cold"})

(defn transition-to-cold-passes-complete [state]
  {"cold_rolling_state" (assoc (s* state)
                               "coldPasses" [{"pass" 1 "in_mm" 3.0 "out_mm" 1.5}
                                             {"pass" 2 "in_mm" 1.5 "out_mm" 0.8}
                                             {"pass" 3 "in_mm" 0.8 "out_mm" 0.45}
                                             {"pass" 4 "in_mm" 0.45 "out_mm" 0.27}]
                               "finalGaugeMm" 0.27 "phase" "cold_passes_complete" "completionPct" 45)
   "next_node" "temper"})

(defn transition-to-temper-complete [state]
  {"cold_rolling_state" (assoc (s* state) "temper" "H19" "phase" "temper_complete" "completionPct" 65)
   "next_node" "migaki"})

(defn transition-to-surface-inspection-complete [state]
  {"cold_rolling_state" (assoc (s* state) "migakiInspectionFindings" []
                               "phase" "surface_inspection_complete" "completionPct" 80)
   "next_node" "qualify"})

(defn transition-to-coil-qualified [state]
  {"cold_rolling_state" (assoc (s* state)
                               "coilId" "KANAYAMA-COIL-2026-05-26-0001"
                               "coilMassKg" 12450.0 "qualificationAccept" true
                               "phase" "coil_qualified" "completionPct" 92)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"cold_rolling_state" s
     "coil_qualification_record" {"$type" "com.etzhayyim.kanayama.coilQualificationRecord"
                                  "lotId" (get s "lotId")
                                  "inputHotBandCoilId" (get s "inputHotBandCoilId")
                                  "coldPasses" (get s "coldPasses")
                                  "finalGaugeMm" (get s "finalGaugeMm")
                                  "temper" (get s "temper")
                                  "migakiInspectionFindings" (get s "migakiInspectionFindings")
                                  "coilId" (get s "coilId")
                                  "coilMassKg" (get s "coilMassKg")
                                  "qualificationAccept" (get s "qualificationAccept")
                                  "intendedProduct" "can-body-stock-AA3104"
                                  "recordedAt" "2026-05-26T16:30:00Z"}
     "next_node" "end"}))
