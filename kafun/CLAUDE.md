# 20-actors/kafun — CLAUDE.md

## What this is

**kafun 花粉** — the **花粉撲滅 remediation** actor. 撲滅 = ecological RESTORATION
(主伐再造林), **never deforestation-for-profit**. The clj-native Tier-B
actor-ization of the legacy `60-apps/etzhayyim-project-public-kafun-bokumetsu`
pipeline (ADR-2605100100 + 2605210928) onto the kotoba Datom log.

**ASSESSMENT + R0 DESIGN ONLY — kafun never cuts and never plants** (no actuation
method; live forestry is the landowner's + operator/Council step, exactly as
ugachi never digs).

`did:web:etzhayyim.com:kafun` · `com.etzhayyim.kafun.*` · ADR-2606211712 · clj-native R0.

## The gate (verdict algebra, `methods/remediate.cljc`)

Edge-primary `pollen-burden = min(1, area-ha/10000) · emission-density ·
(0.5 + 0.5·exposed-pop-weight)` scored on read. `verdict` →
`{:refuse :await-consent :protected-selective :await-sapling-supply
:reforest-priority :monitor}`, in order:

1. `replant=false` (主伐 without 再造林) → `:refuse :clearcut-without-reforest` (G1/G4)
2. `carbon :net-positive` → `:refuse :carbon-positive` (G4 / §2(d))
3. consent absent → `:await-consent` (G3, land sovereignty)
4. `protected` → `:protected-selective` (never 皆伐; gradual/selective)
5. `sapling-supply :none` → `:await-sapling-supply` (**L1-1** 無花粉苗木)
6. `burden ≥ 0.3` AND `reforest-viability ≥ 0.5` → `:reforest-priority` (**L3-1** 主伐再造林)
7. else → `:monitor`

**Hard refusals precede every other route** (meta-invariant: no `replant=false` /
net-carbon-positive stand returns a permit; test-enforced).

## Hard invariants (proven by tests)

- **G1 撲滅-is-restoration** — `:kafun/clearcut` + `:kafun.stand/eradicate-species` unrepresentable.
- **G5 never-acts** — no `:kafun/actuate`; assessment + R0 design only.
- **G2 map-not-cut-list / no person data** — `:kafun.person/health` unrepresentable.
- refuse-precedes-routing; report declares it is NOT a cut-list, DESIGN-ONLY, "never cuts".

## Files

```
methods/kafun_edn.cljc    loader + classify
methods/remediate.cljc    pollen-burden → verdict → assess → render-datoms → render-report (+ bb CLI)
methods/kotoba.cljc       content-addressed append-only REMEDIATION LEDGER (tx-cid/make-tx/append-tx/read-log/verify-chain)
methods/autorun.cljc      deterministic, idempotent-by-content heartbeat — assess → append ONLY on change (+ bb CLI)
methods/ie_flow.cljc      SoS: shared ie-flow metrics + energy-flow viz + --record (ADR-2606212030)
methods/digest.cljc       Murakumo-narrated remediation digest (fail-open template, G6; :dry-run only G8)
methods/dynamics.cljc     kafun's OWN readiness stock-flow (system dynamics; ADR-2607102230)
methods/react_loop.cljc   SD ReAct beat: SENSE..PERSIST over readiness, propose-only ACT (+ bb CLI; ADR-2607102230)
cell.cljc                 fleet heartbeat cell — `fire` (KafunRemediationHeartbeatCell, cells.edn)
methods/test_*.cljc       loader + gate + ledger + heartbeat + ie-flow + digest + bottleneck + SD-react-loop invariants
kotoba/ontology.kafun.edn EAVT schema + enums + refuse-reasons + negative space
kotoba/seed.edn           12 synthetic stands spanning all verdicts
data/ (gitignored)        generated remediation ledger — never committed/hand-edited
manifest.edn              gates G1–G8 + non-goals N1–N5
```

## 持続永続化 (persistence) — `methods/kotoba.cljc` + `methods/autorun.cljc`

Same content-addressed commit-DAG machinery as ugachi (ADR-2606170900): the
heartbeat appends verdict datoms as one content-addressed tx (prev-cid chained,
`verify-chain` tamper-evident) ONLY when they change (identical beat = no-op,
`:appended false :reason :no-change`); deterministic (caller supplies tx-id +
as-of, no wall clock) → resume-safe; no-server-key (local file, no network I/O).

## ie-flow / energy-flow (system of systems) — `methods/ie_flow.cljc` (ADR-2606212030)

kafun embeds the SHARED `etzhayyim.ie-flow.metrics` (the order calculus; NOT a fork).
Its substrate is the 花粉 burden: 散在 (scattered) pollen-source pressure = high-entropy
disorder. kafun's gate is a **RECTIFIER (整流)**: it folds that scattered burden flow onto
OUTCOMES — concentrating realised restoration value onto `:reforest-priority` stands and
routing the rest to named sinks. `order-index = 1 − H(value)/H(volume)` = how much disorder
was rectified into prioritized restoration order; `η = exported ÷ consumed` = the 共生 axis.

The verdict sinks feed downstream actors = **system of systems**: reforest-priority →
sanae+inochi · await-sapling-supply → sanae (無花粉苗木 L1-1) · await-consent → musubi ·
protected-selective → inochi · refuse → kamado · monitor → kafun. The visualization
(`viz/energy-flow.html`, self-contained canvas Sankey, generated from the model) makes the
transfer legible. Synthetic-seed result: order-index **0.320** (H 2.307→1.569), η **6.58×**,
net-gain **+133.9**, non-parasitic. kafun moves INFORMATION-energy (a prioritized map), never
physical forestry (assessment-only; G5). Live `record!`/`beat!` to
`80-data/ie-flow/kafun/flow.kotoba.edn` (gitignored) is the heartbeat/operator step.

## System-dynamics ReAct loop — `methods/dynamics.cljc` + `methods/react_loop.cljc` (ADR-2607102230)

kafun's OWN readiness stock-flow (`:supply-level`/`:consent-level` accumulating toward a
ready-threshold — NOT a reuse of the shared `etzhayyim.ie-flow.dynamics` SaaS-shaped stock or
tsuchifumi's `sysdyn.cljc`) wrapped in a ReAct beat mirroring ibuki's shape (ADR-2606201200):
SENSE (this loop's own ledger) → ORIENT (leak-free surprise) → HYPOTHESIZE (a fixed
readiness-rate catalog targeting the CURRENT binding bottleneck from
`remediation-bottlenecks`) → REVIEW → RANK (kaizen-weighted) → EVOLVE → **ACT** (a
PRE-REGISTERED forecast + a PROPOSAL routed to sanae/musubi — G5 unchanged: this is never an
experiment kafun itself carries out) → OBSERVE → LEARN → PERSIST (its own ledger,
`data/persisted/kafun.react-loop.kotoba.edn`, idempotent-by-content). Every forecasted stand is
re-scored through the UNCHANGED `remediate/verdict` — G1/G4 hold through a forecast exactly as
they hold live (no duplicated or relaxed gate). Fleet registration is a follow-up (out of scope
for R0 of this leg) — only `autorun/beat` runs on the fleet cron today.

## Run

```bash
./20-actors/kafun/run_tests.sh                                  # 9 suites (63 tests / 164 assert)
bb --classpath 20-actors 20-actors/kafun/methods/remediate.cljc # print the remediation map
bb --classpath 20-actors 20-actors/kafun/methods/autorun.cljc   # heartbeat → append to ledger
bb --classpath 20-actors 20-actors/kafun/methods/react_loop.cljc # SD react-loop beat → forecast + propose
# Murakumo-narrated remediation digest (fail-open to template; --live narrates via the fleet):
bb --classpath 20-actors 20-actors/kafun/methods/digest.cljc          # template (offline-safe)
bb --classpath 20-actors 20-actors/kafun/methods/digest.cljc --live   # Murakumo loopback (G6, fail-open)
# energy-flow viz (embeds shared ie-flow metrics; writes viz/energy-flow.html):
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/kafun/methods/ie_flow.cljc
# + --record: also record kafun's ie-flow events to the shared SoS ledger (80-data/ie-flow/kafun/, gitignored):
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/kafun/methods/ie_flow.cljc --record
```

## Fleet (`cell.cljc`)

`KafunRemediationHeartbeatCell` registered in `50-infra/cluster/murakumo/cell-runner/cells.edn`
— node **simeon**, cron `31 * * * *`, healthz **13091** (the kaname/mimamori track). `fire` runs
ONE deterministic, idempotent-by-content remediation beat (`autorun/beat`): an unchanged
assessment is a no-op (`:appended false :reason :no-change`). No-server-key, no external I/O; the
Murakumo digest `--live` narration + any live-engine bridge stay operator/Council-gated.

## Pairs with

- **sanae** (planting robotics — L1-1 苗木 + L3-1 再造林 body) · **inochi** (biosphere restoration)
- **mitate** + **iyashi** (allergic-rhinitis diagnosis/care — kafun does NOT diagnose/treat, N4)
- legacy App `60-apps/etzhayyim-project-public-kafun-bokumetsu` (outreach + Public Fund surface)
- Authorized by **ADR-2606211712**. Live forestry = landowner + operator/Council (never kafun).

## R0 → later

- **R1+**: inochi-grounding bridge (habitat sensitivity as a real gate input,
  ugachi/busshi bridge pattern); real cadastral + Sentinel-2/ALOS canopy → kotoba
  (the legacy scout→cadastral→envoy pipeline, behind a G7 operator flip);
  Murakumo-narrated remediation digest; fleet registration (cell-runner + healthz);
  live kotoba-engine bridge (ibuki-R3 pattern); lexicon JSON under
  `00-contracts/lexicons/com/etzhayyim/kafun/`; a real sapling-nursery/consent-registry
  feed to replace `react_loop.cljc`'s `representative-progress` R0 stand-in (G7-gated,
  same discipline as the canopy-detection roadmap); fleet cell registration for the SD
  react-loop's own cron cadence (ADR-2607102230). Live actuation stays landowner +
  operator/Council, never kafun.
