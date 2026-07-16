# 20-actors/monosashi — CLAUDE.md

物差し — predictive-actor skill yardstick. Per-actor discipline. Read with the root `CLAUDE.md`.

## Identity
- **Name**: monosashi (物差し — a *measuring-stick*; a measure, NOT a target)
- **DID**: `did:web:etzhayyim.com:actor:monosashi` · **Tier-B** · ADR-2606271800
- **Role**: proper-scoring meta-evaluator. Measures the forecast skill of the predictive actors
  (mitooshi / hakoniwa / tsuchifumi) as a distribution-only band, and keeps that measure
  **structurally decoupled from reward**.

## Boundaries (do not cross)
- **monosashi does NOT forecast.** It scores forecasts. The forecast engines are mitooshi
  (probabilistic forecasting) and hakoniwa (forward-sim). tsuchifumi is a system-dynamics what-if.
- **monosashi does NOT reward.** Reward lives in `mio` (Proof-of-Useful-Flow), derived only from
  verified realized flow — never from forecast accuracy. monosashi never emits a reward/target.
- **monosashi does NOT re-implement mitooshi's scoring.** It *consumes* mitooshi score residuals
  (`:score/skill`, `:score/pit`, `:score/baseline-id`) and aggregates them into a band.
- **monosashi does NOT grade people.** It grades forecast/model quality per actor handle, never an
  individual.

## Invariants enforced in code (must stay true)
- **G1/G6** `score/skill-band` returns a p10/p50/p90 band; `:eval/point-asserted` is `false`; no
  `:eval/point` key; `kotoba/band-datoms` emits no point datom.
- **G3 (anti-Goodhart)** `score/assert-no-reward` is called on every band and REFUSES any
  `:eval/reward`/`:eval/target`/`:eval/payout`/`:eval/incentive`/`:eval/stake`. The lexicon has no
  such property. `social/guard-no-steer` refuses adoption/funding/reward steering in the post body.
- **G5** `score/assert-leak-free` REQUIRES `:score/observed-at` on every residual (no silent skip)
  and raises if it is > `:eval/as-of`, compared as parsed **instants** (`OffsetDateTime`, offset-
  correct) — never raw-string `compare` (unsound off the Z happy path).
- **Calibration** is `score/calibration-deviation` = PIT-histogram `Σ|freq−1/bins|` (0 = uniform =
  calibrated). NOT `mean|PIT−0.5|` (which targets 0.25 and is blind to under-dispersion). **Coherence**
  is computed only for a same-series structural model; otherwise omitted (never cross-series smear).
- **Idempotent-by-content** `autorun/run-cycle` no-ops when the new band-datoms equal the last tx's;
  `kotoba/verify-chain` recomputes every CID to prove the commit-DAG is tamper-evident.
- **G7** a `:published` post requires a member-DID `:author`; `:post/server-held-key` is `false`.
  The external AT-Proto relay leg is operator-gated (a `transport` fn with the operator credential);
  with no transport the post persists on-protocol (kotoba log) and the relay is
  `:pending-operator-transport`.
- **G12** `:eval/skilled` is true only if p50 skill > 0 vs the documented `:eval/baselines`.

## Determinism
Pure + deterministic: no `Math/random`, no wall clock. The caller supplies `:as-of` and `:tx-id`;
the kotoba tx CID chains onto the log's previous CID (commit-DAG, resume-safe). Two identical
cycles are byte-identical.

## Tests
`bb 20-actors/monosashi/run_tests.clj` — `monosashi.tests.test-score` + `monosashi.tests.test-social`
(G1/G3/G5/G7/G12 invariants + adversarial cases: inverted-calibration, nil-author G7 bypass, offset/
missing-timestamp leak, verify-chain tamper, idempotent re-run). Keep green; wire into the fleet check.
bb-native runner only — NO new `.sh` under 20-actors/ (ADR-2606072802 / `bb lint:no-new-shell`).

## Registry
`manifest.edn` is the SSoT. After editing it, run `bb gen:tier-b-actors` to regenerate
`50-infra/etzhayyim-did-web/src/registry/tier-b-actors.gen.ts` (do not hand-edit the `.gen.ts`).
The live `/.well-known/actors.json` + `/actor/monosashi/did.json` are served by the
`etzhayyim-did-web` CF Worker — deploy is operator-gated (wrangler).
