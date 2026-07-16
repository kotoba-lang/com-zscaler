# busshi 物資 — MATURITY

| Phase | Scope | Status |
|---|---|---|
| **R0** (ADR-2606161730) | clj-native scaffold: loader + analyze/datoms/coverage + `:representative` seed (25 commodities / 5 classes) + tests | ✅ landed |
| **R2 — observation ledger** (ADR-2606171000) | `kotoba.cljc` content-addressed append-only ledger (tx-cid/verify-chain, tamper-evident, no-server-key) + `autorun.cljc` deterministic, idempotent-by-content heartbeat (analyze → append on change; resume-safe) | ✅ landed |
| R1/R2+ | per-commodity depth (stocks/curve as facts, recycling-loop linkage to kanayama); primary-source live ingest behind G7 (USGS/EIA/public exchanges, no paid terminal G8); Murakumo-narrated digest; fleet registration; lexicons | ⏳ |
| R3 | content-addressed publish + WASM build (rare-earth-coverage/shionome pattern) | ⏳ |

## Tests

```
bb --classpath 20-actors 20-actors/busshi/methods/test_busshi_edn.cljc   # 3 tests / 9 assertions
bb --classpath 20-actors 20-actors/busshi/methods/test_analyze.cljc      # 9 tests / 55 assertions
bb --classpath 20-actors 20-actors/busshi/methods/test_kotoba.cljc       # 4 tests / 13 assertions (ledger)
bb --classpath 20-actors 20-actors/busshi/methods/test_autorun.cljc      # 5 tests / 22 assertions (heartbeat + idempotency)
```

21 tests / 99 assertions green (incl. G1 no-trade, G3 no-signal/no-forecast, G5 not-a-target-list invariants).

## Invariants held

- G1 取引しない · N1 採掘しない · G3 never forecasts · G2/G5 resilience map not a target-list
- clj-native + kotoba-Datom-native (EAVT EDN, derived datoms flagged)
- observation ledger: content-addressed, tamper-evident (verify-chain), deterministic/resume-safe, no-server-key, gitignored
- heartbeat idempotent-by-content: unchanged beat is a no-op (`:appended false`) — recurring loop never bloats the chain
- R0 seed `:representative` (live ingest = G7 operator step)
