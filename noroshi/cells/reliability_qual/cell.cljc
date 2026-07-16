(ns noroshi.cells.reliability-qual.cell
  "LangGraph Pregel wrapper for the noroshi (烽) reliability_qual cell — R0
  scaffold. Matures this cell's descriptor (`cells/reliability_qual.edn`)
  alongside `device_design`, following `cells/active_alignment/cell.cljc`'s
  \"real, tested phase-transitions; `.solve()` still Council-gated\" shape.

  R0 scaffold: `.solve()` raises until Council activation (ADR-2606051600
  §Roadmap). The pure, unit-tested transitions — which DO call the real
  GR-468-SHAPE PASS/FAIL engine in `methods/reliability-qual` — live in
  `cells/reliability_qual/state_machine.cljc`; `.solve()` stays gated. No live
  chamber exists at R0 (G8) regardless of `.solve()`'s gate — every judgment
  is over caller-supplied results, never live I/O.")

(defn solve
  [_input-state]
  (throw (ex-info (str "noroshi R0 scaffold: activate reliability_qual via Council ADR "
                       "(post-2606051600 ratification; live environmental chamber testing "
                       "is G8-gated — this cell has no live chamber at any activation level "
                       "short of a real qualification lab integration)")
                  {:scaffold true :cell :reliability-qual})))
