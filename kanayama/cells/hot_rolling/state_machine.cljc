(ns kanayama.cells.hot-rolling.state-machine
  "1:1 port of cells/hot_rolling/state_machine.py (ADR-2605252400 L5a). Multi-pass hot rolling ~500°C,
  slab → hot band ~3 mm: reheat → 4-pass rough → 4-pass finish (passes accumulate) → coil → record.
  HotRollingState dataclass → string-keyed map under \"hot_rolling_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "hot_rolling_state" {}))

(defn transition-to-slab-reheated [state]
  {"hot_rolling_state" (assoc (s* state) "reheatTempC" 510 "phase" "slab_reheated" "completionPct" 15)
   "next_node" "rough"})

(defn transition-to-rough-roll-complete [state]
  {"hot_rolling_state" (assoc (s* state)
                              "passes" [{"pass" 1 "in_mm" 600 "out_mm" 400 "tempC" 510}
                                        {"pass" 2 "in_mm" 400 "out_mm" 250 "tempC" 495}
                                        {"pass" 3 "in_mm" 250 "out_mm" 120 "tempC" 480}
                                        {"pass" 4 "in_mm" 120 "out_mm" 60 "tempC" 470}]
                              "phase" "rough_roll_complete" "completionPct" 50)
   "next_node" "finish"})

(defn transition-to-finish-roll-complete [state]
  (let [extra [{"pass" 5 "in_mm" 60 "out_mm" 25 "tempC" 460}
               {"pass" 6 "in_mm" 25 "out_mm" 10 "tempC" 440}
               {"pass" 7 "in_mm" 10 "out_mm" 5 "tempC" 410}
               {"pass" 8 "in_mm" 5 "out_mm" 3 "tempC" 380}]
        s (s* state)]
    {"hot_rolling_state" (assoc s
                                "passes" (into (vec (or (get s "passes") [])) extra)
                                "finalGaugeMm" 3.0 "phase" "finish_roll_complete" "completionPct" 75)
     "next_node" "coil"}))

(defn transition-to-coiled [state]
  {"hot_rolling_state" (assoc (s* state)
                              "hotBandCoilId" "KANAYAMA-HBC-2026-05-26-0001"
                              "hotBandMassKg" 12700.0 "phase" "coiled" "completionPct" 90)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"hot_rolling_state" s
     "rolling_attestation" {"$type" "com.etzhayyim.kanayama.rollingAttestation"
                            "lotId" (get s "lotId")
                            "reheatTempC" (get s "reheatTempC")
                            "passes" (get s "passes")
                            "finalGaugeMm" (get s "finalGaugeMm")
                            "hotBandCoilId" (get s "hotBandCoilId")
                            "hotBandMassKg" (get s "hotBandMassKg")
                            "rollingStage" "hot"
                            "recordedAt" "2026-05-26T14:30:00Z"}
     "next_node" "end"}))
