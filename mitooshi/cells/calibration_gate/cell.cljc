(ns mitooshi.cells.calibration-gate.cell
  "LangGraph Pregel wrapper for mitooshi calibration_gate (見通し) — R0 scaffold.
  1:1 port of cells/calibration_gate/cell.py (ADR-2606051800).

  G7/G9/G12 promotion membrane: clears a model version for live forecasting ONLY if it
  beats baseline (skill>0), is calibrated (PIT deviation within bound), and carries a
  member/operator signature (no-server-key). Coded refusal gate in state_machine.cljc.
  solve raises at R0 — actual promotion is Council Lv6+ + operator gated (G10).")

(defn solve
  [_input-state]
  (throw (ex-info "mitooshi R0 scaffold: calibration_gate reviews offline; actual model promotion is Council Lv6+ + operator gated (G9/G10)."
                  {:scaffold true :cell :calibration-gate})))
