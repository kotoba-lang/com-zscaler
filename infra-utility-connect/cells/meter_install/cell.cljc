(ns infra-utility-connect.cells.meter-install.cell
  "LangGraph Pregel wrapper for infra-utility-connect meter_install cell.
  1:1 port of cells/meter_install/cell.py.
  .solve() runs the init→process Pregel chain (state-machine/run-chain) and
  returns the merged state, exactly like Cell.solve compiling + invoking the graph."
  (:require [infra-utility-connect.cells.meter-install.state-machine :as sm]))

(defn solve
  "Run the meter-install Pregel graph over input-state → init → complete.
  String keys mirror the Python dict."
  [input-state]
  (sm/run-chain input-state))
