# watari 渡り — agent reference

> World live moving-craft (ship + aircraft) knowledge graph. Tier-B, R0 design-only. ADR-2606041827.
> Read the repo-root `CLAUDE.md` first; this file only adds actor-local rules.

## Identity

- **DID**: `did:web:etzhayyim.com:actor:watari` (resolvable via INFRA_ACTORS).
- **Glyph**: 渡り — "the crossing / the migration". 渡り鳥 (migratory birds → aircraft) +
  渡し・渡海 (ferry crossing → ships) in one word: the kami of sea-and-sky passage.
  Lineage with watatsumi 綿津見 (sea body) and watatsuna 綿津綱 (sea cables) — the 海/空 path trilogy.
- **Role**: the *live moving-craft observation face*. watari ingests the LIVE positions of
  public craft and records them as an append-only as-of trajectory; the latest fix IS the
  current position, the fix stream IS the trail (非終末論).

## Why watari exists (kotoba-native rewrite)

The legacy `maps` actor (`aismarine` + `aircraft_live` pipelines) stored vessel/aircraft
positions in **RisingWave** (`vertex_vessel_position` / `vertex_aircraft_state`), and the
legacy `vessel` actor wrote them via **graph.write SQL**. Both violate the substrate
boundary (ADR-2605262130: the kotoba Datom log is first-class canonical state; NO RisingWave /
SQL) and route narration off-Murakumo. watari supersedes both onto kotoba EAVT + Murakumo-only.
Mapping: `00-contracts/lexicons/com/etzhayyim/watari/MIGRATION-NOTES.md`.

## Hard rules (constitutional — do not weaken)

1. **Public transponder broadcasts only (G1).** AIS (ships) and ADS-B (aircraft) are open
   public signals. **Forbidden inputs**: non-broadcasting craft; military / blocked-from-display
   aircraft (FAA LADD, PIA); naval vessels not openly broadcasting; anything that aids
   de-anonymization.
2. **Situational-awareness, not surveillance / not targeting (G2).** Every output is
   aggregate-first (lane / corridor / chokepoint / approach density) framed toward safety,
   collision-avoidance, congestion-easing, and resilience. Never a "follow this craft"
   tool, never a targeting feed. Mirrors watatsuna **G2** + Charter Rider **§2(a)** (force
   separation) + **§2(d)** (infrastructure attack).
3. **No person-tracking / no pattern-of-life (G4 — the defining gate).** A craft is a craft,
   not a person. watari MUST NOT link a position track to a named individual, build
   pattern-of-life on a private-yacht / private-jet owner, crew, or passenger, or answer
   "where is person X". Private-owner identity → encrypted / excluded. This is the invariant
   the legacy `maps`/`vessel` surfaces never had.
4. **Sourcing honesty (G5).** Every node/fix carries `:*/sourcing` ∈
   `:authoritative | :representative | :synthesized`. **No fabricated live coverage** — a craft
   not seen in the latest wave is reported in the *freshness tail*, not silently shown as
   current. Absence ≠ non-existence — it means "not yet ingested".
5. **kotoba-native (substrate boundary).** State = kotoba Datom log. No SQL / RisingWave /
   Lance as canonical store. "Current position" = latest as-of `:craft.fix`; read path =
   kotoba-kqe arrangements (EAVT / AEVT) over the Datom log.
6. **Murakumo-only (G6).** Any LLM narration routes through the Murakumo fleet
   (LiteLLM `127.0.0.1:4000`), never a commercial GPU path (ADR-2605215000).
7. **Outward-gated (G7).** Live AISStream / OpenSky / adsb.fi ingest requires Council +
   operator. R0 ships a bounded `:representative` seed only.
8. **No git-lfs (G8).** Bulk position history / replay tiles → DataLad → IPFS under
   `80-data/moving-craft`.

## Vocabulary

`00-contracts/schemas/moving-craft-ontology.kotoba.edn`:
- `:craft/*` — a moving craft's stable identity (kind ∈ :vessel | :aircraft; MMSI/IMO for
  ships, ICAO24/registration for aircraft; operator = a company, never a person).
- `:craft.fix/*` — **first-class append-only position fix**. Latest per craft = current
  position; the set = the trajectory (非終末論). Carries `:source` ∈ `:ais | :adsb`.
- `:craft.leg/*` — observed/declared voyage (ship) or flight (aircraft); mirrors the public
  AIS-destination / schedule only (no intent adjudication, no forecasting).
- `:lane/*` — a sea-lane / air-corridor / chokepoint / approach; the density unit. Its
  `:lane/chokepoint` keyword is **shared with watatsuna `:station/chokepoint`**.
- derived (`:movement/*`) — lane-load, chokepoint-transit, approach-congestion,
  track-freshness. Computed by `analyze.py`, flagged `:derived`, **never re-ingested as fact**.

## Cells

- `cell:watari.analyze` → `methods/analyze.py` (stdlib only). Pipeline:
  classify → latest as-of fix per craft → lane/corridor load (by kind) → chokepoint transit →
  approach congestion → freshness tail. Aggregate-first. Idempotent; rerun to regenerate `out/`.
- `cell:watari.ingest` → `methods/ingest.py` (R0 stub) — AISStream/OpenSky public batch →
  normalize → dedup-merge. Live fetch G7-gated.
- `cell:watari.autorun` → `methods/autorun.py` (+ `methods/kotoba.py`). The autonomous
  Murakumo-fleet heartbeat — the same shape shionome/ipaddress/yabai/sukashi/watatsuna use. Each
  cycle observes the OFFLINE merged graph → classify → analyze → **persists a content-addressed
  transaction** (graph datoms + derived `:movement/*`) to the append-only **local** kotoba Datom
  log (`methods/kotoba.py`), linking the previous tx's CID into a verifiable commit-DAG.
  Deterministic / resume-safe; NO external I/O. **G2/G4 hold by construction**: only aggregate
  `:movement/*` lane/chokepoint/approach density is representable — no per-craft follow feed and
  no person/owner/passenger/crew attr (a craft is a craft, never a person). The chokepoint-transit
  output composes with watatsuna's static cable-load over the SAME chokepoint keywords (静↔動).
  Fleet cells `watari_craft_ingest` (cron 38) + `watari_situation_weave` (cron 43) +
  `watari_situation_persist` (cron 48) on `simeon` (co-located with watatsuna — the 海/空 path
  trilogy) — see `50-infra/murakumo/fleet.toml`. Live AIS/ADS-B ingest + the live-node push stay
  Council + operator gated (G7). Invariants guarded by `methods/test_autorun.py` (commit-DAG
  verify, tamper-detect, determinism, append-only, derived-flagging, **G4 no-person-tracking**,
  no-external-I/O).

  ```bash
  python3 methods/autorun.py --cycles 3 --fresh   # AUTONOMOUS heartbeat → LOCAL kotoba Datom log
  ```

## Lexicons (kotoba-native)

`com.etzhayyim.watari.{registerCraft, recordFix, recordLeg, registerLane}` — supersede the
legacy maps `aismarine`/`aircraft_live` (RisingWave) + vessel `tracking`/`vesselPosition`
(graph.write SQL) surfaces. Mapping + inventory:
`00-contracts/lexicons/com/etzhayyim/watari/MIGRATION-NOTES.md`.

## Pairing with watatsuna (静 ↔ 動 maritime resilience)

watatsuna 綿津綱 charts WHERE the submarine-cable network is fragile (static infrastructure);
watari adds WHO is moving through the same chokepoints right now (live craft). Because both
key on the SAME chokepoint keywords (`:malacca` `:luzon-strait` `:suez-red-sea` `:hormuz` …),
a chokepoint's *static cable dependence* (watatsuna) and its *live vessel transit*
(watari) compose into one maritime resilience picture — both routed to redundancy + faster
repair + safer routing, **never to interdiction**.

## Distinct from the craft-building / craft-control actors (N3)

`kami-autodrive` (GNC autopilot), `funadaiku` (zero-emission shipyard), `watatsumi`
(submersible + cable-laying robotics) BUILD or CONTROL craft. watari only OBSERVES the
real-world positions of craft that already exist. Observe ≠ control — watari flies and
sails nothing.
