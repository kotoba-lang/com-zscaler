(ns kotodama.cells.tadori-transparent-force-log.cell
  "LangGraph Pregel wrapper for the kotodama/tadori transparent_force_log cell — R0 scaffold.
  1:1 port of cells/tadori_transparent_force_log/cell.py (ADR-2605301400).
  .solve() raises at R0 (Council-gated R1 implementation).")

(defn solve
  [_input-state]
  (throw (ex-info "tadori R0 scaffold: cell disabled until Council-gated R1 implementation"
                  {:scaffold true :cell :tadori-transparent-force-log})))
