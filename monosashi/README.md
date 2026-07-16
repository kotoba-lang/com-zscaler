# monosashi 物差し — Predictive-Actor Skill Yardstick

> **ADR-2606271800 · Tier-B · `did:web:etzhayyim.com:actor:monosashi` · Lexicon `com.etzhayyim.monosashi.*`**

物差し (*a measuring-stick*) is the etzhayyim **forecast-skill yardstick**: a proper-scoring
meta-evaluator that measures **how well the predictive actors forecast** — and is *structurally
forbidden from becoming a reward target*.

It binds the two real prediction engines and the structural model:

| input | from | what monosashi reads |
|---|---|---|
| score residuals (CRPS / pinball / logscore / **skill-vs-baseline** / PIT) | **mitooshi** 見通し | the empirical, outcome-verified skill of each forecast |
| system-dynamics ensemble (p10/p50/p90 band) | **tsuchifumi** 土踏み | band-width **coherence** cross-check (a diagnostic) |
| forward-sim distributions | **hakoniwa** 箱庭 | (an evaluated actor — its forecasts are scored too) |

…and emits, per **(actor, baseline)** pair (skill is never pooled across different baselines), a
**distribution-only skill band**:

```
mitooshi の予測スキル(対ベースライン climatology): n=3 件の分布として
p10=0.346 / 中央値p50=0.41 / p90=0.458。中央値はベースラインを上回ります。
※n=3 と少数のため帯域は外挿です。 較正逸脱(0=均一が良)=1.4。
これは可能性の分布であり、断定でも投資助言でもありません。
```

**Calibration** is a PIT-histogram deviation `Σ|freq − 1/bins|` (the mitooshi `calibration-summary`
primitive): **0 = uniform = calibrated**, larger = mis-calibrated (over- *or* under-confident). It
is not `mean|PIT−0.5|` — that would target 0.25 and be blind to under-dispersion. **Coherence** vs
tsuchifumi is computed *only* when a structural model over the **same series** is supplied (else it
is omitted, never smeared across unrelated series).

## Why a *measure, never a target* (the whole point)

> *"When a measure becomes a target, it ceases to be a good measure."* — Goodhart's law.

A swarm-prediction engine (the `666ghj/MiroFish` shape) becomes dangerous the moment forecast
accuracy is wired to a reward — it creates an incentive to **steer** the world toward the
prediction. etzhayyim already firewalls this: **reward lives in `mio` 澪 (Proof-of-Useful-Flow),
derived only from *verified realized flow*, never from forecast accuracy** (`mitooshi owns
forecasts; mio accounts realized flow`). monosashi makes that firewall explicit and enforced:

- **G3 anti-Goodhart** — `score/assert-no-reward` REFUSES any `:eval/reward` / `:eval/target` /
  `:eval/payout` / `:eval/incentive` / `:eval/stake` key (mirrors mio's `:consumed-reward` ban).
  The skill score never routes to `:reward`. The social post is non-steering (no "adopt/fund/reward
  this model").

## Charter gates

| gate | rule |
|---|---|
| **G1 / G6** | distribution-only — the skill output is a p10/p50/p90 band; `:eval/point-asserted` is structurally `false`; no point grade exists |
| **G2** | non-speculative — `:eval/use ∈ {model-assessment, resilience, planning, research}`; never a trade signal |
| **G3** | **anti-Goodhart / reward-decoupled** — no reward/target attribute exists or may be added; post is non-steering |
| **G5** | leak-free — every aggregated residual's `:score/observed-at` ≤ `:eval/as-of` (inherits mitooshi G5) |
| **G7** | member-signed — a `:published` post needs a member-DID author; `:post/server-held-key` false |
| **G12** | anti-pseudoscience — `:eval/skilled` true **only if** p50 skill > 0 vs a documented baseline |

## Run

```bash
# tests (16 tests / 69 assertions — incl. adversarial calibration, nil-author, leak, tamper, idempotency)
bb 20-actors/monosashi/run_tests.clj

# one autorun cycle → skill bands + social posts + content-addressed kotoba tx (idempotent-by-content)
bb -e '(require (quote [monosashi.methods.autorun :as ar]))
       (ar/run-cycle (ar/load-residuals "20-actors/monosashi/data/seed-scores.kotoba.edn")
                     {:as-of "2026-06-27T00:00:00Z" :tx-id "cycle-1" :status ":dry-run"})'

# verify the kotoba commit-DAG is intact (tamper-evident)
bb -e '(require (quote [monosashi.methods.kotoba :as k])) (println (k/verify-chain))'
```

A real `app.bsky.feed.post` to the live PDS goes through `methods/transport.cljc`
(`createSession → createRecord`); with no member credential it returns a structured `:blocked`
naming the missing leg — it never fabricates a publish (no-server-key).

## Layout

```
20-actors/monosashi/
├── manifest.edn              # Gen-3 kotoba-native manifest (drives the live registry)
├── methods/
│   ├── score.cljc            # skill-band core + assert-no-reward (G3) + instant-correct leak-free (G5) + G12
│   ├── social.cljc           # draft/emit (G1 no-point, G3 no-steer, G7 member-DID-signed)
│   ├── kotoba.cljc           # append-only content-addressed Datom log + verify-chain (tamper-evident)
│   ├── autorun.cljc          # deterministic, idempotent-by-content heartbeat (caller supplies tx-id + as-of)
│   └── transport.cljc        # REAL atproto createSession→createRecord (operator/member-gated; :blocked w/o creds)
├── lex/skillBand.edn         # the skill-band record shape (no reward field — by construction)
├── data/seed-scores.kotoba.edn  # FICTIONAL fixture (mitooshi residuals, per-series)
├── run_tests.clj             # bb-native runner (no shell, ADR-2606072802)
└── tests/                    # test_score / test_social (incl. adversarial cases)
```

The canonical state is the **kotoba Datom log** (`data/monosashi.datoms.kotoba.edn`, generated,
git-ignored): content-addressed, append-only, tamper-evident. The external AT-Proto relay leg is
**operator-gated (no-server-key)** — `emit` persists on-protocol and leaves the firehose delivery
to an operator/Council-held credential.
