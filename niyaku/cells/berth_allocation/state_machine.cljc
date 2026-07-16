(ns niyaku.cells.berth-allocation.state-machine
  "1:1 port of cells/berth_allocation/state_machine.py (ADR-2606082000). berth_allocation state machine — ADR-2606082000 (L0 assign an arriving vessel to a berth + STS crane window).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-berth-assigned [state]
  {"cell_state" (assoc (cs state) "phase" "berth_assigned" "completionPct" 33) "next_node" "crane_window_reserved"})
(defn transition-to-crane-window-reserved [state]
  {"cell_state" (assoc (cs state) "phase" "crane_window_reserved" "completionPct" 67) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate berth_allocation via Council ADR (post-2606082015 ratification)" {:scaffold true})))
