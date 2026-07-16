(ns magatama.cells.shionome-flow-graph.cell
  "shionome_flow_graph — per-bucket net capital-flow index (shionome).
  Resident in Kotoba WASM. Per ADR-2606072200. Capital-movement kinds only; edge-primary (G4).

  1:1 port of shionome_flow_graph/cell.py — the compiled graph wraps a single `index`
  super-step; `solve` runs it (START → index → END) over the input state."
  (:require [magatama.cells.shionome-flow-graph.state-machine :as sm]))

(defn solve
  "Run the compiled graph: per-bucket net capital flow (capital-movement kinds only)."
  [input-state]
  (sm/run-chain input-state))
