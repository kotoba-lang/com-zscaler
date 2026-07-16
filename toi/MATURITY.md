# 樋 toi — MATURITY scorecard

Actor: **樋 toi** · ADR-2606211200 · status **R0** · suite **Energy Order Protocol** (compute leg)

## R0 checklist (15/15)

- [x] manifest.edn (gates G1–G7, methods, suite role, ledger spec)
- [x] ontology.toi.edn (job + site kinds, route params, negative space)
- [x] seed.edn (6 jobs + 5 sites; designed Murakumo-preferred / pinned-job cases)
- [x] toi_edn.cljc (seed loader + classify into jobs/sites)
- [x] analyze.cljc (site-score → greedy routing → avoided-carbon → datoms → coverage → report)
- [x] kotoba.cljc (content-addressed append-only routing ledger, verify-chain)
- [x] autorun.cljc (deterministic idempotent-by-content heartbeat)
- [x] test_toi_edn.cljc
- [x] test_analyze.cljc (incl. G1 + G2 Murakumo-preferred invariants)
- [x] test_kotoba.cljc (commit-DAG roundtrip + tamper detection)
- [x] test_autorun.cljc (idempotent-by-content)
- [x] run_tests.sh (babashka) — **21 tests / 98 assertions green**
- [x] README.md
- [x] CLAUDE.md (actor-local invariants)
- [x] G2 proven: Murakumo outscores commercial GPU; commercial GPU unused while clean capacity exists

## Seed routing result (current)

| metric | value |
|---|---|
| jobs / sites | 6 / 5 |
| routed | 5 (all movable jobs) |
| in-place (pinned) | 1 |
| avoided carbon | 1422.0 kgCO2 |
| waste heat reusable (→ okibi) | 1900.0 kWh |
| commercial-GPU utilization | 0 kWh (fallback, unused) |

Demonstrated: clean Murakumo sites absorb all movable compute (cold-hydro fills first
with heat-reuse jobs); commercial GPU scored lowest (0.055) and never chosen; the pinned
latency-bound job stays in-place (never coerced).

## R1 (next)

- [x] claim emitter (LANDED 2026-06-21) — a routed saving → a 澪 mio flow-improvement claim (avoided carbon +
      reusable heat; the suite seam); waste-heat handoff to 燠 okibi as a heat source
- [ ] live ingest (operator G7): Murakumo scheduler + grid carbon-intensity API
- [ ] deadline/deferral feasibility (currently movability-gated only)
- [x] fleet registration (heartbeat cell in cell-runner cells.edn) — LANDED 2026-06-21

## Negative space (must stay absent)

`:toi/dispatch` · `:toi.job/kill-order` · `:toi/trade` · `:toi/signal`
