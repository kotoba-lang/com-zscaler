(ns niyaku.cells.sts-hoist-cycle.state-machine
  "1:1 port of cells/sts_hoist_cycle/state_machine.py (ADR-2606082000). sts_hoist_cycle state machine — ADR-2606082000 (L3 ship-to-shore hoist: raise the box clear of the cell guides).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-hoist-commanded [state]
  {"cell_state" (assoc (cs state) "phase" "hoist_commanded" "completionPct" 25) "next_node" "box_lifted"})
(defn transition-to-box-lifted [state]
  {"cell_state" (assoc (cs state) "phase" "box_lifted" "completionPct" 50) "next_node" "clear_of_guides"})
(defn transition-to-clear-of-guides [state]
  {"cell_state" (assoc (cs state) "phase" "clear_of_guides" "completionPct" 75) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate sts_hoist_cycle via Council ADR (post-2606082015 ratification)" {:scaffold true})))
