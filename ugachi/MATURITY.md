# ugachi 穿ち — MATURITY

| Phase | Scope | Status |
|---|---|---|
| **R0** (ADR-2606161800) | clj-native gate: loader + verdict/assess/datoms/coverage + synthetic project seed + tests | ✅ landed |
| **R1 — busshi grounding** (ADR-2606161830) | `bridge.cljc`: ground monopoly-effect in busshi 物資 concentration (corroborate/downgrade diversify, never fabricate entrench) → ground-and-assess | ✅ landed |
| **R2 — stewardship ledger** (ADR-2606170900) | `kotoba.cljc` content-addressed append-only ledger (tx-cid/verify-chain, tamper-evident, no-server-key) + `autorun.cljc` deterministic heartbeat (assess → append verdict datoms; resume-safe) | ✅ landed |
| **ie-flow / SoS score** (ADR-2606212200) | `ie_flow.cljc`: ugachi is scored as an information-control actor — RECTIFIES scattered multi-gen extraction risk into stewardship verdicts (propose-r0/route-to-recovery realised order; refuse = protective order); embeds the SHARED `etzhayyim.ie-flow.metrics` (not a fork) + `record-flow!`; in the SoS scoreboard (`80-data/ie-flow/scoreboard.edn`, score 0.303) feeding the organism reward | ✅ landed |
| R2+ | + rare-earth-coverage detail + kamado carbon-balance + inochi habitat-irreversibility as real gate inputs (G7/G9); Murakumo-narrated gate digest; lexicons; fleet registration; live kotoba-engine bridge | ⏳ |
| R3 | (only if ever) live actuation is a SEPARATE Council Lv7+ + no-server-key actor — NEVER ugachi | ⏳ (out of ugachi scope by G1/G7) |

## Tests

```
bb --classpath 20-actors 20-actors/ugachi/methods/test_ugachi_edn.cljc   # 3 tests / 8 assertions
bb --classpath 20-actors 20-actors/ugachi/methods/test_gate.cljc         # 11 tests / 29 assertions
bb --classpath 20-actors 20-actors/ugachi/methods/test_bridge.cljc       # 7 tests / 19 assertions (busshi grounding)
bb --classpath 20-actors 20-actors/ugachi/methods/test_kotoba.cljc       # 5 tests / 16 assertions (ledger)
bb --classpath 20-actors 20-actors/ugachi/methods/test_autorun.cljc      # 6 tests / 22 assertions (heartbeat + idempotency)
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" \
   20-actors/ugachi/methods/test_ie_flow.cljc                            # 4 tests / 12 assertions (SoS score)
```

36 tests / 106 assertions green.

## Invariants held

- G1 採掘しない (no actuation/extraction method; unrepresentable) · G2 not by-industry-name
- refuse on no-consent / carbon-positive / monopoly-entrenchment / irreversible-multigen-harm
- hard refusals precede recovery routing (no failing project returns a permit — meta-test)
- recovery-first preference (viable alt → kanayama) · permits-design only with Transparent-Force + consent
- clj-native + kotoba-Datom-native; verdict datoms flagged :ugachi/derived + :ugachi/sourcing
- stewardship ledger: content-addressed, tamper-evident (verify-chain), deterministic/resume-safe, no-server-key, gitignored (never committed)
- heartbeat idempotent-by-content: an unchanged beat is a no-op (`:appended false`) — a recurring loop never bloats the chain with identical snapshots; it grows only on real change
- R0 seed :synthetic (real-project assessment + live actuation = operator/Council steps)
