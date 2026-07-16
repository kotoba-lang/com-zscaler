(ns kanayama.cells.dc-casting.state-machine
  "1:1 port of cells/dc_casting/state_machine.py (ADR-2605252400 L4). Direct-Chill slab casting
  (typical Al slab 1m × 0.6m × 8m) followed by homogenization 540–580°C × 12–24h. CastingState
  dataclass → string-keyed map under \"casting_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "casting_state" {}))

(defn transition-to-mold-prepared [state]
  {"casting_state" (assoc (s* state)
                          "slabDimensionsMm" {"width" 1000 "thickness" 600 "length" 8000}
                          "phase" "mold_prepared" "completionPct" 15)
   "next_node" "cast"})

(defn transition-to-dc-casting-complete [state]
  {"casting_state" (assoc (s* state) "castingTempC" 690 "chillWaterFlowLpm" 1800
                          "phase" "dc_casting_complete" "completionPct" 45)
   "next_node" "homogenize"})

(defn transition-to-homogenization-complete [state]
  {"casting_state" (assoc (s* state) "homogenizationTempC" 560 "homogenizationHours" 18
                          "phase" "homogenization_complete" "completionPct" 70)
   "next_node" "inspect"})

(defn transition-to-inspection-passed [state]
  {"casting_state" (assoc (s* state) "inspectionFindings" [] "slabMassKg" 12960.0
                          "phase" "inspection_passed" "completionPct" 90)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"casting_state" s
     "dc_casting_attestation" {"$type" "com.etzhayyim.kanayama.dcCastingAttestation"
                               "lotId" (get s "lotId")
                               "slabDimensionsMm" (get s "slabDimensionsMm")
                               "castingTempC" (get s "castingTempC")
                               "chillWaterFlowLpm" (get s "chillWaterFlowLpm")
                               "homogenizationTempC" (get s "homogenizationTempC")
                               "homogenizationHours" (get s "homogenizationHours")
                               "inspectionFindings" (get s "inspectionFindings")
                               "slabMassKg" (get s "slabMassKg")
                               "recordedAt" "2026-05-26T13:00:00Z"}
     "next_node" "end"}))
