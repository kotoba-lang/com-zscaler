(ns niyaku.cells.emissions-audit.state-machine
  "1:1 port of cells/emissions_audit/state_machine.py (ADR-2606082000). emissions_audit state machine — ADR-2606082000 (cross-cutting electric-crane energy + regenerative-recovery audit).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-energy-metered [state]
  {"cell_state" (assoc (cs state) "phase" "energy_metered" "completionPct" 33) "next_node" "regen_credited"})
(defn transition-to-regen-credited [state]
  {"cell_state" (assoc (cs state) "phase" "regen_credited" "completionPct" 67) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate emissions_audit via Council ADR (post-2606082015 ratification)" {:scaffold true})))
