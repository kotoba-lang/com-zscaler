(ns niyaku.cells.manifest-attestation.state-machine
  "1:1 port of cells/manifest_attestation/state_machine.py (ADR-2606082000). manifest_attestation state machine — ADR-2606082000 (terminal per-move kotoba EAVT anchor + open move registry).
  R0 scaffold: phase transitions are structural placeholders; .solve() raises until Council Lv6+ ratifies ADR-2606082015."
  (:require [clojure.string]))

;; CellState default (R0 0%-completion INIT record)
(def cell-state-defaults
  {"phase" "init" "moveId" "NIYAKU-MOVE-0001" "vesselId" "MV-DEMO-0001"
   "terminalId" "JPYOK-T1" "completionPct" 0 "robotSignatures" [] "payload" {}})

(defn- cs [state] (merge cell-state-defaults (get state "cell_state" {})))

(defn transition-to-move-recorded [state]
  {"cell_state" (assoc (cs state) "phase" "move_recorded" "completionPct" 33) "next_node" "datom_anchored"})
(defn transition-to-datom-anchored [state]
  {"cell_state" (assoc (cs state) "phase" "datom_anchored" "completionPct" 67) "next_node" "attestation_emitted"})
(defn transition-to-attestation-emitted [state]
  {"cell_state" (assoc (cs state) "phase" "attestation_emitted" "completionPct" 100) "next_node" "end"})

(defn solve [_input-state]
  (throw (ex-info "niyaku R0 scaffold: activate manifest_attestation via Council ADR (post-2606082015 ratification)" {:scaffold true})))
