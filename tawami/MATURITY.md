# 撓 tawami — MATURITY scorecard

Actor: **撓 tawami** · ADR-2606211200 · status **R0** · suite **Energy Order Protocol** (flexibility leg)

## R0 checklist (15/15)

- [x] manifest.edn (gates G1–G7, methods, suite role, ledger spec)
- [x] ontology.tawami.edn (EAVT schema + negative space)
- [x] seed.edn (12 flexibility assets, 6 resource classes, mixed provenance)
- [x] tawami_edn.cljc (seed loader + classify)
- [x] analyze.cljc (flex-value → tier → best-use → datoms → coverage → report)
- [x] kotoba.cljc (content-addressed append-only flexibility ledger, verify-chain)
- [x] autorun.cljc (deterministic idempotent-by-content heartbeat)
- [x] test_tawami_edn.cljc
- [x] test_analyze.cljc (incl. G1/G2/G3 invariants)
- [x] test_kotoba.cljc (commit-DAG roundtrip + tamper detection)
- [x] test_autorun.cljc (idempotent-by-content)
- [x] run_tests.sh (babashka) — **20 tests / 134 assertions green**
- [x] README.md
- [x] CLAUDE.md (actor-local invariants)
- [x] G1 proven: no `:tawami/dispatch`; map-not-dispatch

## Seed analysis result (current)

| metric | value |
|---|---|
| assets | 12 |
| resource classes | 6 |
| total flex-value (kWh-equiv) | 5472.6 |
| fast-flex (grid-grade) | 3582.75 across 6 assets |
| `:authoritative` provenance | 4/12 |

## R1 (next)

- [x] claim emitter (LANDED 2026-06-21) — a USED flexibility → a 澪 mio flow-improvement claim (the suite seam)
- [ ] live telemetry ingest (operator G7): SCADA / charger telematics / MES adapters
- [ ] moyai reciprocity-credit handoff for flexibility provision (cash≡0)
- [x] fleet registration (heartbeat cell in cell-runner cells.edn) — LANDED 2026-06-21

## Negative space (must stay absent)

`:tawami/dispatch` · `:tawami/curtail-order` · `:tawami.person/load-profile` ·
`:tawami/trade` · `:tawami/signal`
