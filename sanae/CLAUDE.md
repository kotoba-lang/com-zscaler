# sanae (早苗) — field-agriculture robotics actor

**DID**: `did:web:etzhayyim.com:actor:sanae` · **Tier**: B · **Status**: R0 · **ADR**: 2606032100 (+ 2606032130 coupling)

## What this is

The **robotics body** of mitsuho's L2-Sustenance mandate, and **LPS #1** in the labour-liberation
robotics-actor wave (ADR-2606032100). Automates the single largest pool of human toil on Earth —
field agriculture (sow / weed / harvest): **ISIC A01 · ISCO 6111/6112/6113/9211 · UNSPSC 70/10/11**.
Mechanical/biological only, herbicide-free, seed-sovereign, regenerative.

## Fleet (design, open-source, `:representative`)

早乙女 Saotome (seed) · 草薙 Kusanagi (weed; mechanical+laser, NO herbicide) · 稲架 Hasa (harvest+thresh) ·
案山子 Kakashi (survey drone) · 田螺 Tanishi (soil probe).

## Cells (langgraph→WASM; `.solve()` raises at R0)

field_preparation · precision_seeding · **autonomous_weeding** (coded reference cell) ·
harvest_coordination · soil_regeneration_audit. Murakumo-only inference (gemma3:4b @127.0.0.1:4000).

## Gates (immutable R0→R3)

G1 open-source firmware · **G2 displacement-dividend coupling** (no live displacement without the
displaced cohort registered for the tenure-weighted dividend, ADR-2606032130) · G3 witness quorum
(≥2 robot + ≥1 agronomist) · G4 Murakumo-only · G5 no-payroll/cash≡0 · G6 Wellbecoming · G7
outward-gated · G8 sourcing-honesty · **G9 regenerative-only** (no synthetic pesticide/herbicide;
net soil-carbon ≥0; seed sovereignty, no patented lines).

## Non-goals

N1 no military/weaponized · N2 no surveillance product · N3 no labour-intensification for external
throughput · N4 no industrial monoculture (>50 ha single-crop) · N5 no patented seeds.

## Build / test

```
python3 methods/test_labor_liberation.py      # LPS ranking (7/7)
cd cells && <pytest-shim> test_state_machines.py   # weeding state machine + G3/G9 (5/5)
bb --classpath 20-actors 20-actors/sanae/methods/labor_coverage.cljc   # build-priority worklist (LPS × actor roster)
```

`methods/labor_coverage.cljc` cross-references the LPS ranking against the actor roster
(`build-priority` / `coverage-gaps` / `coverage-report`) to surface the highest-priority toil
that still has **no liberating actor** — currently **hofuri (meat processing)** and **ama
(fishing/aquaculture)**. Charter-excluded sectors (LPS 0, e.g. MINING N1) are never build targets.

R0 = design + state-machines + `:representative` seed only. No hardware; field deployment is Council
Lv6+ + operator gated (G7) **and** dividend-coupled (G2).

## Do not

- Do not add a chemical-herbicide weeding method (G9 hard-fails in `autonomous_weeding/state_machine.py`).
- Do not call any cell's `.solve()` — R0 scaffolds raise `RuntimeError` by design.
- Do not introduce a patented/IP-restricted seed varietal (N5).
- Do not let a deployment displace field labour without a funded displacement cohort (G2 → ADR-2606032130).
