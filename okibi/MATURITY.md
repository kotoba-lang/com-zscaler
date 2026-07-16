# 燠 okibi — MATURITY scorecard

Actor: **燠 okibi** · ADR-2606211200 · status **R0** · suite **Energy Order Protocol** (waste-heat leg)

## R0 checklist (15/15)

- [x] manifest.edn (gates G1–G7, methods, suite role, ledger spec)
- [x] ontology.okibi.edn (source + sink kinds, match params, negative space)
- [x] seed.edn (4 sources + 6 sinks; designed cascade / distance / surplus cases)
- [x] okibi_edn.cljc (seed loader + classify into sources/sinks)
- [x] analyze.cljc (temperature-cascade + distance greedy matching → datoms → coverage → report)
- [x] kotoba.cljc (content-addressed append-only thermal-matching ledger, verify-chain)
- [x] autorun.cljc (deterministic idempotent-by-content heartbeat)
- [x] test_okibi_edn.cljc
- [x] test_analyze.cljc (incl. physics gates + G1/G2 invariants)
- [x] test_kotoba.cljc (commit-DAG roundtrip + tamper detection)
- [x] test_autorun.cljc (idempotent-by-content)
- [x] run_tests.sh (babashka) — **21 tests / 92 assertions green**
- [x] README.md
- [x] CLAUDE.md (actor-local invariants)
- [x] G2 proven: cascade + distance gates; infeasible pairs never match

## Seed matching result (current)

| metric | value |
|---|---|
| sources / sinks | 4 / 6 |
| matches | 4 |
| matched thermal | 1138.5 kW |
| unmatched source surplus | 409.0 kW |
| unmatched demand | 238.4 kW (absorption-f cascade + spaceheat-e distance) |

Demonstrated: geothermal cascades to two demands (hot-water + drying); a 65°C DC cannot
serve a 90°C absorption chiller (cascade); a remote sink is unmet (distance); a remote
source is surplus.

## R1 (next)

- [x] claim emitter (LANDED 2026-06-21) — a realized match → a 澪 mio flow-improvement claim (delivered kWh
      with signed BTU meter; the suite seam)
- [ ] live meter/nameplate ingest (operator G7)
- [ ] zone/site index for O(sources×sinks) → spatial-bucketed matching at scale
- [x] fleet registration (heartbeat cell in cell-runner cells.edn) — LANDED 2026-06-21

## Negative space (must stay absent)

`:okibi/dispatch` · `:okibi.match/fabricated` · `:okibi/trade` · `:okibi/signal` ·
`:okibi.sink/cooling-load`
