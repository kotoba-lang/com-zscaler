# iriai 入会 — global lifeline-commons (電気 / 水道 / ガス / 通信)

The non-profit operator of the four lifelines (ライフライン) **as one commons**. 入会 (iriai) =
the traditional Japanese commons — collectively-held rights of use over a shared resource. The
lifelines are held as a commons right of use (**入会権**), delivered **§1.16 social-security
in-kind** (cash ≡ 0), governed **1 SBT = 1 vote**. The charter-clean inversion of the for-profit
utility — and of utility-as-coercion (a lifeline is never withheld as leverage).

iriai is the **System-of-Systems umbrella** over the producers — 電気→**hikari 光** · 水道→**mizuho
水穂** · ガス→**kamado 竈** · 通信→**noroshi 烽** — covering **infra + 資金 (funding) + 管理
(management)** in one heartbeat. It **never produces and never actuates** a lifeline
(ASSESSMENT + R0 DESIGN ONLY).

`did:web:etzhayyim.com:iriai` · `com.etzhayyim.iriai.*` · **ADR-2606272200 + 2606280900** · clj-native R0
(79 tests / 512 assertions green).

## Five layers

| layer | what | output |
|---|---|---|
| **infra** | edge-primary commons-gap `(1−coverage)·essentiality·vulnerability` + resilience (SPOF / N-1) per region × lifeline | verdict ∈ `{await-consent provision reinforce redundancy maintain monitor}` — a coverage map, **never a shut-off list** |
| **資金 fund** | each provision/reinforce/redundancy → §1.16 in-kind proposal (donation→tithe→Public Fund→grant/escrow/in-kind) | **cash ≡ 0** to the consumer; imputed market-equivalent value (transparency-only); advisory, decided 1 SBT = 1 vote |
| **管理 manage** | governance envelope: 1 SBT=1 vote (20%/50%/48h) + Council Lv6+/Lv7+; actuation-class **:intent**; no-server-key | a commons governed by its members, never a sovereign operator |
| **物理 twin** | per-asset REAL degradation physics (transformer IEEE C57.91 · pipe Hazen-Williams · gas corrosion→leak · fibre attenuation · road PCI+bridge) | condition (0..1) + RUL + operating margin + structural safety; `project` run-ahead; SIM ONLY |
| **運用 maintain** | twin → maintenance lifecycle (**safety-floor first**) + executor routing + OpEx | verdict ∈ `{decommission renew corrective-repair refurbish preventive-service inspect ok}`; §1.16 OpEx, cash≡0; :intent |

Lifelines: 電気→hikari · 水道→mizuho · ガス→kamado · 通信→noroshi · **道路→tatekata** (road, ADR-2606280900).

## Gates

**G1** commons-map-not-shutoff-list · **G2** commons-not-a-market (cash≡0, give-only) ·
**G3** steward-not-sovereign · **G4** non-profit-rails-only · **G5** assessment/sim-only (:intent) ·
**G6** no-server-key · **G7** kotoba-EAVT · **G8** synthetic-seed · **G9** maintenance-safety-floor
(unsafe → corrective/decommission, never deferred for cost). The strongest gates are *structural* —
the forbidden acts (shutoff / tariff / actuate / dispatch-crew / consumer-bill / self-fund) have no
attribute to express them, proven by `gates/forbidden-absent?` over the whole datom stream.

## Run

```bash
bb 20-actors/iriai/run_tests.clj                              # all suites
bb --classpath 20-actors 20-actors/iriai/methods/infra.cljc   # coverage + resilience map
bb --classpath 20-actors 20-actors/iriai/methods/fund.cljc    # §1.16 in-kind funding plan
bb --classpath 20-actors 20-actors/iriai/methods/manage.cljc  # 1 SBT=1 vote governance ledger
bb --classpath 20-actors 20-actors/iriai/methods/twin.cljc    # physical-simulation asset condition
bb --classpath 20-actors 20-actors/iriai/methods/maintain.cljc # operations/maintenance plan
bb --classpath 20-actors 20-actors/iriai/methods/autorun.cljc # heartbeat → commons ledger (5 layers)
```

Apache 2.0 + etzhayyim Charter Compliance Rider v3.5.
