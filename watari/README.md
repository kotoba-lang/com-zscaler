# watari 渡り

> **World live moving-craft (ship + aircraft) knowledge graph.** Tier-B actor · R0 design-only · ADR-2606041827.
> DID: `did:web:etzhayyim.com:actor:watari`

watari ingests the **live positions of public, transponder-broadcasting craft** — ships
(AIS) and aircraft (ADS-B) — into the **kotoba Datom log** as an append-only *as-of*
trajectory, and surfaces aggregate **sea-lane / air-corridor / chokepoint / approach**
concentration routed to safety, collision-avoidance, congestion-easing, and resilience.

It is the **kotoba-native successor** to the legacy `maps` (`aismarine` + `aircraft_live`,
RisingWave) and `vessel` (`tracking:ais`, graph.write SQL) pipelines, which violated the
substrate boundary (ADR-2605262130). See
[`MIGRATION-NOTES.md`](../../00-contracts/lexicons/com/etzhayyim/watari/MIGRATION-NOTES.md).

**A situational-awareness map — never a person-surveillance feed, never a target-list.**
A craft is a craft, not a person (G4).

## 渡り — the name

渡り鳥 (migratory birds → aircraft) + 渡し・渡海 (ferry crossing → ships): the kami of
sea-and-sky passage. Lineage with **watatsumi 綿津見** (sea body) and **watatsuna 綿津綱**
(sea cables) — the 海/空 path trilogy.

## Run

```bash
# analyze the bounded :representative seed → situational report + derived datoms
python3 methods/analyze.py
#   → out/intel-report.md  +  out/movement-situation.kotoba.edn

# normalize a public AIS/ADS-B batch (offline; live fetch is G7-gated)
python3 methods/ingest.py --batch data/ingest/sample-batch.json
```

## What it computes (aggregate-first)

- **Chokepoint transit** — distinct craft transiting each maritime chokepoint *now*.
  Composes with watatsuna's static cable load over the **same** chokepoint keywords.
- **Lane / corridor load** — live concentration per sea-lane / air-corridor, split vessel / aircraft.
- **Approach congestion** — craft holding in a port / airport approach.
- **Current position snapshot** — the latest as-of fix per craft.
- **Freshness tail** — craft NOT seen in the latest wave (honest gaps; no fabricated live coverage).

## Layout

```
20-actors/watari/
├── CLAUDE.md          # actor-local rules (read repo-root CLAUDE.md first)
├── manifest.jsonld    # DID, cells, lexicons, 9 gates, 6 non-goals
├── data/
│   └── seed-craft-graph.kotoba.edn   # bounded :representative seed (13 craft, 26 fixes, 9 lanes)
├── methods/
│   ├── analyze.py     # situational analyzer (stdlib)
│   └── ingest.py      # public AIS/ADS-B normalizer (R0; live fetch G7-gated)
└── out/               # generated: intel-report.md + movement-situation.kotoba.edn
```

## Gates (manifest.jsonld for full text)

G1 public broadcasts only · **G2 situational, not surveillance/targeting** · G3 aggregate-first ·
**G4 no person-tracking / no pattern-of-life** · G5 sourcing honesty (no fabricated coverage) ·
G6 Murakumo-only · G7 outward-gated live ingest · G8 no git-lfs · G9 no PII.

## Honest R0

Design + data-model + analyzer only. Bounded `:representative` seed (rounded coords,
illustrative timestamps — NOT a live capture). Live AIS (AISStream) + ADS-B (OpenSky /
adsb.fi) ingest is **Council + operator gated** (G7). Public transponder broadcasts only;
no person-tracking (G4). watari observes craft — it does not fly or sail any (N3).
