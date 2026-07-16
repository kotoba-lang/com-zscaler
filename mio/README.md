# 澪 mio — Proof of Useful Flow

> **Hashrate → Flowrate.** Bitcoin's Proof of Work made *consumed* energy the basis
> of scarcity. **澪 mio** is the backbone of the **Energy Order Protocol**: it makes
> *ordered energy-flow* the basis of value. The reward basis is **ORDERED flow, never
> CONSUMED energy.**

澪 (mio) = the navigable channel — the *ordered path* through moving water. mio is the
**verification + accounting backbone** of the Energy Order Protocol actor suite. The
suite actors submit flow-improvement **claims**; mio decides which are real and accounts
the org-wide **Flowrate** (the verified useful-flow total).

```
撓 tawami (flexibility) ┐
燠 okibi  (waste-heat)  ├─ flow-improvement claims ─→ 澪 mio (verify + account) ─→ :reward (1 SBT=1 vote)
樋 toi    (compute)     ┤        §9 gates: baseline · additionality · measurement              │
委 yudane (intention)  ┘                · double-count-key · leakage                           └─→ hikari actuates (Council gate)
hikari (renewable/peak)
```

## The §9 verification problem is the gate

PoW is strong because verification is trivial (anyone can re-hash). Real-world energy
improvement is the opposite — it is *easy to claim and hard to verify*. So mio's defining
gate is verification. A flow-improvement claim reaches `:verified` (and therefore earns)
**only** if it carries all five facts and clears the confidence threshold:

| fact | why | failure → |
|---|---|---|
| `:baseline-method` | what counterfactual the delta is measured against | `:insufficient-evidence` |
| `:additionality` ≥ 0.3 | it would NOT have happened anyway | `:insufficient-evidence` |
| `:measurement-source` | a *trusted* measurement (self-report alone can't verify) | `:insufficient-evidence` |
| `:double-count-key` unique | the same saving isn't counted twice | `:rejected-double-count` |
| `:leakage` ≤ 0.5 | not offset by emissions elsewhere | `:rejected-leakage` |

`verification-confidence = measurement-weight × additionality × (1 − leakage)`, and
`useful-flow-score = order-delta-kWh × confidence` — **0 unless `:verified`**.

## Constitutional gates

- **G1 order-not-consumption** — reward derives ONLY from verified ordered flow. No
  `:consumed-reward` attribute exists; consumption never earns. (The PoW → PoUF pivot.)
- **G2 verification-is-the-gate** — the five §9 facts are the only path to reward.
- **G3 map-not-market-signal** — an order-delta is a disclosed fact, never a trade/forecast.
- **G4 kotoba-eavt-native** — append-only content-addressed commit-DAG (no SQL).
- **G5 content-free-intention** — 委 yudane claims are aggregate-only; no per-person text.
- **G7 no-server-key** — mio emits reward *proposals* (advisory/drafted-unsent); issuance
  is 1 SBT=1 vote + TitheRouter; actuation is hikari under Council gate.

## Run

```bash
./20-actors/mio/run_tests.sh                                   # 24 tests / 174 assertions
bb --classpath 20-actors 20-actors/mio/methods/analyze.cljc    # render the Proof-of-Useful-Flow ledger
bb --classpath 20-actors 20-actors/mio/methods/autorun.cljc    # one heartbeat → append verdicts (idempotent-by-content)
```

## Layout

```
20-actors/mio/
├── manifest.edn                     actor manifest (gates, methods, suite role)
├── kotoba/
│   ├── ontology.mio.edn             EAVT schema + negative space (unrepresentable attrs)
│   └── seed.edn                     flow-improvement claims (mixed provenance)
├── methods/
│   ├── mio_edn.cljc                 seed loader + classify
│   ├── analyze.cljc                 §9 verification → datoms → coverage → report
│   ├── kotoba.cljc                  content-addressed append-only verification ledger
│   └── autorun.cljc                 deterministic idempotent-by-content heartbeat
└── data/                            (gitignored) generated ledger
```

OBSERVATION + VERIFICATION ONLY. A resilience/reward map, **never a market signal**.
ADR-2606211200 · Energy Order Protocol.
