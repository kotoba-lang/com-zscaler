(ns niyaku.cells.spreader-engagement.state-machine
  "1:1 port of cells/spreader_engagement/state_machine.py (ADR-2606082000). spreader_engagement state machine — ADR-2606082000 (L2 align + engage the twistlock spreader on the target container).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-spreader-aligned [state]
  {"cell_state" (assoc (cs state) "phase" "spreader_aligned" "completionPct" 25) "next_node" "twistlocks_engaged"})
(defn transition-to-twistlocks-engaged [state]
  {"cell_state" (assoc (cs state) "phase" "twistlocks_engaged" "completionPct" 50) "next_node" "load_verified"})
(defn transition-to-load-verified [state]
  {"cell_state" (assoc (cs state) "phase" "load_verified" "completionPct" 75) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate spreader_engagement via Council ADR (post-2606082015 ratification)" {:scaffold true})))
