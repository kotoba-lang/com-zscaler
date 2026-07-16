(ns infra-utility-connect.cells.provider-approval.cell
  "LangGraph Pregel wrapper for infra-utility-connect provider_approval cell.
  1:1 port of cells/provider_approval/cell.py.
  .solve() runs the init→process Pregel chain (state-machine/run-chain) and
  returns the merged state, exactly like Cell.solve compiling + invoking the graph."
  (:require [infra-utility-connect.cells.provider-approval.state-machine :as sm]))

(defn solve
  "Run the provider-approval Pregel graph over input-state → init → complete.
  String keys mirror the Python dict."
  [input-state]
  (sm/run-chain input-state))
