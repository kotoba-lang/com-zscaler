# kafun 花粉 — MATURITY

| Phase | Scope | Status |
|---|---|---|
| **R0** (ADR-2606211712) | clj-native 花粉撲滅 remediation gate: loader + pollen-burden/verdict/assess/datoms/report + 12-stand synthetic seed + content-addressed REMEDIATION LEDGER (`kotoba.cljc`, verify-chain) + deterministic idempotent-by-content heartbeat (`autorun.cljc`) + tests | ✅ landed |
| **ie-flow embedding** (ADR-2606212030) | `ie_flow.cljc`: kafun assessment → measured ie-flow events folded through the SHARED `etzhayyim.ie-flow.metrics` (not a fork); energy-flow viz `viz/energy-flow.html` (整流 = scattered burden → prioritized restoration order; order-index 0.320 / η 6.58× / non-parasitic) | ✅ landed |
| **score + organism reward** (ADR-2606212200) | kafun is scored as an information-control actor (`etzhayyim.ie-flow.score`): info-control-score = its active-inference 利得, gated by 子孫 (:descendant 0.85); contributes to the colony-order negentropy source feeding ibuki's metabolic reward. Real scoreboard entry (score 0.452) | ✅ landed |
| R1 — inochi grounding | `bridge.cljc`: ground `:protected`/habitat-sensitivity in inochi 命's ecological observation (ugachi/busshi bridge pattern) — a stand in a high-biodiversity biome favors `:protected-selective` over clearcut, never fabricates protection | ⏳ |
| **ie-flow record!** (R1) | `ie_flow.cljc` `record-flow!` + `--record` flag: records kafun's measured ie-flow events to `80-data/ie-flow/kafun/flow.kotoba.edn` via `etzhayyim.ie-flow.embed` (the ie-flow ADR-2606212200 live-record follow-up) → kafun's SoS scoreboard entry is tool/heartbeat-produced, not adapter-on-demand only | ✅ landed |
| **Murakumo digest** (R1) | `digest.cljc`: kafun narrates its remediation MAP (L1-1/L3-1 bottlenecks + refusals + downstream actors) via the Murakumo fleet — injected `infer`, FAIL-OPEN to a deterministic template (G6, verified offline), loopback-only `murakumo-infer` (non-loopback host refused); emitted `:digest/status :dry-run` ONLY (G8, `:published` unrepresentable) | ✅ landed |
| **lexicons** (R1) | AT-proto lexicon JSON under `00-contracts/lexicons/com/etzhayyim/kafun/` — `remediationVerdict` + `pollenRemediationMap` (tatara/kaname convention); G1/G2/G5 guards as `const` (`isCutList:false`, `neverActuates:true`), all 11 verdict/reason/route enums in parity with `remediate.cljc` | ✅ landed |
| **fleet** (R1) | `cell.cljc` `fire` (KafunRemediationHeartbeatCell) registered in `50-infra/cluster/murakumo/cell-runner/cells.edn` — node simeon, cron `31 * * * *`, healthz 13091 (the kaname/mimamori maturity track); one deterministic, idempotent-by-content remediation beat (verified: fire#0 appends, fire#1 no-op, chain ok); no-server-key, no external I/O | ✅ landed |
| **system-dynamics ReAct loop** (ADR-2607102230) | `dynamics.cljc`: kafun's OWN readiness stock-flow (`:supply-level`/`:consent-level` accumulating toward a ready-threshold, re-scored through the UNCHANGED `remediate/verdict` — G1/G4 hold through a forecast exactly as live). `react_loop.cljc`: SENSE→ORIENT→HYPOTHESIZE→REVIEW→RANK→EVOLVE→ACT→OBSERVE→LEARN→PERSIST (mirrors ibuki's shape, ADR-2606201200); ACT is a pre-registered forecast + a propose-only route to sanae/musubi (never actuation kafun itself carries out, G5 unchanged); own ledger, idempotent-by-content, verify-chain | ✅ landed |
| R1 — real stands (G7) | real cadastral + Sentinel-2/ALOS canopy → kotoba (the legacy ADR-2605100100 scout→cadastral→envoy pipeline, behind an operator flip) | ⏳ (operator/Council step) |
| R1 — real readiness feed (G7) | replace `react_loop.cljc`'s `representative-progress` R0 stand-in with a real sapling-nursery/consent-registry feed (same operator-flip discipline as the canopy-detection roadmap) | ⏳ (operator/Council step) |
| R1 — react-loop fleet cell | register `react_loop/beat` as its own Murakumo cell (cron cadence + healthz port), separate from `KafunRemediationHeartbeatCell` | ⏳ |
| R2+ | live forestry — a SEPARATE landowner + operator/Council step, NEVER kafun (G5/G7) | ⏳ (out of kafun scope by G5) |

## Tests

```
bb --classpath 20-actors 20-actors/kafun/methods/test_kafun_edn.cljc    # 3 tests / 9 assertions
bb --classpath 20-actors 20-actors/kafun/methods/test_remediate.cljc    # 12 tests / 29 assertions
bb --classpath 20-actors 20-actors/kafun/methods/test_kotoba.cljc       # 3 tests / 11 assertions (ledger)
bb --classpath 20-actors 20-actors/kafun/methods/test_autorun.cljc      # 4 tests / 13 assertions (heartbeat + idempotency)
# the SoS embedding suite needs the shared ie-flow lib on the classpath:
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" \
   20-actors/kafun/methods/test_ie_flow.cljc                            # 8 tests / 28 assertions (ie-flow + viz + record!)
bb --classpath 20-actors 20-actors/kafun/methods/test_digest.cljc       # 8 tests / 21 assertions (Murakumo digest + fail-open)
bb --classpath 20-actors 20-actors/kafun/methods/test_bottleneck.cljc   # 4 tests / 8 assertions (pipeline bottleneck lens)
bb --classpath 20-actors 20-actors/kafun/methods/test_dynamics.cljc     # 8 tests / 15 assertions (readiness stock-flow)
bb --classpath 20-actors 20-actors/kafun/methods/test_react_loop.cljc   # 13 tests / 30 assertions (SD ReAct loop)
# or all nine at once:
./20-actors/kafun/run_tests.sh
```

63 tests / 164 assertions green.

## Invariants held

- **G1 撲滅 = restoration** — 主伐 without 再造林 → `:refuse :clearcut-without-reforest`; `:kafun/clearcut` + `:kafun.stand/eradicate-species` unrepresentable (test-enforced)
- **G5 never-acts** — no `:kafun/actuate`; assessment + R0 design only; live forestry is the landowner's + operator/Council step
- **G2 map-not-cut-list / no person data** — restoration worklist, never a cut-list/target-list; `:kafun.person/health` unrepresentable (cohorts aggregate)
- hard refusals precede every other route (no `replant=false` / net-carbon-positive stand returns a permit — meta-test)
- consent/land-sovereignty (G3) → `:await-consent` · protected (watershed/steep) → `:protected-selective` (never 皆伐) · carbon-balance §2(d) (G4) → `:refuse :carbon-positive`
- clj-native + kotoba-Datom-native; verdict datoms flagged `:kafun/derived` + `:kafun/sourcing`
- remediation ledger: content-addressed, tamper-evident (verify-chain), deterministic/resume-safe, no-server-key, gitignored (never committed)
- heartbeat idempotent-by-content: an unchanged beat is a no-op (`:appended false`) — a recurring loop never bloats the chain; it grows only on real change
- ie-flow: embeds the SHARED metrics (not a fork); kafun moves INFORMATION-energy only (a prioritized map), never physical forestry
- score: a parasitic / 子孫-harming kafun would be vetoed to 0 — it cannot feed the organism reward by predation (G-parasitism / G-subordinate as a scalar)
- R0 seed `:synthetic` (real cadastral/satellite ingest + live actuation = operator/Council steps)
- SD react-loop: every forecasted stand is re-scored through the UNCHANGED `remediate/verdict` — no duplicated or relaxed gate, so a `replant=false`/carbon-positive stand can never advance in a forecast either (G1/G4 test-enforced); ACT persists a forecast + a propose-only route, never an experiment kafun itself carries out (G5); the realized readiness rate is a function of the beat index only, never of kafun's own chosen scenario (a proposal is never conflated with the outside world's actual pace)
