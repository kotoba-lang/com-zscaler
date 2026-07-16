# 樋 toi — CLAUDE (actor-local rules)

Compute-as-Thermal-Routing — the compute leg of the **Energy Order Protocol** suite
(submits to 澪 mio). OBSERVATION ONLY. ADR-2606211200.

## Invariants (do not weaken)

- **G1 map-not-job-kill.** toi maps routing; it NEVER issues a forced job-kill or
  load-shedding order. Never add a `:toi/dispatch` or `:toi.job/kill-order` attribute —
  the Murakumo fleet + operator actuate. A non-movable job is never coerced (stays
  in-place). Test `g1-no-dispatch-no-kill-order-no-trade` + `non-movable-job-stays-in-place`
  guard this.
- **G2 murakumo-default-preferred.** Transparency weight scores Murakumo / donated-mesh
  above commercial GPU (Rider §2(i) / ADR-2606172359). This is a NET SCORE, not a vendor
  ban — commercial GPU is a scored fallback, not forbidden. Keep `commercial-gpu` in the
  transparency map (low, not absent). Tests `murakumo-outscores-commercial-gpu` +
  `commercial-gpu-unused-while-clean-capacity-exists` guard the preference.
- **G3 waste-heat-to-okibi.** Heat-demand-sink sites are preferred; compute-as-heater
  only where a heat demand exists (never reward heating where cooling is needed).
- **G7 no-server-key.** Scheduler/carbon-intensity ingest is an operator step.

## Conventions

- clj/bb over the kotoba Datom log; append-only content-addressed commit-DAG ledger.
- Two node kinds (jobs + sites); greedy whole-job assignment by size, deterministic
  (stable sort by score then id). Ledger machinery byte-identical to mio/okibi.
- Tests: `./20-actors/toi/run_tests.sh` (babashka). Keep green before commit.

## Suite

backbone = 澪 mio. toi routes compute, offers waste heat to 燠 okibi, and submits routed
savings (avoided carbon / reusable heat) to mio as flow-improvement claims.
