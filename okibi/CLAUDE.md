# 燠 okibi — CLAUDE (actor-local rules)

Thermal Matching Market — the waste-heat leg of the **Energy Order Protocol** suite
(submits to 澪 mio). OBSERVATION ONLY. ADR-2606211200.

## Invariants (do not weaken)

- **G1 map-not-dispatch.** okibi maps matches; it NEVER issues a dispatch order. Never
  add a `:okibi/dispatch` attribute — hikari actuates under Council gate.
- **G2 physical-feasibility.** A match MUST pass the temperature cascade (source ≥
  sink-req + approach) AND distance. Infeasible pairs can never become matches — keep
  `feasible?` as the gate inside `match-heat`. A cooling LOAD is not a heat sink (the
  §1 anti-pattern); sinks are heat demands by construction. Tests
  `temperature-cascade-gate` + `distance-gate` + `cascade-failure-leaves-demand-unmet`
  guard this — keep them green.
- **G5 net-not-positive-carbon.** A match must not induce additional fossil heat
  (kamado net≤0). Waste heat to a non-heat-demand surfaces as unmatched, never a match.
- **G7 no-server-key.** Meter/nameplate ingest is an operator step; the loop does no I/O.

## Conventions

- clj/bb over the kotoba Datom log; append-only content-addressed commit-DAG ledger.
- Two node kinds (sources + sinks) — the loader/autorun thread both. The matching is a
  deterministic greedy allocation by quality (stable sort by quality then id).
- Ledger machinery is byte-identical to mio/tawami — keep the family consistent.
- Tests: `./20-actors/okibi/run_tests.sh` (babashka). Keep green before commit.

## Suite

backbone = 澪 mio. okibi submits realized matches to mio as flow-improvement claims
(delivered heat verified by signed BTU meter); hikari actuates under Council gate.
