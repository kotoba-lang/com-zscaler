(ns magatama.cells.shionome-regime-observer.cell
  "shionome_regime_observer — FACTUAL cross-asset regime risk-on/off/mixed (shionome).
  Resident in Kotoba WASM. Per ADR-2606072200. Descriptive, never advice (G2, トレードはしない).

  1:1 port of shionome_regime_observer/cell.py — the compiled graph wraps a single `observe`
  super-step; `solve` runs it (START → observe → END) over the input state."
  (:require [magatama.cells.shionome-regime-observer.state-machine :as sm]))

(defn solve
  "Run the compiled graph: FACTUAL regime from net flow into risk vs safe buckets."
  [input-state]
  (sm/run-chain input-state))
