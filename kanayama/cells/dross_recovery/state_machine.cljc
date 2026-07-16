(ns kanayama.cells.dross-recovery.state-machine
  "1:1 port of cells/dross_recovery/state_machine.py (ADR-2605252400 L3 cross-cutting + G14). Salt-
  cake processing → secondary-Al recovery + K-salt recycled; standalone disposal is a §2(g) violation
  so this cell enforces the G14 closed loop (landfill residue → 0). DrossState dataclass → string-
  keyed map under \"dross_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "dross_state" {}))

(defn transition-to-dross-collected [state]
  {"dross_state" (assoc (s* state) "drossMassKg" 8.0 "phase" "dross_collected" "completionPct" 20)
   "next_node" "salt_cake"})

(defn transition-to-salt-cake-processed [state]
  {"dross_state" (assoc (s* state) "saltCakeMassKg" 6.4 "phase" "salt_cake_processed" "completionPct" 45)
   "next_node" "al"})

(defn transition-to-secondary-al-recovered [state]
  {"dross_state" (assoc (s* state) "secondaryAlRecoveredKg" 3.6 "phase" "secondary_al_recovered" "completionPct" 70)
   "next_node" "k_salt"})

(defn transition-to-k-salt-recycled [state]
  {"dross_state" (assoc (s* state) "kSaltRecycledKg" 2.6 "landfillResidueKg" 0.2 "phase" "k_salt_recycled" "completionPct" 90)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"dross_state" s
     "dross_recovery_record" {"$type" "etzhayyim:kanayama:drossRecoveryRecord"
                              "lotId" (get s "lotId")
                              "drossMassKg" (get s "drossMassKg")
                              "saltCakeMassKg" (get s "saltCakeMassKg")
                              "secondaryAlRecoveredKg" (get s "secondaryAlRecoveredKg")
                              "kSaltRecycledKg" (get s "kSaltRecycledKg")
                              "landfillResidueKg" (get s "landfillResidueKg")
                              "g14CircularAccept" (< (or (get s "landfillResidueKg") 0) 0.5)
                              "recordedAt" "2026-05-26T11:15:00Z"}
     "next_node" "end"}))
