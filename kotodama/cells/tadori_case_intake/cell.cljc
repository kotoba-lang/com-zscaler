(ns kotodama.cells.tadori-case-intake.cell
  "LangGraph Pregel wrapper for the kotodama/tadori case_intake cell — R0 scaffold.
  1:1 port of cells/tadori_case_intake/cell.py (ADR-2605301400).
  .solve() raises at R0 (Council-gated R1 implementation).")

(defn solve
  [_input-state]
  (throw (ex-info "tadori R0 scaffold: cell disabled until Council-gated R1 implementation"
                  {:scaffold true :cell :tadori-case-intake})))
