(ns niyaku.cells.stowage-planning.state-machine
  "1:1 port of cells/stowage_planning/state_machine.py (ADR-2606082000). stowage_planning state machine — ADR-2606082000 (L1 compute bay/row/tier stow plan (weight/rotation/reefer/hazmat) + work sequence).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-plan-computed [state]
  {"cell_state" (assoc (cs state) "phase" "plan_computed" "completionPct" 25) "next_node" "sequence_ordered"})
(defn transition-to-sequence-ordered [state]
  {"cell_state" (assoc (cs state) "phase" "sequence_ordered" "completionPct" 50) "next_node" "no_rehandle_verified"})
(defn transition-to-no-rehandle-verified [state]
  {"cell_state" (assoc (cs state) "phase" "no_rehandle_verified" "completionPct" 75) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate stowage_planning via Council ADR (post-2606082015 ratification)" {:scaffold true})))
