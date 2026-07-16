# 20-actors/iriai — CLAUDE.md

## What this is

**iriai 入会** — the non-profit **global lifeline-commons** actor. 入会 (iriai) = the
traditional Japanese **commons**: collectively-held rights of use over a shared resource.
Here the resource is the four lifelines (ライフライン) — **電気 / 水道 / ガス / 通信** — held
as a commons right of use (**入会権**), delivered §1.16 social-security **in-kind** (cash≡0),
governed **1 SBT = 1 vote**.

iriai is the **System-of-Systems umbrella** over the producer actors (the way **kaname 要** /
**amime 網目** synthesize across single-domain mirrors): 電気→**hikari 光** · 水道→**mizuho 水穂**
· ガス→**kamado 竈** · 通信→**noroshi 烽**. It does **infra + 資金 (funding) + 管理 (management)**
in one heartbeat. It **never produces and never actuates** a lifeline — ASSESSMENT + R0 DESIGN ONLY.

`did:web:etzhayyim.com:iriai` · `com.etzhayyim.iriai.*` · ADR-2606272200 + **2606280900** · clj-native R0.

## The five layers

### infra (`methods/infra.cljc`) — coverage + resilience

Edge-primary, on read: `commons-gap = (1 − coverage) · essentiality · (0.5 + 0.5·vulnerability)`
(essentiality 水 1.0 · 電 0.9 · 通信 0.7 · ガス 0.6) + resilience (single-source SPOF / N-1 margin).
verdict → `{:await-consent :provision :reinforce :redundancy :maintain :monitor}`:

1. action-needed AND no consent → `:await-consent` (land sovereignty, G3)
2. disaster-degraded → `:reinforce`
3. commons-gap ≥ 0.30 → `:provision` (§1.16 reach gap)
4. single-source OR N-1 < 0 → `:redundancy`
5. coverage ≥ 0.85 AND resilient → `:maintain`
6. else → `:monitor`

A COVERAGE + RESILIENCE map — **never a shut-off list**; a lifeline is never withheld (G1).

### 資金 fund (`methods/fund.cljc`) — §1.16 in-kind, cash≡0

provision/reinforce/redundancy → a funding proposal on the non-profit rails:
**donation → TitheRouter 10% → Public Fund → grant/milestone-escrow/in-kind** (decided by
1 SBT = 1 vote, NOT iriai). Delivery is **§1.16 social-security in-kind** — cash ≡ 0 to the
consumer, never billed, never disconnected (G2). Imputed market-equivalent value is
transparency-only (the income is HIGH while cash≡0; ADR-2605301020). subaru 昴 precedent +
Displacement-Dividend coupling.

### 管理 manage (`methods/manage.cljc`) — 1 SBT=1 vote + :intent-only + no-server-key

Each proposal → governance envelope: 1 SBT = 1 vote (20% quorum / 50% / 48h) + Council Lv6+
(critical-infra → Lv7+); **actuation-class :intent** (compute-only R0 — live act is the producer
cell under Council Lv7+ + operator-DID + member-sig, G5); **no-server-key** (member-CACAO leash, G6).

### 物理シミュレーション twin (`methods/twin.cljc`) — degradation + condition (ADR-2606280900)

Per DEPLOYED asset, a **real engineering degradation model** → condition (0..1) + remaining-useful-life
(RUL, yr) + operating margin + structural safety:

- electric (transformer) — IEEE C57.91 thermal aging: load → hot-spot θh → `FAA = exp(15000/383 − 15000/(θh+273))` → loss-of-life
- water (main) — Hazen-Williams `C(t)=C0−k·t`; gas (main) — wall corrosion → leak-prob (safety floor)
- telecom (fibre) — attenuation creep vs link budget; **road (道路)** — pavement PCI `100−a·t^b` + bridge load-rating

`project` runs the twin **ahead** of reality → maintenance is **preventive**. SIM ONLY (G5). Same
twin discipline as the infra-robotics device-loop (ADR-2606101430), at the condition timescale.

### 運用メンテナンス maintain (`methods/maintain.cljc`) — lifecycle + OpEx (ADR-2606280900)

Twin → maintenance verdict, **SAFETY FLOOR FIRST**:
`{:decommission :renew :corrective-repair :refurbish :preventive-service :inspect :ok}`. An unsafe
asset is never deferred for cost (G9). Each routes to an **executor** (kuni-umi / tazuna / giemon /
noroshi / hodoki+kanayama) and imputes **OpEx** onto the §1.16 rails — **cash≡0 to the consumer**
(upkeep never billed, G2). DESIGN ONLY (:intent) — iriai plans, the executor acts under Council Lv7+.

## Gates (the charter inversions, structurally enforced — `methods/gates.cljc`)

- **G1** commons-map-not-shutoff-list · **G2** commons-not-a-market (cash≡0, give-only) ·
  **G3** steward-not-sovereign (advisory + 1 SBT=1 vote) · **G4** non-profit-rails-only ·
  **G5** assessment/sim-only-never-acts (:intent) · **G6** no-server-key · **G7** kotoba-EAVT ·
  **G8** synthetic-seed · **G9** maintenance-safety-floor (unsafe → corrective/decommission, never deferred).
- Strongest gates are **structural**: forbidden acts have no attribute (`gates/forbidden-absent?`
  proves the whole datom stream is clean, test-enforced). The negative space is declared in
  `kotoba/ontology.iriai.edn`.

## Files

```
methods/iriai_edn.cljc   seed loader + classify (regions + lifeline-cells)
methods/infra.cljc       SoS coverage/resilience gate → verdict → assess → datoms → report (+ bb CLI)
methods/fund.cljc        §1.16 in-kind funding proposal (cash≡0, give-only) → plan → datoms → report
methods/manage.cljc      1 SBT=1 vote governance + :intent + no-server-key → ledger → datoms → report
methods/twin.cljc        物理シミュレーション: per-asset degradation physics → condition/RUL/safety (+ project, bb CLI)
methods/maintain.cljc    運用メンテナンス: lifecycle gate (safety-floor first) + OpEx + executor routing
methods/forecast.cljc    予測保全: twin.project run-ahead → per-asset lead-time to next action → schedule (mitooshi 見通し)
methods/gates.cljc       constitutional assertions (ex-info, incl. G9 safety-floor) + structural forbidden-absent?
methods/kotoba.cljc      content-addressed append-only COMMONS LEDGER (tx-cid/make-tx/append-tx/verify-chain)
methods/kotoba_bridge.cljc LIVE-engine bridge — push local commit-DAG → kotoba :8077 (allowlist + :bridge/* cursor; dry-run default, fail-open)
methods/identity.cljc     self-certifying did:key (Ed25519, present-only) — actor self-gen key → self-mint own graph + self-certify did.json; seed sealed (no-server-key)
methods/social.cljc      self-publication membrane — commons coverage/§1.16-funding/upkeep → DRY-RUN posts (G1 commons-map / G2 cash-zero / G5 sim-only / no-server-key)
cells/social_post/       state-machine membrane (refuse <2 sources / server-key / published / shut-off vocab)
methods/autorun.cljc     deterministic, idempotent-by-content heartbeat — infra+fund+manage+twin+maintain+forecast → append; --bridge pushes live
methods/test_*.cljc      13 suites: infra·fund·manage·twin·maintain·forecast·gates·kotoba·bridge·social·identity·autorun·cell (79 tests / 512 assert)
cell.cljc                fleet heartbeat cell — `fire` runs one commons beat (IriaiCommonsHeartbeatCell)
deploy/                  LaunchAgent residency (install.clj + plist template + README; machine-local)
kotoba/ontology.iriai.edn EAVT schema + verdicts + instruments + degradation-models + NEGATIVE SPACE
kotoba/seed.edn          6 regions × 4 lifelines = 24 cells + 11 deployed assets (5 lifelines incl. road)
data/ (gitignored)       generated commons ledger — never committed
manifest.edn             gates G1–G8 + non-goals N1–N5 + composes the producer actors
run_tests.clj            bb-native runner (no shell, ADR-2606072802)
```

## Run

```bash
bb 20-actors/iriai/run_tests.clj                                  # 13 suites (79 tests / 512 assert)
bb --classpath 20-actors 20-actors/iriai/methods/infra.cljc       # coverage + resilience map
bb --classpath 20-actors 20-actors/iriai/methods/fund.cljc        # §1.16 in-kind funding plan
bb --classpath 20-actors 20-actors/iriai/methods/manage.cljc      # 1 SBT=1 vote governance ledger
bb --classpath 20-actors 20-actors/iriai/methods/twin.cljc        # physical-simulation asset condition
bb --classpath 20-actors 20-actors/iriai/methods/maintain.cljc    # operations/maintenance plan
bb --classpath 20-actors 20-actors/iriai/methods/forecast.cljc    # predictive-maintenance schedule
bb --classpath 20-actors 20-actors/iriai/methods/autorun.cljc     # heartbeat → append (all 6 layers)
bb --classpath 20-actors 20-actors/iriai/methods/autorun.cljc <seed> <log> --bridge  # + push to live kotoba :8077 (dry-run unless IRIAI_KOTOBA_LIVE=1)
```

## Live-engine bridge (ADR-2606280900, ibuki-R3/kaname pattern)

`methods/kotoba_bridge.cljc` pushes each local commit-DAG tx to the LIVE kotoba engine
(`com.etzhayyim.apps.kotoba.datomic.transact`): host allowlist (loopback + EVO-X2 LAN), `:iriai.tx/*`
provenance, `:bridge/*` exactly-once cursor, `expected_parent` chaining. **DRY-RUN by default**
(`IRIAI_KOTOBA_LIVE=1` or `:live true` for live), **fail-open** (engine down / boundary → `:error`,
the local beat still completes). `autorun --bridge` wires it after persist. Live push is the documented
**G7 operator step** (`IRIAI_KOTOBA_OPERATOR_DID` = the node's public operator DID); no-server-key —
the member CACAO leash stays present-only. SIM/assessment-only is unaffected (ships the map/plan, never
actuates).

## Self-key & autonomous registration (`methods/identity.cljc`, ADR-2606280900)

iriai **self-generates its OWN Ed25519 `did:key`** (kaname/tsubasa pattern) — the seed is sealed in
Keychain/1Password (`etzhayyim.iriai`), used **present-only** (signs, never exfiltrated), never committed.
With this key the actor can act autonomously at three different levels — and "no-server-key" means the
opposite of "can't act": it means *the actor holds its own key, the server holds none*.

| act | autonomous? | why |
|---|---|---|
| fetch public data · persist to local kotoba log · **self-mint to its OWN graph** | ✅ no key / own key | depth-1 self-mint is structurally authorized — the key-derived IPNS name **is** the actor's graph (no owner hand-off, no shared token); read-only is exempt (ADR-2606072802) |
| **self-sign / self-register** its did.json CID (`attest-did-doc`) | ✅ own key, present-only | self-certifying did:key (seed sealed, no-server-key) |
| **outward AT-proto broadcast** (assert `:published` to a 3rd-party public network) | ❌ Council Lv6+ gate | §1.12 outward-action + ADR-2606272355 — a charter gate on outward FORCE/voice, **not** a key-custody gate; holds even with the self-key |

So iriai *can* autonomously register + sign to its own graph; only the outward public broadcast is
Council-gated. `gen-keypair`/`sign`/`verify`/`did-key`/`attest-did-doc`; base58btc inlined (dep-free,
portable to the kototama actor-runtime subset). `build-live` social broadcast still raises (G6/§1.12).

## Pairs with

- **producers**: hikari 光 (電気) · mizuho 水穂 (水道) · kamado 竈 (ガス) · noroshi 烽 (通信) ·
  infra-utility-connect (connection) · kuni-umi (production robotics, infra-robotics 3-layer)
- **funding**: tanemaki 種蒔き (Public Fund steward) · fuchi 扶持 (in-kind sustenance) ·
  TitheRouter / PublicFundGovernance · Displacement Dividend · §1.16 social security
- **SoS pattern**: kaname 要 (leverage synthesizer) · amime 網目 (energy N-1 mesh)
- Authorized by **ADR-2606272200**. Live production + actuation = producer actors under Council Lv7+, never iriai.

## Fleet residency (ADR-2606280900)

`IriaiCommonsHeartbeatCell` is registered in `50-infra/cluster/murakumo/cell-runner/cells.edn`
(node **judah**, cron **44 * * * ***, healthz **13093**) and runs one deterministic,
idempotent-by-content beat per fire (infra+fund+manage+twin+maintain → local commons ledger).
Local LaunchAgent residency: `bb 20-actors/iriai/deploy/install.clj install` (hourly :44).
No-server-key, no external I/O; Murakumo digest + live-engine bridge + crew dispatch stay
operator/Council-gated.

## R0 → later

- **R1+ (G7-gated)**: real region/utility-coverage ingest from public open data (World Bank / IEA /
  WHO-JMP / ITU — read-only, no key); real asset-condition telemetry (kizashi sensing); inochi/jinushi
  land-sovereignty grounding for consent; amime N-1 energy-mesh join; predictive maintenance via
  mitooshi; producer-twin device-in-the-loop coupling. **R2**: Murakumo-narrated commons digest; live
  kotoba-engine bridge (ibuki-R3); road coverage already landed; lexicon JSON deploy. Live actuation +
  crew dispatch stays the producer/executor actors' under Council Lv7+, never iriai.
