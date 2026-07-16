(ns noroshi.cells.device-design.cell
  "LangGraph Pregel wrapper for the noroshi (烽) device_design cell — R0
  scaffold. Matures this cell's descriptor (`cells/device_design.edn`)
  alongside `reliability_qual`, following `cells/active_alignment/cell.cljc`'s
  \"real, tested phase-transitions; `.solve()` still Council-gated\" shape.

  R0 scaffold: `.solve()` raises until Council activation (ADR-2606051600
  §Roadmap). The pure, unit-tested transitions — which DO call the real
  civilian-gate + open-EDA plan-generation engine in
  `methods/device-design` — live in
  `cells/device_design/state_machine.cljc`; `.solve()` stays gated. A real
  GDS mask write is a further, independently G8-gated step inside
  `methods/pic-layout` regardless of this cell's own activation level.")

(defn solve
  [_input-state]
  (throw (ex-info (str "noroshi R0 scaffold: activate device_design via Council ADR "
                       "(post-2606051600 ratification; live tapeout / mask-set order "
                       "remains G8-gated independent of this cell's own activation)")
                  {:scaffold true :cell :device-design})))
