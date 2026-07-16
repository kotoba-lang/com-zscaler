(ns kanayama.cells.decoating-separation.state-machine
  "1:1 port of cells/decoating_separation/state_machine.py (ADR-2605252400 L2). ~500°C rotary
  de-coater (lacquer/paint burnoff + off-gas capture/filter), rotary shredder, magnetic + eddy-
  current separation. DecoatingState dataclass → string-keyed map under \"decoating_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "decoating_state" {}))

(defn transition-to-decoater-heated [state]
  {"decoating_state" (assoc (s* state) "decoaterTempC" 500 "phase" "decoater_heated" "completionPct" 15)
   "next_node" "burnoff"})

(defn transition-to-lacquer-burnoff-complete [state]
  {"decoating_state" (assoc (s* state) "offGasCaptureCid" "bafkreioffgas..." "phase" "lacquer_burnoff_complete" "completionPct" 40)
   "next_node" "shred"})

(defn transition-to-shred-complete [state]
  {"decoating_state" (assoc (s* state) "shredFractionMm" {"size_distribution_mm" [5 10 20 50] "fines_pct" 8}
                            "phase" "shred_complete" "completionPct" 60)
   "next_node" "magnetic"})

(defn transition-to-magnetic-separation-complete [state]
  {"decoating_state" (assoc (s* state) "magneticFraction" {"removedFeKg" 1.4 "diversionPath" "kanayama-wave2-feedstock-bin"}
                            "phase" "magnetic_separation_complete" "completionPct" 75)
   "next_node" "eddy"})

(defn transition-to-eddy-current-separation-complete [state]
  {"decoating_state" (assoc (s* state) "nonAlFraction" {"removedCuKg" 0.6 "removedSteelTrimsKg" 0.2}
                            "cleanAlMassKg" 470.0 "phase" "eddy_current_separation_complete" "completionPct" 90)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"decoating_state" s
     "decoating_attestation" {"$type" "com.etzhayyim.kanayama.decoatingAttestation"
                              "lotId" (get s "lotId")
                              "decoaterTempC" (get s "decoaterTempC")
                              "offGasCaptureCid" (get s "offGasCaptureCid")
                              "shredFractionMm" (get s "shredFractionMm")
                              "magneticFraction" (get s "magneticFraction")
                              "nonAlFraction" (get s "nonAlFraction")
                              "cleanAlMassKg" (get s "cleanAlMassKg")
                              "recordedAt" "2026-05-26T09:30:00Z"}
     "next_node" "end"}))
