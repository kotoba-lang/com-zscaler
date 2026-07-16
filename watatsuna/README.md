# watatsuna 綿津綱 — World Submarine-Cable Network Knowledge Graph

**Tier-B actor · R0 design-only · ADR-2606012600**

watatsuna 綿津綱 (*"the sea-god's ropes"*) datafies the planet's submarine cable
network — cable **systems**, their **landing stations**, the **segments** that join
them, and observed **fault** bulletins — into the kotoba Datom log, and surfaces where
the world's capacity concentrates onto a maritime chokepoint or single point of failure.

It is the **observation / knowledge-graph** counterpart to **watatsumi 綿津見** (the
民生 submersible + cable-laying robotics actor). Same root (`綿津`), two faces:

| | actor | role |
|---|---|---|
| 綿津**綱** | **watatsuna** (this) | *knows* the network — where it's fragile |
| 綿津**見** | **watatsumi** | *acts* on the network — lays / repairs / monitors (never cuts; N8) |

watatsuna sits in the **observation upper layer** alongside `tsumugi` (産霊 power graph),
`danjo` (public accountability), `kanae` (fiscal flows), `tadori` (on-chain tracing).

## The constitutional frame (read first)

The output ranks chokepoints **by fragility so the network can be made more robust** —
add diverse routes, pre-stage repair ships, accelerate restoration. It is a **resilience
map, never a target-list.**

- **G2 resilience-not-interdiction** — mirrors `watatsumi` **N8** (no cable cutting /
  sabotage / interdiction) + Charter Rider **§2(d)** (infrastructure attack prohibited).
- **G1 public-only** — cable owners, landing points, capacity, RFS year are matters of
  public record (TeleGeography, consortium RFS notices, regulatory filings, press).
  Classified military routes, precise armoring depth, and live repair-vessel position
  beyond public AIS are **out of scope and must not be ingested.**
- **G4 no intent adjudication** — a fault's `:kind` mirrors only the *public bulletin's
  own* classification. Sabotage is a state matter; watatsuna never asserts it.

## Substrate

- **State**: kotoba Datom log (ADR-2605312345) — IPFS block backend, MST ingress, Base L2 anchor. No SQL, no RisingWave.
- **Vocabulary**: [`00-contracts/schemas/submarine-cable-ontology.kotoba.edn`](../../00-contracts/schemas/submarine-cable-ontology.kotoba.edn)
- **Large geo assets** (route GeoJSON, satellite tiles): DataLad → IPFS under `80-data/submarine-cable` (**no git-lfs**, G8).
- **Inference / narration**: Murakumo-only (ADR-2605215000).

## Layout

```
20-actors/watatsuna/
├── manifest.jsonld                         # DID, cells, gates, watatsumi pairing
├── README.md                               # this file
├── CLAUDE.md                               # agent reference
├── data/
│   ├── seed-cable-graph.kotoba.edn         # 14 cables · 22 stations · 43 links · 11 segments · 2 faults (:representative)
│   ├── cable-graph.merged.kotoba.edn       # GENERATED: seed + ingest bridge (dedup)
│   └── ingest/
│       ├── telegeography-sample.json       # public-dataset-shaped sample input (submarinecablemap/TeleGeography)
│       └── telegeography-bridge.kotoba.edn # GENERATED: bridged → kotoba EAVT
├── methods/
│   ├── ingest.py                           # R1 — public dataset → kotoba EAVT bridge (offline default; live G7-gated)
│   ├── analyze.py                          # resilience analyzer (stdlib only)
│   └── plan.py                             # R2 — resilience → watatsumi cable-laying mission plan (N8/G2-bound)
├── viz/
│   ├── _template.htm, _globe_template.htm  # viewer templates (data token)
│   ├── build_viz_data.py                   # R1/R2 — analyzer → payload + self-contained 2D map + 3D globe
│   ├── cable-resilience.json               # GENERATED: viz payload (kami-engine consumable)
│   ├── cable-resilience.htm                # GENERATED: 2D map (data inlined, no build step)
│   └── cable-globe.htm                     # GENERATED: 3D orthographic globe (drag-rotate, auto-spin)
└── out/
    ├── intel-report.md                     # aggregate-first resilience report
    ├── cable-criticality.kotoba.edn        # derived datoms (:derived; not re-ingested)
    ├── resilience-plan.md                  # R2 — watatsumi fleet plan (human)
    └── resilience-plan.kotoba.edn          # R2 — :plan/* recommendations (consumed by watatsumi)
```

## Run

```bash
cd 20-actors/watatsuna
python3 methods/ingest.py             # R1: bridge data/ingest/*.json + seed → data/cable-graph.merged.kotoba.edn
python3 methods/analyze.py data/cable-graph.merged.kotoba.edn --out out   # → resilience report + derived datoms
python3 methods/plan.py   data/cable-graph.merged.kotoba.edn --out out    # R2: → watatsumi fleet plan (N8-bound)
python3 viz/build_viz_data.py         # → viz/cable-resilience.htm + viz/cable-globe.htm  (open in a browser)
```

`python3 methods/analyze.py` with no argument runs the **seed** graph alone (no ingest needed).

### Result (seed alone → +ingest merged)

| | seed | + TeleGeography bridge (merged) |
|---|---:|---:|
| cable systems | 14 | **18** (+Echo, Apricot, SEA-ME-WE 6, JGA-S) |
| landing stations | 22 | **26** |
| total design capacity | 1748 Tbps | **2234 Tbps** |

- Top chokepoints (merged) by dependent capacity: **Malacca 940 Tbps** · **Luzon Strait 681** ·
  **Suez/Red Sea 350** · **Gibraltar 324** · **South China Sea 191** — the same vulnerable
  straits the industry already watches.
- Most brittle systems (single charted chokepoint): Bifrost, PLCN, Equiano, JUPITER, FASTER.
- Single-cable landing stations flagged → routed to redundancy.

### Fleet planning (R2 — watatsuna knows → watatsumi acts)

`methods/plan.py` turns the resilience analysis into a **watatsumi cable-laying mission
plan** (`out/resilience-plan.{md,kotoba.edn}`), tasking specific robot classes:

- **lay-diverse-route** — for single-cable landing stations (close redundancy gaps): `hibiki`
  survey → `tsuna-suki`/`horinuki` lay+bury → `tsugite` splice → `funamori` cable-ship.
- **pre-stage-repair** — for the top chokepoint-load straits (Malacca, Luzon, Suez, Gibraltar):
  `tedori` (REPAIR-ONLY recovery) + `tsugite` + `funamori`.
- **monitor** — for brittle single-chokepoint cables: `kikimimi` DAS watch.

Every recommendation is **redundancy / repair / monitor only** — there is **no interdiction
output by construction** (G2 + watatsumi N8). First run: 20 recommendations (9 lay-route /
4 pre-stage-repair / 7 monitor).

### Visualization

Two **self-contained** viewers (data inlined — no build step, no external fetch, open via
`file://`), cross-linked:
- `viz/cable-resilience.htm` — 2D equirectangular world map.
- `viz/cable-globe.htm` — 3D orthographic globe (drag to rotate, auto-spins when idle).

Both: stations sized by landed capacity + coloured by chokepoint; great-circle segment arcs;
ranked chokepoint-load panel; **click a station → kotoba object data**. A **resilience map**
(where to add redundancy), never a target-list (G2). The **kami-engine WASM globe** (shared
renderer with kanae/shibuya, ADR-2605302300) remains the deferred integration —
`cable-resilience.json` is the data contract it would consume.

## Lexicons (kotoba-native, supersede legacy `etzhayyim`)

`com.etzhayyim.cable.*` — `registerCableSystem` / `registerLandingStation` /
`registerSegment` / `flagCableFault`. These replace the legacy `etzhayyim` telecom / telecomInfra /
cableRepairFleet lexicons; see the inventory + mapping in
[`00-contracts/lexicons/com/etzhayyim/cable/MIGRATION-NOTES.md`](../../00-contracts/lexicons/com/etzhayyim/cable/MIGRATION-NOTES.md).

## Display on etzhayyim.com

Registered in `INFRA_ACTORS` (`50-infra/etzhayyim-did-web/src/registry/infra-actors.ts`)
→ resolvable as **`did:web:etzhayyim.com:actor:watatsuna`** at
`https://etzhayyim.com/actor/watatsuna/did.json`.

## Honesty (R0)

Bounded illustrative seed — **not** exhaustive coverage. Coordinates are rounded to the
landing town. Capacities/lengths are public design figures (`:representative`); uncertain
values are flagged `:synthesized`. Chokepoint dependency is charted only for seeded
segments. Live planet-scale ingest is **G7** Council + operator gated.

## Roadmap

- **R0**: schema + actor scaffold + runnable seed analyzer + kotoba-native lexicons. ✅
- **R1** (this increment): **TeleGeography-bridge ingest cell** (`methods/ingest.py`, offline
  default, live fetch G7-gated) → kotoba EAVT merged graph; **resilience visualization**
  (`viz/`, self-contained canvas map; kami-engine WASM 3D globe deferred). ✅
- **R2** (this increment): **watatsumi fleet planner** (`methods/plan.py` → `out/resilience-plan.*`,
  N8/G2-bound) + **3D resilience globe** (`viz/cable-globe.htm`). ✅
- **R3** (post-Council): live public fault-bulletin + cable-ship AIS ingest (public sources
  only, operator-gated); **kami-engine WASM** 3D globe integration (replace the canvas globe with
  the kanae/shibuya shared renderer); live watatsumi fleet tasking under Council + operator.
