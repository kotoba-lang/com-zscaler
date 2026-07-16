# torifune 鳥船 — zero-net-carbon open launch-vehicle manufacturing + Transparent space access

**ADR**: 2606162355 · **depends**: 2605192100 (Mission Charter — §1.12 Transparent Force) +
2605192330 (orbital land sovereignty) + 2606073600 (hoshimori — orbital stewardship, the
observe sibling) + 2606013400 (funadaiku — zero-emission shipbuilding, the build-pattern) +
2606032130 (Displacement Dividend) + 2605312345 (Datom = canonical state) + 2605215000
(Murakumo-only). **Status**: 🟡 R0+R1 (design + offline sim; live legs Council-gated).

torifune ("鳥船" — from 天鳥船 Ame-no-Torifune, the Shinto heavenly bird-boat that flies to
heaven) is the **船大工 of the sky**: the `funadaiku` / `sarutahiko` / `giemon-factory`
build-pattern pointed at **space access**. It designs and (Council-gated) manufactures a
**reusable, open-design launch vehicle** (the **Ama 天 class**, two-stage hydrolox), runs the
**plant** (grand-block / 4D-BIM) and the **ascent + staging + recovery GNC simulation** on
`kami-genesis` (clean-room `isaacsim.core.api`, no NVIDIA binary — the same Featherstone engine
`funadaiku` ShipHydro and `niyaku` Cartpole use). It **builds the bus `subaru` operates**, and
its disposal plans are an input to `hoshimori` stewardship.

This is the charter-clean inversion of SpaceX: **open-design, zero-net-carbon, weapon-
unrepresentable, debris-responsible, dividend-coupled, every live leg Council-gated.**

## Hard gates (constitutional — read before any change)

- **G1 — civilian launch ONLY, NEVER a weapon-delivery / ballistic-strike vehicle.** This is
  the defining inversion, load-bearing because a launch vehicle is — modulo payload — a
  ballistic missile. Weaponizable flight profiles (depressed / suborbital-strike trajectories,
  MIRV / post-boost-vehicle deployment buses, kinetic re-entry-vehicle delivery, fractional-
  orbital bombardment) are **structurally unrepresentable**: the trajectory `:traj/class` enum
  admits only `{ :ascent :orbit-insertion :rendezvous :deorbit }` and the payload
  `:payload/class` enum has **no munition / kinetic member**. Payloads are restricted to
  civilian classes. A dedicated test (`test_g1_no_strike_profile`) asserts no strike-trajectory
  / munition-payload attribute exists (Charter §1.12 Transparent-Force + Rider §2(a)).
- **G2 — zero-net-carbon propellant only.** Primary = green-H₂ hydrolox (LH₂/LOX from renewable
  electrolysis, `hydrogen_electrolysis`); permitted = `kamado`-synthetic methalox at net≤0
  closed-carbon. Fossil-derived and toxic-hypergolic (UDMH/N₂O₄) propellants are representable
  only as **disfavored**; carbon-balance is **measured** (Rider §2(d)), never assumed.
- **G3 — open-design + dividend-coupled.** Open-source vehicle + GNC + plant (robotics-actor
  default); automated space-access labor frees workers → Displacement Dividend (ADR-2606032130).
- **G4 — Transparent space access.** Open-source + on-chain flight/ops log + 1 SBT = 1 vote
  (§1.12). Never covert / proprietary / state-military-aligned.
- **G5 — debris-responsibility (couples hoshimori).** Mandatory per-mission disposal / deorbit
  plan; no intentional debris; stage recovery preferred; the plan feeds `hoshimori`'s congestion
  stewardship — torifune may not create the congestion hoshimori routes around.
- **G6 — no-server-key.** The simulation is dry-run; actual launch operation is Council +
  operator-DID gated. R0 = sim + plant model + ontology only.
- **G7 — Murakumo-only narration** (ADR-2605215000).
- **G8 — sourcing honesty.** Every record `:authoritative | :representative`; sim numbers are
  representative engineering estimates, never measured flight data, until a Council-gated flight
  campaign exists.

## Layout

```
20-actors/torifune/
├── CLAUDE.md                          # this file
├── manifest.jsonld                    # actor manifest (4 cells, 8 gates)
├── data/                              # R1
│   └── seed-ama-vehicle.kotoba.edn    # Ama-class engineering seed (stages/engines/propellant)
├── methods/                           # R1 — pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── ascent_sim.py                  # kami-genesis ascent/staging/recovery GNC sim
│   ├── carbon_balance.py             # zero-net-carbon propellant accounting (G2)
│   ├── disposal_plan.py              # per-mission debris-responsibility plan (G5)
│   └── datom_emit.py                  # kotoba Datom-log (EAVT) emitter — canonical state
├── tests/                             # R1 (incl. G1 no-strike-profile assertion)
├── wasm/                              # R1 — componentize-py design
└── out/                               # GENERATED — do not hand-edit
```

## Run (R1)

```bash
cd 20-actors/torifune
bb -cp 20-actors -m torifune.methods.ascent-sim        # → out/ascent-report.md
bb -cp 20-actors -m torifune.methods.carbon-balance    # → out/carbon-report.md
bb -cp 20-actors -m torifune.methods.disposal-plan     # → out/disposal-plan.kotoba.edn
bb -cp 20-actors -m torifune.methods.datom-emit        # → out/launch-datoms.kotoba.edn (EAVT)
```

## Cross-links

torifune is the **build** leg of the off-Earth chain: **torifune builds + (gated) launches →
subaru operates the constellation → hoshimori observes → stewardship**. Build-pattern siblings:
`funadaiku` (ships), `sarutahiko` (trucks), `giemon-factory` (4D-BIM plant). Energy siblings:
`hydrogen_electrolysis` (green-H₂), `kamado` (synthetic methalox at net≤0). The G1 weapon-
unrepresentability mirrors `tazuna` ("weaponizable unrepresentable") and `hoshimori` G1
("never a targeting aid").
