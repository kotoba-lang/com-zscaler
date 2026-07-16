(ns kotodama.cells.tadori-address-label.cell
  "LangGraph Pregel wrapper for the kotodama/tadori address_label cell — R0 scaffold.
  1:1 port of cells/tadori_address_label/cell.py (ADR-2605301400).
  .solve() raises at R0 (Council-gated R1 implementation).")

(defn solve
  [_input-state]
  (throw (ex-info "tadori R0 scaffold: cell disabled until Council-gated R1 implementation"
                  {:scaffold true :cell :tadori-address-label})))
