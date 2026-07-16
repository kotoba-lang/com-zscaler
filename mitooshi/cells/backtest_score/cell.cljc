(ns mitooshi.cells.backtest-score.cell
  "LangGraph Pregel wrapper for mitooshi backtest_score (見通し) — R0 scaffold.
  1:1 port of cells/backtest_score/cell.py (ADR-2606051800).

  Runs the leak-free proper-scoring backtest (methods/score.cljc) over a set of issued
  forecasts and the observations that realized them, emitting a scorecard + per-forecast
  residual datoms + calibration + skill-vs-baseline. The coded reasoner lives in
  state_machine.cljc (score_batch — leak-checked, refuses G1 point / G2 use / G5 leak); the
  scoring engine itself runs (it is pure + offline). solve raises only because wiring it to
  the LIVE Datom log read/write is Council Lv6+ + operator gated (G10).")

(defn solve
  [_input-state]
  (throw (ex-info "mitooshi R0 scaffold: scoring runs offline via methods/score.cljc; live Datom log read/write is Council Lv6+ + operator gated (G10)."
                  {:scaffold true :cell :backtest-score})))
