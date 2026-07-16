# uzu 渦 — maturity scorecard

**Actor**: uzu 渦 — dissipative information-energy organism + real-world energy measurement/viz
**ADR**: 2606211500 · **Status**: 🟡 R0 · **Generated** from manifest + test run.

## R0 checklist (25/25)

| # | Criterion | State |
|---|---|---|
| 1 | ADR accepted | ✅ ADR-2606211500 |
| 2 | manifest.edn (clj-native, gates, non-goals) | ✅ 9 gates / 5 non-goals |
| 3 | ontology (kotoba EAVT schema + invariants) | ✅ `kotoba/ontology.uzu.edn` (5 invariants) |
| 4 | seed (tape + organisms + measured flows) | ✅ 12 steps · 3 organisms · 11 flows · 15 edges |
| 5 | generative model (VFE infer + EFE plan) | ✅ `model.cljc` |
| 6 | energy ledger (intake/cost/hazard/death) | ✅ `ledger.cljc` |
| 7 | dissipative loop (perceive→…→live-or-die) | ✅ `metabolism.cljc` |
| 8 | real-world measurement (4 honest units) | ✅ `measure.cljc` |
| 9 | visualization (self-contained, data-driven) | ✅ `viz.cljc` → `out/energy-field.html` |
| 10 | content-addressed append-only log | ✅ `kotoba.cljc` (verify-chain) |
| 11 | deterministic heartbeat (idempotent) | ✅ `autorun.cljc` |
| 12 | two-ledgers-never-conflated (G1) | ✅ test-enforced |
| 13 | never-equate-units / no-joules-per-meaning (G2/G3) | ✅ test-enforced |
| 14 | self-maintenance earned / mortality (G5) | ✅ kurage lives, meial+gyoja die |
| 15 | tests green | ✅ **106 tests / 240 assertions** |
| 16 | self-validating seed (integrity validator) | ✅ `validate.cljc` (defends I1–I5; + bb CLI) |
| 17 | lexicons + manifest↔ontology parity | ✅ `organismBeat` / `energyFlow` (drift-locked by test) |
| 18 | colony self-reflection (digest) | ✅ `digest.cljc` (survival · energy economy · field dissipation; + bb CLI) |
| 19 | multi-epoch seasons (live-epochs) | ✅ `metabolism/live-epochs` — net-negative world starves even the fittest (self-maintenance needs a net-positive niche) |
| 20 | robustness / adversarial property suite | ✅ `test_robustness.cljc` — energy accounting exact · belief normalized · choose affordable · finite · deterministic · unit-boundary over all flows |
| 21 | maturity self-audit (scorecard) | ✅ `scorecard.cljc` — tallies inventory + verifies every method/suite/lexicon file exists (manifest↔fs drift); + bb CLI |
| 22 | niche generator (world tapes) | ✅ `world.cljc` — abundant niche sustains+accumulates kurage across seasons; scarce starves it (self-maintenance needs a net-positive niche) |
| 23 | viability envelope (landscape) | ✅ `landscape.cljc` — meaning × niche survival matrix; fitness is joint (meial pathology lethal only in a punishing niche; gyoja starves in plenty) |
| 24 | sharp cross-cutting properties | ✅ `test_properties.cljc` — fold≡reduce · predict leak endpoints · pathology dormant w/o hazard (kurage≡meial) · richness ordering · digest consistency · energy veto absolute |
| 25 | log read path (query + as-of) | ✅ `query.cljc` — folds EAVT datoms → entity views + as-of time-travel; persisted colony digest/flows round-trip (the log is queryable) |

## Verified heartbeat (autorun)

```
kurage  alive=true  final-energy= 6.600 lifespan=12/12 actions={:forage 9, :flee 3}
meial   alive=false final-energy=-1.800 lifespan= 5/ 6 actions={:forage 6}
gyoja   alive=false final-energy=-8.200 lifespan= 3/ 4 actions={:flee 3, :rest 1}
physical Σ=1.730e+17 W · economic Σ=2.805e+15 USD/yr · informational Σ=1.670e+15 bit/s · experiential Σ=1.070 index
circulation closed? true · chain ok
```

## R1+ (Council/operator-gated)

- Live ingest of real flows from observatory siblings (kasa/kanjō/shionome/hikari/busshi) — G7.
- `:experiential` grounding via spirit-in-physics 霊性 (ADR-2606011501).
- WASM build (componentize-py) of the stateless beat (host owns the log).
- Candidate R2: merge uzu's energy ledger into ibuki's autonomy loop.
