(ns noroshi.cells.fibre-loop.cell
  "LangGraph Pregel wrapper for the noroshi (烽) fibre_loop cell — R0 scaffold.
  1:1 port of cells/fibre_loop/cell.py (ADR-2606051600).

  R0 scaffold: .solve() raises until Council activation (ADR-2606051600 §Roadmap). The cell lays
  fibre-optic cable along a planned route (cross-track tracking), actively aligns the fibre to a
  coupler under the laser-safety interlock (G5, reusing methods/active_alignment), evaluates a
  fusion splice, and commits a member-signed, witness-quorum'd, server-keyless segment (G7/G8) —
  dry-run only (G8). It is NOT a certified IEC 60825 safety controller. The pure, unit-tested
  transitions live in cells/fibre_loop/state_machine.cljc; .solve() stays Council-gated.")

(defn solve
  [_input-state]
  (throw (ex-info (str "noroshi R0 scaffold: activate fibre_loop via Council ADR "
                       "(post-2606051600 ratification; live cable-laying actuation Lv6+, Class-3B/4 lasers Lv7+)")
                  {:scaffold true :cell :fibre-loop})))
