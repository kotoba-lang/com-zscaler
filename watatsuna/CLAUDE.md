# watatsuna 綿津綱 — agent reference

> World submarine-cable network knowledge graph. Tier-B, R0 design-only. ADR-2606012600.
> Read the repo-root `CLAUDE.md` first; this file only adds actor-local rules.

## Identity

- **DID**: `did:web:etzhayyim.com:actor:watatsuna` (resolvable via INFRA_ACTORS).
- **Glyph**: 綿津綱 — "the sea-god's ropes" (= submarine cables). Deliberate sibling of
  watatsumi 綿津見 (same `綿津` root).
- **Role**: the *observation / knowledge-graph* face. watatsumi 綿津見 is the *operational /
  敷設 robotics* face. watatsuna knows where the network is fragile; watatsumi's cable-laying
  fleet acts to lay/repair/monitor — **never to cut** (watatsumi N8).

## Hard rules (constitutional — do not weaken)

1. **Resilience, not interdiction (G2).** Every analysis output is framed toward redundancy,
   diverse routing, and faster repair. Never emit a "where to cut" / target-list framing.
   This mirrors `watatsumi` **N8** and Charter Rider **§2(d)** (infrastructure attack).
2. **Public infrastructure only (G1).** Ingest only public-record data. **Forbidden inputs**:
   classified/military cable routes, precise armoring/burial depth charts, live repair-vessel
   position beyond public AIS, anything that would aid interdiction.
3. **No intent adjudication (G4).** `:cable.fault/kind` records only the *public bulletin's
   own* classification (e.g. `:under-investigation`). watatsuna never asserts sabotage —
   that is a state matter.
4. **Sourcing honesty (G5).** Every node/edge carries `:*/sourcing` ∈
   `:authoritative | :representative | :synthesized`. No fabricated coverage. Absence ≠
   non-existence — it means "not yet ingested".
5. **kotoba-native (substrate boundary).** State = kotoba Datom log. No SQL / RisingWave /
   Lance as canonical store. Read path = kotoba-kqe arrangements over the Datom log.
6. **No git-lfs (G8).** Route GeoJSON / satellite tiles → DataLad → IPFS under
   `80-data/submarine-cable`.
7. **Murakumo-only (G6).** Any LLM narration routes through the Murakumo fleet
   (LiteLLM `127.0.0.1:4000`), never a commercial GPU path (ADR-2605215000).
8. **Outward-gated (G7).** Live planet-scale ingest (TeleGeography feed, cable-ship AIS,
   live fault bulletins) requires Council + operator. R0 ships a bounded seed only.

## Vocabulary

`00-contracts/schemas/submarine-cable-ontology.kotoba.edn`:
- `:cable/*` — a cable system (id, name, owner-consortium, length, design/lit capacity, RFS, status).
- `:station/*` — a landing station (id, name, country, lat/lon, **`:chokepoint`** tags).
- `:cable.link/*` — first-class edge: cable ⇄ station incidence.
- `:cable.seg/*` — first-class edge: station ⇄ station segment, with **`:traverses`** chokepoints.
- `:cable.fault/*` — observed public fault bulletin (as-of history; appended, never overwritten — 非終末論).
- derived (`:resilience/*`) — chokepoint-load, station-degree/-capacity, cable-diversity,
  redundancy-gap. Computed by `analyze.py`, flagged `:derived`, **never re-ingested as fact**.

## Cells

- `cell:watatsuna.analyze` → `methods/analyze.py` (stdlib only). Pipeline:
  classify → cable⇄station incidence → station degree/capacity → chokepoint-load →
  cable-diversity → redundancy-gap. Aggregate-first. Idempotent; rerun to regenerate `out/`.
- `cell:watatsuna.autorun` → `methods/autorun.py` (+ `methods/kotoba.py`). The autonomous
  Murakumo-fleet heartbeat — the same shape shionome/ipaddress/yabai/sukashi use. Each cycle
  observes the OFFLINE merged graph → classify → analyze → **persists a content-addressed
  transaction** (graph datoms + derived `:resilience/*`) to the append-only **local** kotoba Datom
  log (`methods/kotoba.py`), linking the previous tx's CID into a verifiable commit-DAG.
  Deterministic / resume-safe; NO external I/O. **G2 holds by construction**: only resilience
  framing (chokepoint-load / redundancy-gap) is representable — no interdiction/target attr.
  Fleet cells `watatsuna_cable_ingest` (cron 23) + `watatsuna_resilience_weave` (cron 28) +
  `watatsuna_resilience_persist` (cron 33) on `simeon` — see `50-infra/murakumo/fleet.toml`.
  Live TeleGeography/AIS/fault-bulletin ingest + the live-node push stay Council + operator gated
  (G7). Invariants guarded by `methods/test_autorun.py` (commit-DAG verify, tamper-detect,
  determinism, append-only, derived-flagging, **G2 resilience-not-interdiction**, no-external-I/O).

  ```bash
  python3 methods/autorun.py --cycles 3 --fresh   # AUTONOMOUS heartbeat → LOCAL kotoba Datom log
  ```

## Lexicons (kotoba-native)

`com.etzhayyim.cable.{registerCableSystem,registerLandingStation,registerSegment,flagCableFault}`
— supersede the legacy `etzhayyim` telecom / telecomInfra / cableRepairFleet lexicons. Mapping +
inventory: `00-contracts/lexicons/com/etzhayyim/cable/MIGRATION-NOTES.md`.

## Pairing with watatsumi (敷設 robotics)

watatsumi's cable-laying robotics fleet (ADR-2606012600 §robotics; `watatsumi/data/cable-laying-fleet.kotoba.edn`)
is *planned off* watatsuna's redundancy-gap + chokepoint output: lay diverse routes where
watatsuna shows fragility, pre-stage repair where chokepoint-load is high. The robotics act
ONLY to lay / bury / splice / repair / monitor. Cutting/interdiction = watatsumi N8, hard prohibited.
