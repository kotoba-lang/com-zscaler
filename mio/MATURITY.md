# 澪 mio — MATURITY scorecard

Actor: **澪 mio** · ADR-2606211200 · status **R0** · suite **Energy Order Protocol** (backbone)

## R0 checklist (15/15)

- [x] manifest.edn (gates G1–G7, methods, suite role, ledger spec)
- [x] ontology.mio.edn (EAVT schema + negative space / unrepresentable attrs)
- [x] seed.edn (15 flow-improvement claims, 6 flow classes, mixed provenance)
- [x] mio_edn.cljc (seed loader + classify)
- [x] analyze.cljc (§9 verification → datoms → coverage → report)
- [x] kotoba.cljc (content-addressed append-only verification ledger, verify-chain)
- [x] autorun.cljc (deterministic idempotent-by-content heartbeat)
- [x] test_mio_edn.cljc
- [x] test_analyze.cljc (incl. G1/G3/G5 invariants)
- [x] test_kotoba.cljc (commit-DAG roundtrip + tamper detection)
- [x] test_autorun.cljc (idempotent-by-content)
- [x] run_tests.sh (babashka) — **24 tests / 174 assertions green**
- [x] README.md
- [x] CLAUDE.md (actor-local invariants)
- [x] G1 backbone proven: useful-flow-score is 0 unless verified; no `:consumed-reward`

## Seed verification result (current)

| metric | value |
|---|---|
| claims | 15 |
| verified | 9 |
| verified Flowrate (kWh-equiv) | 37313.778 |
| rejected (double-count) | 1 |
| rejected (leakage) | 1 |
| insufficient-evidence | 4 |
| `:authoritative` provenance | 4/15 |

## R1 (next)

- [x] sibling submission seam — `撓/燠/樋/委` emit claims via per-leg `claim.cljc`; proven
      end-to-end through mio by `methods/test_suite.cljc` (LANDED 2026-06-21)
- [x] advisory reward proposal emitter — `methods/reward.cljc`: verified claims → moyai
      reciprocity-credit proposals (drafted-unsent; 1 SBT=1 vote + TitheRouter; cash≡0)
      (LANDED 2026-06-21)
- [ ] live measurement ingest (operator G7): signed-meter / third-party-audit / satellite
      / zk-proof adapters folding real `:authoritative` order-deltas
- [x] claim write-surface lexicon — com.etzhayyim.mio.flowClaim + methods/lexicon.cljc; all 25 emitted claims conform (LANDED 2026-06-21)
- [x] fleet registration (heartbeat cell in cell-runner cells.edn) — LANDED 2026-06-21
- [ ] kotoba_bridge — push the verification commit-DAG to the live kotoba engine (ibuki-R3 pattern)

## Negative space (must stay absent)

`:mio/trade` · `:mio/signal` · `:mio.obs/consumed-reward` · `:mio.claim/consumed-reward-kwh`
· `:mio.claim/price-forecast-point` · `:mio.person/intention-content`
