(ns infra-utility-connect.cells.service-request.cell
  "LangGraph Pregel wrapper for infra-utility-connect service_request cell.
  1:1 port of cells/service_request/cell.py.
  .solve() runs the init→process Pregel chain (state-machine/run-chain) and
  returns the merged state, exactly like Cell.solve compiling + invoking the graph."
  (:require [infra-utility-connect.cells.service-request.state-machine :as sm]))

(defn solve
  "Run the service-request Pregel graph over input-state → init → complete.
  String keys mirror the Python dict."
  [input-state]
  (sm/run-chain input-state))
