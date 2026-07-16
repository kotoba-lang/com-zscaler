(ns magatama.cells.shionome-ingest.cell
  "shionome_ingest — cross-asset capital-flow intake membrane (shionome).
  Resident in Kotoba WASM. Per ADR-2606072200.

  Screens a public market-data batch from context (G1/G2/G3, トレードはしない) and emits the
  validated flows downstream. Live market-data ingest into the substrate is Council Lv6+ +
  operator gated (G8); this cell screens whatever batch is already in context.

  1:1 port of shionome_ingest/cell.py — the kotoba-WASM/StateGraph wrapper compiled a single
  `screen` super-step; `solve` runs that super-step (START → screen → END) over the input state."
  (:require [magatama.cells.shionome-ingest.state-machine :as sm]))

(defn solve
  "Run the compiled graph: screen the context market batch (G1/G2/G3).
  Refuses the whole batch on violation — never silently ingest a trade-token / undersourced flow."
  [input-state]
  (sm/run-chain input-state))
