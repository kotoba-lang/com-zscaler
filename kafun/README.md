# kafun 花粉 — 花粉撲滅 remediation actor

**撲滅 = ecological RESTORATION (主伐再造林), NEVER deforestation-for-profit.**

clj-native Tier-B actor. The actor-ization of the legacy
`60-apps/etzhayyim-project-public-kafun-bokumetsu` pipeline (ADR-2605100100 +
2605210928) onto the kotoba Datom log. `did:web:etzhayyim.com:kafun` ·
`com.etzhayyim.kafun.*` · **ADR-2606211712** · R0.

Edge-primary **pollen-source concentration** is scored on read and each forest
**stand** routed to a remediation verdict. **ASSESSMENT + R0 DESIGN ONLY — kafun
never cuts and never plants** (live forestry is the landowner's + operator/Council
step, exactly as ugachi never digs).

## The score (on read)

```
pollen-burden = min(1, area-ha/10000) · emission-density · (0.5 + 0.5·exposed-pop-weight)
```

## The gate (`methods/remediate.cljc`)

`verdict` → `{:refuse :await-consent :protected-selective :await-sapling-supply
:reforest-priority :monitor}`, in order:

1. `replant=false` (主伐 without 再造林) → `:refuse :clearcut-without-reforest` (G1/G4)
2. `carbon :net-positive` → `:refuse :carbon-positive` (G4 / §2(d))
3. consent absent → `:await-consent` (G3, land sovereignty)
4. `protected` (watershed/steep/habitat) → `:protected-selective` (never 皆伐)
5. `sapling-supply :none` → `:await-sapling-supply` (**L1-1** 無花粉苗木 bottleneck)
6. `burden ≥ 0.3` AND `reforest-viability ≥ 0.5` → `:reforest-priority` (**L3-1** 主伐再造林)
7. else → `:monitor`

**Hard refusals precede every other route** — a non-restorative cut is never
"fixed" by high burden or consent (test-enforced meta-invariant).

## Hard invariants (proven by tests)

- **G1** 撲滅 is restoration — 主伐 without 再造林 refused; `:kafun/clearcut` +
  `:kafun.stand/eradicate-species` unrepresentable.
- **G2** restoration worklist, NEVER a cut-list/target-list; `:kafun.person/health`
  unrepresentable (cohorts aggregate).
- **G5** no actuation — `:kafun/actuate` unrepresentable; kafun never cuts/plants.
- refuse-precedes-routing; no clearcut/carbon-positive stand returns a permit.

## 持続永続化 (persistence)

`methods/kotoba.cljc` + `methods/autorun.cljc` — content-addressed append-only
**remediation ledger** (commit-DAG, `tx-cid = 'b'+sha256`, prev-cid chained,
`verify-chain` tamper-evident). The heartbeat is **deterministic** +
**idempotent-by-content**: an unchanged assessment is a NO-OP (`:appended false
:reason :no-change`) — the ledger records CHANGES, not a liveness tick.
Resume-safe, no-server-key.

## Files

```
methods/kafun_edn.cljc    loader + classify
methods/remediate.cljc    pollen-burden → verdict → assess → render-datoms → render-report (+ bb CLI)
methods/kotoba.cljc       持続永続化: content-addressed append-only REMEDIATION LEDGER
methods/autorun.cljc      持続永続化: deterministic, idempotent-by-content heartbeat (+ bb CLI)
methods/ie_flow.cljc      SoS: embeds shared etzhayyim.ie-flow.metrics + energy-flow viz generator (+ bb CLI)
methods/dynamics.cljc     kafun's OWN readiness stock-flow (system dynamics; ADR-2607102230)
methods/react_loop.cljc   SD ReAct beat over the readiness stock-flow (+ bb CLI; ADR-2607102230)
methods/test_*.cljc       loader + gate + ledger + heartbeat + ie-flow + bottleneck + SD-react-loop invariants
kotoba/ontology.kafun.edn EAVT schema + enums + refuse-reasons + negative space
kotoba/seed.edn           12 synthetic stands spanning all verdicts
viz/energy-flow.html      generated, self-contained energy-flow visualization (ie · SoS)
data/ (gitignored)        generated remediation ledger — never committed/hand-edited
manifest.edn              gates G1–G8 + non-goals N1–N5
```

## ie-flow / energy-flow (system of systems) — ADR-2606212030

kafun embeds the SHARED `etzhayyim.ie-flow.metrics` (order calculus, not a fork). It is a
**RECTIFIER (整流)**: scattered pollen-burden (散在 disorder) → prioritized restoration order.
`order-index = 1 − H(value)/H(volume)` (整流度), `η = exported ÷ consumed` (共生軸). Verdict sinks
feed downstream actors (sanae/inochi/musubi/kamado) = the **system of systems**. The
visualization `viz/energy-flow.html` (self-contained canvas Sankey, generated from the model)
shows the transfer. Synthetic-seed: order-index **0.320** (H 2.307→1.569), η **6.58×**, net-gain
**+133.9**, non-parasitic. kafun moves INFORMATION-energy only (assessment-only; never forestry).

```bash
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/kafun/methods/ie_flow.cljc
open 20-actors/kafun/viz/energy-flow.html
```

## System-dynamics ReAct loop (ADR-2607102230)

`methods/dynamics.cljc` is kafun's OWN readiness stock-flow (`:supply-level`/`:consent-level`
∈ [0,1] accumulating toward a ready-threshold, exactly like a bathtub filling before it
overflows) — a DIFFERENT stock from the shared `etzhayyim.ie-flow.dynamics` (SaaS-shaped) or
tsuchifumi's `sysdyn.cljc` (a separate risk-domain model). Every forecasted stand is re-scored
through the UNCHANGED `remediate/verdict` — a forecast can never make a `replant=false` or
carbon-positive stand advance (G1/G4 hold through the forecast, not just live assessment).

`methods/react_loop.cljc` wraps it in a ReAct beat (mirrors ibuki's shape, ADR-2606201200):
SENSE (fold its own ledger) → ORIENT (leak-free surprise) → HYPOTHESIZE (a fixed catalog of
readiness-rate scenarios targeting the CURRENT binding bottleneck) → REVIEW → RANK (kaizen-
weighted efficiency) → EVOLVE (recombine) → **ACT** (a PRE-REGISTERED forecast + a PROPOSAL
routed to sanae/musubi — never an experiment kafun itself carries out; G5 unchanged) → OBSERVE
→ LEARN → PERSIST (its own ledger, idempotent-by-content).

```bash
bb --classpath 20-actors 20-actors/kafun/methods/react_loop.cljc   # one SD react-loop beat
```

## Run

```bash
./20-actors/kafun/run_tests.sh                                  # 9 suites (63 tests / 164 assert)
bb --classpath 20-actors 20-actors/kafun/methods/remediate.cljc # print the remediation map
bb --classpath 20-actors 20-actors/kafun/methods/autorun.cljc   # heartbeat → append to ledger
bb --classpath 20-actors 20-actors/kafun/methods/react_loop.cljc # SD react-loop beat → forecast + propose
```

R0 synthetic seed → 3 `:reforest-priority`, 1 `:await-sapling-supply`,
1 `:await-consent`, 2 `:protected-selective`, 2 `:refuse`, 3 `:monitor`.

## Pairs with

- **sanae** (OSS planting robotics, the L1-1 苗木 line + L3-1 再造林 body) ·
  **inochi** (biosphere restoration — `:reforest-priority` / `:protected-selective` target)
- **mitate** (allergic-rhinitis diagnosis routing) + **iyashi** (care) — kafun does NOT diagnose/treat (N4)
- legacy App `60-apps/etzhayyim-project-public-kafun-bokumetsu` (outreach + Public Fund surface)
- Authorized by **ADR-2606211712**. Live forestry = landowner + operator/Council (never kafun).
