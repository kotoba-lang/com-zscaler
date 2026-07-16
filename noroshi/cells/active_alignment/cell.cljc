(ns noroshi.cells.active-alignment.cell
  "LangGraph Pregel wrapper for the noroshi (烽) active_alignment cell — R0 scaffold.
  1:1 port of cells/active_alignment/cell.py (ADR-2606051600).

  R0 scaffold: .solve() raises until Council activation (ADR-2606051600 §Roadmap). The cell
  drives a photonic packaging robot to actively align a fibre to a grating coupler under a
  laser-safety interlock (G3/G5) and commits a member-signed, server-keyless packaging job (G7),
  dry-run only (G8). It is NOT a certified IEC 60825 safety controller. The pure, unit-tested
  transitions live in cells/active_alignment/state_machine.cljc; .solve() stays Council-gated.")

(defn solve
  [_input-state]
  (throw (ex-info (str "noroshi R0 scaffold: activate active_alignment via Council ADR "
                       "(post-2606051600 ratification; live actuation Lv6+, Class-3B/4 lasers Lv7+)")
                  {:scaffold true :cell :active-alignment})))
