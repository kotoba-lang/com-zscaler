# minori 稔り — social-capital growth react-runtime

The resident clj/kotoba runtime that drives the **social-capital GROWTH score**
(ADR-2606261114) toward its targets via a react loop, and **evaluates the growth**.

```
観測(observe) → 計測(measure) → 実装(implement) → social-action(dry-run) → 評価(evaluate)
```

minori is the *runtime* counterpart of ADR-2606261114's static MAP
(`80-data/ie-flow/social-capital-valuation.edn`): the ADR measured where the value
*could* come from; minori reacts, beat by beat, to **close the captured-vs-addressable
gap and raise η from 0 (net taker) toward ≥1 (net giver)** — the phase transition that
is the ADR's "本丸" — and persists an as-of growth history.

## The score it climbs

`G = 0.35·η + 0.30·adoption + 0.20·capture + 0.15·Φ` (weights in `system.edn`), where:

| lever | meaning | target |
|---|---|---|
| **η** | exported÷consumed (共生 axis) | 0 → ≥1 (net-giver phase transition) |
| **adoption** | fraction of the actor roster running its SoS reward (ADR-2606212200) | full roster — the Part-3 capture-rate lever |
| **capture** | captured ÷ addressable | pre → 1% SOM milestone |
| **Φ** | realized energy-flow amplification | ln(n=18,342) ≈ ×9.8 ceiling |

**Non-parasitism gate**: while η<1 the loop is a *net taker*, so raw G is **not** rewarded —
only the give-back levers (η + adoption) score. Growth is fruition (稔り), never extraction.

## Charter-cleanliness

- The intervention catalog **cannot represent** a predatory mechanism, an η-lowering
  (net-taker) action, or an outward send: `:kind ∈ {:observe :measure :implement
  :social-action :symbiosis}`, all **dry-run / no-server-key** (live legs G7-gated).
- It **ranks structural levers, never persons** (NEVER-a-throne); it reads the MAP +
  SoS roster (edge-primary), stores no verdict-of-soul.
- Deterministic + resume-safe: beat index = ledger length, **no wall-clock / no randomness**.

## Run

```bash
bb --classpath 20-actors/minori/src \
   -e "(require 'minori.autorun) (minori.autorun/-main)"
```

Each invocation = one react beat appended to a content-addressed append-only ledger
(`data/ledger.edn`, gitignored, verify-chain tamper-evident), with a printed growth
evaluation (`G_prev → G_now`, ΔG, η, adoption). A `/loop 30m` cron drives it and deepens
one observe/measure/implement/social-action lever each fire.

## Layout

```
20-actors/minori/
├── system.edn              # SoS membrane + 報酬系 (reward) spec + score model
├── src/minori/
│   ├── score.cljc          # the growth-score model (η/adoption/capture/Φ, gated reward)
│   ├── react.cljc          # the beat: catalog → rank → apply (dry-run) → ΔG
│   ├── ledger.cljc         # content-addressed append-only ledger (verify-chain)
│   └── autorun.cljc        # heartbeat: one beat + persist + evaluate growth
├── test/minori/score_test.clj
└── data/                   # local runtime state (gitignored)
```

ADR: `90-docs/adr/2606261114-…md` · `90-docs/adr/2606212200-actor-system-of-systems-reward.md`
