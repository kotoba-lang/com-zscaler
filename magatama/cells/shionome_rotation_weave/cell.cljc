(ns magatama.cells.shionome-rotation-weave.cell
  "shionome_rotation_weave — top rotation pair どこからどこへ (shionome).
  Resident in Kotoba WASM. Per ADR-2606072200. Aggregate, edge-primary (G4); no per-asset score.

  1:1 port of shionome_rotation_weave/cell.py — the compiled graph wraps a single `weave`
  super-step; `solve` runs it (START → weave → END) over the input state."
  (:require [magatama.cells.shionome-rotation-weave.state-machine :as sm]))

(defn solve
  "Run the compiled graph: the largest bucket→bucket rotation (or {} when none)."
  [input-state]
  (sm/run-chain input-state))
