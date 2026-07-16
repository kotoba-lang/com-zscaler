(ns niyaku.cells.yard-transfer.state-machine
  "1:1 port of cells/yard_transfer/state_machine.py (ADR-2606082000). yard_transfer state machine — ADR-2606082000 (L5 AGV/straddle transfer quay apron -> yard stack tier).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-agv-dispatched [state]
  {"cell_state" (assoc (cs state) "phase" "agv_dispatched" "completionPct" 25) "next_node" "box_landed"})
(defn transition-to-box-landed [state]
  {"cell_state" (assoc (cs state) "phase" "box_landed" "completionPct" 50) "next_node" "stack_updated"})
(defn transition-to-stack-updated [state]
  {"cell_state" (assoc (cs state) "phase" "stack_updated" "completionPct" 75) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate yard_transfer via Council ADR (post-2606082015 ratification)" {:scaffold true})))
