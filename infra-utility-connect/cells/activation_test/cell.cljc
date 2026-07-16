(ns infra-utility-connect.cells.activation-test.cell
  "LangGraph Pregel wrapper for infra-utility-connect activation_test cell.
  1:1 port of cells/activation_test/cell.py.
  .solve() runs the init→process Pregel chain (state-machine/run-chain) and
  returns the merged state, exactly like Cell.solve compiling + invoking the graph."
  (:require [infra-utility-connect.cells.activation-test.state-machine :as sm]))

(defn solve
  "Run the activation-test Pregel graph over input-state → init → complete.
  String keys mirror the Python dict."
  [input-state]
  (sm/run-chain input-state))
