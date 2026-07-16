(ns niyaku.cells.trolley-traverse.state-machine
  "1:1 port of cells/trolley_traverse/state_machine.py (ADR-2606082000). trolley_traverse state machine — ADR-2606082000 (L4 anti-sway trolley traverse ship<->shore (crane_dynamics / Isaac-Sim verified)).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-traverse-commanded [state]
  {"cell_state" (assoc (cs state) "phase" "traverse_commanded" "completionPct" 25) "next_node" "anti_sway_settled"})
(defn transition-to-anti-sway-settled [state]
  {"cell_state" (assoc (cs state) "phase" "anti_sway_settled" "completionPct" 50) "next_node" "over_target_slot"})
(defn transition-to-over-target-slot [state]
  {"cell_state" (assoc (cs state) "phase" "over_target_slot" "completionPct" 75) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate trolley_traverse via Council ADR (post-2606082015 ratification)" {:scaffold true})))
