(ns mitooshi.cells.forecast-issue.cell
  "LangGraph Pregel wrapper for mitooshi forecast_issue (見通し) — R0 scaffold.
  1:1 port of cells/forecast_issue/cell.py (ADR-2606051800).

  G1/G2 invariant gate: emits a probabilistic forecast (distribution-only, non-speculative
  use) into the Datom log with its info-as-of stamp. Coded reasoner in state_machine.cljc.
  solve raises at R0 — live forecast publication (atproto firehose) is Council Lv6+ +
  operator gated (G10).")

(defn solve
  [_input-state]
  (throw (ex-info "mitooshi R0 scaffold: forecast_issue validates offline; live forecast publication is Council Lv6+ + operator gated (G10)."
                  {:scaffold true :cell :forecast-issue})))
