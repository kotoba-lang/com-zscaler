(ns kotodama.cells.tsukuroi-patch-synthesis.cell
  "LangGraph Pregel wrapper for the kotodama/tsukuroi patch_synthesis cell — R0 scaffold.
  1:1 port of cells/tsukuroi_patch_synthesis/cell.py (ADR-2605291500).
  .solve() raises at R0 (Council-gated R1 implementation).")

(defn solve
  [_input-state]
  (throw (ex-info "tsukuroi R0 scaffold: cell disabled until Council-gated R1 implementation"
                  {:scaffold true :cell :tsukuroi-patch-synthesis})))
