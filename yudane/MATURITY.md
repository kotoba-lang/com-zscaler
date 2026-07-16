# 委 yudane — MATURITY scorecard

Actor: **委 yudane** · ADR-2606211200 · status **R0** · suite **Energy Order Protocol** (intention leg)

## R0 checklist (15/15)

- [x] manifest.edn (gates G1–G7, methods, suite role, ledger spec)
- [x] ontology.yudane.edn (offer kind, consent params, negative space = the degeneration series)
- [x] seed.edn (8 consented-intention offers; 4 consent + 4 refusal — one per gate)
- [x] yudane_edn.cljc (seed loader + classify)
- [x] analyze.cljc (consent verdicts → content-free datoms → coverage → report)
- [x] kotoba.cljc (content-addressed append-only intention ledger, verify-chain)
- [x] autorun.cljc (deterministic idempotent-by-content heartbeat)
- [x] test_yudane_edn.cljc (incl. no-person-field structural check)
- [x] test_analyze.cljc (incl. consent gates + degeneration-series unrepresentability)
- [x] test_kotoba.cljc (commit-DAG roundtrip + tamper detection)
- [x] test_autorun.cljc (idempotent-by-content)
- [x] run_tests.sh (babashka) — **21 tests / 96 assertions green**
- [x] README.md
- [x] CLAUDE.md (actor-local invariants)
- [x] G1/G2 proven: consent-bound + content-free + k-anonymous; no per-person field

## Seed consent result (current)

| metric | value |
|---|---|
| offers | 8 |
| consented | 4 |
| refused | 4 (k-anon / no-cap / expired / non-reciprocal — one per gate) |
| consented aggregate flex | 12800.0 kWh (crossed the membrane) |

Demonstrated: a cohort of 3 is refused (k-anonymity); a missing capability reads nothing;
an expired capability is refused (revocable leash); a non-reciprocal offer is refused
(symmetry). A cohort of 8 (≥5) passes. All datoms are content-free.

## R1 (next)

- [x] claim emitter (LANDED 2026-06-21) — a consented aggregate flex offer → a 撓 tawami flexibility asset +
      a 澪 mio flow-improvement claim (the suite seam)
- [ ] real revocable-leash capability verification (CACAO, member-signed; ibuki pattern)
      replacing the seed's opaque capability reference
- [ ] consent revocation path + expiry sweep
- [x] fleet registration (heartbeat cell in cell-runner cells.edn) — LANDED 2026-06-21

## Negative space (must stay absent — the degeneration series)

`:yudane.person/intent-content` · `:yudane.person/score` · `:yudane/denunciation` ·
`:yudane/surveil` · `:yudane/dispatch` · `:yudane/trade`
