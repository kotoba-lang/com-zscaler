# etzhayyim-project-maps

> **KOTOBA-NATIVE MIGRATION IN PROGRESS (ADR-2606064500).** Everything below this banner
> describes the **legacy RisingWave/Hyperdrive** implementation, which is a substrate-boundary
> violation (ADR-2605262130: the kotoba Datom log is first-class canonical state; NO RisingWave).
> The migration off SQL is landing in phases — the moving-craft slice already moved to
> **watari** (ADR-2606041827); the static-feature substrate is moving now.
>
> **kotoba-native surfaces (use these for new work):**
> - Ontology: `00-contracts/schemas/maps-spatial-ontology.kotoba.edn` (`:feature/*`, the
>   `vertex_spatial` successor; H3-cell-as-Datom + **AVET** spatial index `:feature.cell/rN`).
> - Methods: `20-actors/maps/methods/{ingest,analyze}.py` (legacy `vertex_spatial` export →
>   `:feature/*` + H3 cells → `kg.ingest_batch`, G7-gated; Earth-coverage report). `run_tests.sh`
>   (12 green).
> - Adapter: `60-apps/.../maps-ui-uqpel6i6/src/kotoba-spatial.ts` (the §3 leverage point —
>   `queryByCells` AVET read, `ingestFeatures` no-server-key write). `cmdGetChunk` is rewired
>   **kotoba-first, fail-open** to RisingWave until R3.
> - Lexicons: `com.etzhayyim.maps.kg.{registerFeature,queryChunk}` +
>   `00-contracts/lexicons/com/etzhayyim/maps/MIGRATION-NOTES.md` (legacy→new map).
>
> **Migration phases:** R0 (foundation, done) · R1 (wire `KOTOBA_ENDPOINT`, backfill, flip
> getChunk kotoba-primary) · R2 (dumpers + 172-command tail via the adapter) · R3 (delete
> Hyperdrive + `vertex_*`). The legacy SQL below stays live as the fail-open fallback until R3.
> **The H3/Earth-coverage situation (Tokyo-anchored 3D walker) is unchanged by this work —
> it swaps *what stores features*, not *how much Earth is loaded*.**

---

Spatial Intelligence + Digital Twin Platform (maps.etzhayyim.com). Graph-first architecture — 自前グラフを育て、データがない時だけ外部ソースから取得・永続化。全外部ソースは path-based DID で identity 管理。

## App Identity

→ collection nanoid / domain: `deps.toml [[mitama_actors]]`

| Key | Value |
|---|---|
| **nanoid (UI)** | `uqpel6i6` |
| **AT bot DID** | `did:web:maps.etzhayyim.com` |
| **Runtime** | **TS Native** (`src/app.ts` + `@etzhayyim/kotodama-host-sdk` → esbuild bundle) |
| **Data store** | **RisingWave via Hyperdrive** — Write: `sdk.pds.dispatch({ type: "com.atproto.repo.createRecord", ... })` → PDS commit pipeline → graph Worker → Hyperdrive RisingWave, Read: `createKyselyDb(env.HYPERDRIVE).selectFrom(...)` → Hyperdrive RisingWave |
| **UI mode** | `iframe` (SvelteKit-Primary, MapLibre + KAMI engine) |

## Architecture: Graph-First, DID-Scoped Sources

```
Client Request
  → createKyselyDb(env.HYPERDRIVE).selectFrom(...)  (Kysely → Hyperdrive RisingWave)
    → HIT → return from graph (source_did で provenance 追跡)
    → MISS → collection job record 作成 (async PDS pipeline)
```

**Write path**: `sdk.pds.dispatch({ type: "com.atproto.repo.createRecord", payload: { collection, recordJson } })` → PDS commit pipeline → graph Worker → Hyperdrive RisingWave
**Social path**: `sdk.pds.dispatch({ type: "app.bsky.feed.post", text, ... })`
**Read path**: `createKyselyDb(env.HYPERDRIVE).selectFrom("vertex_*").where(...).execute()` (SQL は 2026-04-13 に archived)
**TTL**: WeatherPoint は `fetched_at` + `ttl_hours` で stale 判定。その他は永続。

## Source DIDs (path-based Multi-DID)

| DID | 外部 API 置換 | TTL |
|---|---|---|
| `did:web:maps.etzhayyim.com:geocode` | Nominatim (OSM) | 無期限 |
| `did:web:maps.etzhayyim.com:weather` | Open-Meteo | 1h |
| `did:web:maps.etzhayyim.com:ip_geolocation` | ip-api | 24h |
| `did:web:maps.etzhayyim.com:infrastructure` | Overpass API (OSM) | 7d |
| `did:web:maps.etzhayyim.com:tile` | OpenFreeMap | 30d |
| `did:web:maps.etzhayyim.com:street_view` | Mapillary | 30d |
| `did:web:maps.etzhayyim.com:planet` | OSM Planet | 週次 |
| `did:web:maps.etzhayyim.com:user_post` | User post EXIF geolocation | 無期限 |
| `did:web:maps.etzhayyim.com:mapraly` | Mapraly POI/route | 7d |
| `did:web:maps.etzhayyim.com:vision` | Murakumo Vision analysis | 無期限 |
| `did:web:maps.etzhayyim.com:satellite` | Sentinel-2 / Landsat (STAC) | 30d |
| `did:web:maps.etzhayyim.com:seismic` | USGS Earthquake Hazards API | 15m |
| `did:web:maps.etzhayyim.com:gtfs` | MLIT GTFS-JP (全国公共交通) | 1d |
| `did:web:maps.etzhayyim.com:adsb` | OpenSky Network ADS-B (航空機位置) | 5m |
| `did:web:site.etzhayyim.com` | site.etzhayyim.com Web Crawl (WET/WAT geo extraction) | 7d |

## Geo DID Architecture (multi-scheme, 3-layer)

### Layer 1: Visual Layer DIDs (11, KAMI rendering layers)

`did:web:{appId}.etzhayyim.com:layer:{slug}` — tile, poi, route, infra, building, weather, sensor, transport, geography, satellite, event

### Layer 2: Region DIDs (canonical nanoid + scheme alias DIDs)

`did:web:{appId}.etzhayyim.com:region:{nanoid}` — canonical AdminArea DID (stable, scheme-agnostic)
`did:web:{appId}.etzhayyim.com:geo:{scheme}:{code}` — scheme alias DIDs (ISO 3166, JIS, H3, S2, MGRS, etc.)

**Bootstrap**: JP country (1) + 47 prefectures. Each gets canonical DID + 2 alias DIDs (iso3166-2 + jis-x0401).

**32 supported schemes**: iso3166-1/2, jis-x0401/0402, fips, h3, s2, geohash, pluscode, mgrs, maidenhead, utm, flight-level, icao-fir, atmo-layer, elevation, depth-band, infra-depth, iho-sea, eez, bath-zone, koppen, wwf-biome, wwf-ecoregion, tectonic, icao-airport, iata-airport, unlocode, iana-tz

### Layer 3: Zone DIDs (vertical + natural)

`did:web:{appId}.etzhayyim.com:vzone:{slug}` — VerticalZone (atmosphere 5 + underground 4 + ocean 5 = 14)
`did:web:{appId}.etzhayyim.com:nzone:{slug}` — NaturalZone (Köppen 5 + biome 14 + tectonic 15 = 34)

### Graph Nodes

- `AdminArea` — canonical region with all scheme codes as properties
- `GeoAlias` — scheme→canonical resolution (RESOLVES_TO edge)
- `VerticalZone` — altitude/depth bands (minAlt, maxAlt)
- `NaturalZone` — climate/biome/tectonic zones
- `LayerCoordinator` — KAMI visual layer DID actors

### DID Count

| Category | Canonical | Alias | Total | 状態 |
|---|---|---|---|---|
| Layer coordinators | 11 | — | 11 | 実装済 |
| JP country | 1 | 2 (iso3166-1 + unlocode) | 3 | 実装済 |
| JP prefectures | 47 | 94 (iso3166-2 + jis-x0401) | 141 | 実装済 |
| Vertical zones | 14 | 14 | 28 | 実装済 |
| Natural zones | 34 | 34 | 68 | 実装済 |
| Source DIDs | 14 | — | 14 | 実装済 (+seismic, +gtfs, +adsb) |
| 195 sovereign countries | 195 | 390 (iso3166-1 + unlocode) | 585 | 実装済 |
| Major ports (50) | — | 50 (unlocode) | 50 | 実装済 |
| Major airports (40) seed | — | 80 (icao + iata) | 80 | 実装済 |
| **Total** | **316** | **664** | **980** | |

## Components

| Component | Folder | nanoid | Runtime | 役割 | コマンド数 |
|---|---|---|---|---|---|
| maps-ui | `maps-ui-uqpel6i6` | uqpel6i6 | TS Native | 全 14 WIT ドメイン (134 commands) | 134 |
| maps-collection | `maps-collection-control-plane-v1m9k2q8` | v1m9k2q8 | TS Native | Source registry, jobs, dataset (10 commands) | 10 |

## Commands — maps-ui (uqpel6i6)

### Spatial Intelligence (12)

| Command | Description |
|---|---|
| `search_places` | Search places by name/label |
| `get_place` | Get place by place_id |
| `reverse_geocode` | Reverse geocode lat/lng (graph-first, MISS → collection job) |
| `register_route` | Register route |
| `list_routes` | List routes (filter: route_type) |
| `get_route` | Get route by route_id |
| `weather_at` | Weather at lat/lng (graph-first, MISS → collection job) |
| `weather_grid` | Weather grid query (bbox) |
| `ip_geolocate` | IP geolocation lookup |
| `graph_traverse` | Graph traverse from node (depth 1-5) |
| `graph_neighbors` | Graph neighbors of node |
| `search_resources` | Search all spatial resources (multi-label) |

### Infrastructure Intelligence (10)

`register_infra_network`, `list_infra_networks`, `register_infra_segment`, `list_infra_segments`, `register_infra_node`, `list_infra_nodes`, `register_infra_incident`, `list_infra_incidents`, `infra_query` (type+location filter), `infra_cross_section` (7 layer depth/color map)

### Transport Intelligence (24)

register + list × 12 types: `road`, `railway`, `sea_route`, `air_route`, `bus_route`, `waterway`, `port`, `airport`, `station`, `bus_stop`, `parking`, `ev_charger`

### Geography Intelligence (18)

register + list × 7 types: `spot`, `river`, `lake`, `coastline`, `mountain`, `maritime_zone`, `admin_area` + `spot_search` (area+category+query) + `spot_recommend` (rating-based nearby) + `get_spot`

### Digital Twin (12)

`register_building`, `list_buildings`, `get_building`, `register_building_floor`, `register_asset`, `list_assets`, `device_bind`, `list_devices`, `twin_state_update`, `twin_state_get`, `twin_scene` (KAMI JSON-LD), `occupancy_update`

### Sensor Intelligence (7)

`register_sensor`, `list_sensors`, `sensor_ingest` (batch readings), `sensor_query`, `sensor_latest`, `sensor_alert_set`, `list_sensor_alerts`

### Simulation Intelligence (6)

`simulation_create`, `simulation_run`, `simulation_result`, `forecast_get`, `health_assess`, `maintenance_plan`

### Spatiotemporal (10)

`spatial_event_record`, `spatial_event_query`, `spatial_version_record`, `spatial_version_query`, `spatial_relation_write`, `spatial_relation_query`, `timeline`, `spatial_diff`, `display_layer_define`, `list_display_layers`

### Post Geolocation (2)

| Command | Description |
|---|---|
| `extract_post_location` | Extract EXIF geolocation from post images → SpatialEvent + Place |
| `list_post_locations` | List geolocated user posts (filter: author, area) |

### Mapraly Intelligence (3)

| Command | Description |
|---|---|
| `mapraly_ingest` | Create Mapraly collection job for region/bbox |
| `mapraly_import_poi` | Import Mapraly POIs/routes (batch) |
| `mapraly_list_pois` | List Mapraly-sourced POIs (filter: category, area) |

### Vision Intelligence (3)

| Command | Description |
|---|---|
| `analyze_image` | Submit image for spatial entity analysis via Murakumo Vision |
| `vision_import_entities` | Import vision-detected entities (Building, Spot, Place, etc.) |
| `list_vision_results` | List vision analysis results (filter: job, kind, confidence) |

### Satellite Intelligence (5)

| Command | Description |
|---|---|
| `satellite_ingest` | Ingest from free STAC catalogs (sentinel-2, landsat, sentinel-1, hls, cop-dem, naip) |
| `satellite_import_scene` | Import satellite scene metadata (sensor_type, stac_collection_id) |
| `satellite_analyze` | Analyze satellite scene via Murakumo Vision (change detection, land use) |
| `list_satellite_scenes` | List satellite scenes (filter: satellite, area, date) |
| `list_satellite_sources` | List available free satellite data sources with STAC endpoints |

### Geo DID Management (8)

| Command | Description |
|---|---|
| `register_region` | Register region with canonical DID + multi-scheme alias DIDs |
| `resolve_geo_alias` | Resolve any geo scheme code to canonical DID |
| `list_geo_aliases` | List geo aliases (filter by scheme) |
| `list_vertical_zones` | List vertical zones (atmosphere/underground/ocean) |
| `list_natural_zones` | List natural zones (climate/biome/tectonic) |
| `list_layer_coordinators` | List KAMI layer coordinator DIDs |
| `resolve_zones_3d` | Resolve all zones at 3D point (horizontal + vertical) |
| `list_geo_schemes` | List all 32 supported geographic code schemes |

### Web Crawl Geo Coverage (3)

| Command | Description |
|---|---|
| `seed_geo_domains` | Seed geo domain crawls via site.etzhayyim.com + CommonCrawl fallback (36 target domains) |
| `list_geo_domains` | List geo domain crawl targets (filter: category, country) |
| `list_web_crawl_geo_entities` | List geo entities extracted from WET/WAT (filter: domain, entityType) |

**Pipeline**: `seedGeoDomains` → cross-actor invoke `site.etzhayyim.com:seedForProject` → site crawls domains + CC fallback → WET/WAT records → maps `handleComAtprotoSyncSubscribeReposCommit` subscribes → WET: Murakumo NER geo entity extraction → WAT: outlink graph + geo sub-page discovery → `WebCrawlGeoEntity` graph nodes

**Target domains (56)**: JP GIS (nlftp.mlit.go.jp, gsi.go.jp, maps.gsi.go.jp, stat.go.jp), JP Transport (JR East/West/Central, Tokyo Metro, Navitime, ekitan), JP Hazard (disaportal.gsi.go.jp, jma.go.jp, j-shis.bosai.go.jp, river.go.jp), JP Municipal GIS (Tokyo/Osaka/Nagoya city), JP Real Estate (reinfolib, land.mlit.go.jp), JP Airport/Port (NRT, KIX, Tokyo Port), Global GIS (OSM, Natural Earth, GADM, Wikidata, Wikipedia, geofabrik, humdata, data.europa.eu), Global Transport (OpenRailwayMap, FlightRadar24, MarineTraffic, OurAirports), Hazard (USGS earthquake, EMSC, tsunami.gov, GDACS, FIRMS wildfire, flood.firetoc.eu), Satellite (Copernicus, USGS earthexplorer), Tourism (JNTO, japan.travel), Infrastructure (TEPCO, Tokyo Waterworks)

### Analytics (1)

`get_dashboard` — 21 entity type counts

## Commands — maps-collection-control-plane (v1m9k2q8)

| Command | Description |
|---|---|
| `register_source` | Register external data source (name, url, type, ttl, format, region) |
| `list_sources` | List sources (filter: status, source_type) |
| `create_collection_job` | Create collection job (source_id → pending) |
| `advance_job` | Advance job status/phase/progress |
| `list_jobs` | List jobs (filter: status, source_id) |
| `get_job_status` | Get job details by job_id |
| `store_dataset` | Store dataset (name, format, record_count, region, source_did) |
| `get_dataset` | Get dataset by dataset_id |
| `list_datasets` | List datasets (filter: format, region) |
| `get_pipeline_stats` | Pipeline stats (sources/jobs/datasets count) |

## Deploy Architecture

### maps-ui — Hono + Svelte CSR (Single Worker)

```
maps.etzhayyim.com / uqpel6i6.etzhayyim.com
  → Single Worker (kotodama-uqpel6i6, src/app.ts)
    ├─ /_app/meta     → host-sdk auto route
    ├─ static assets  → Workers Assets (svelte/build/)
    ├─ / , /?embed=1  → Hono router (Svelte CSR, MapLibre + KAMI)
    ├─ /_heartbeat    → runHeartbeat()
    ├─ /_commit       → handleComAtprotoSyncSubscribeReposCommit() (reactive pipeline)
    └─ /xrpc/{NSID}   → sdk.handleRequest() (87 commands)
```

### maps-collection — App Worker

```
v1m9k2q8.etzhayyim.com
  → dispatcher → account-level Worker (kotodama-v1m9k2q8)
    ├─ /_heartbeat    → runHeartbeat()
    ├─ /_commit       → handleComAtprotoSyncSubscribeReposCommit()
    ├─ /health        → {"status":"ok"}
    └─ /xrpc/{NSID}   → sdk.handleRequest() (10 commands)
```

## Write Path

`sdk.pds.dispatch({ type: "com.atproto.repo.createRecord", payload: { collection: "com.etzhayyim.apps.maps.{kind}", recordJson } })` → PDS → graph write path → RisingWave

Social posts: `writeBuffer.push({ type: "com-atproto-repo-create-post", payload: { value: { text, createdAt } } })`

## Graph Schema

### Node Labels (51 types)

**Core Spatial**: Place, Route, WeatherPoint, CrawlerHost, Region
**Infrastructure**: InfraNetwork, InfraSegment, InfraNode, InfraIncident
**Transport**: Road, Railway, SeaRoute, AirRoute, BusRoute, Waterway, Port, Airport, Station, BusStop, Parking, EvCharger
**Geography**: Spot, River, Lake, Coastline, Mountain, MaritimeZone, AdminArea
**Digital Twin**: Building, BuildingFloor, PhysicalAsset, TwinState, DeviceBinding, Sensor, SensorReading, SensorAlert, HealthAssessment, MaintenancePlan, Simulation, SimulationResult, Forecast
**Spatiotemporal**: SpatialEvent, SpatialVersion, SpatialRelation, DisplayLayer
**Vision**: VisionResult, SatelliteScene, CollectionJob
**Collection**: MapsSource, MapsJob, MapsDataset
**Geo DID**: LayerCoordinator, GeoAlias, VerticalZone, NaturalZone
**Web Crawl**: WebCrawlGeoEntity

### Edge Types

STARTS_AT, ENDS_AT, IN_REGION, PARENT_OF, OBSERVED_AT, LOCATED_AT, SEGMENT_OF, NODE_OF, CONNECTS, FLOOR_OF, ASSET_IN, TWIN_OF, BOUND_TO, MONITORS, RELATES_TO, EVENT_ON, VERSION_OF, DETECTED, SAME_AS, ANALYZED_FROM, RESOLVES_TO

## Lexicon (com.etzhayyim.apps.maps.*)

47 record kinds mapped via `LABEL_MAP` in `app.ts`. W Protocol kind `maps.{type}` → AT Lexicon `com.etzhayyim.apps.maps.{type}`.

## Infrastructure Types (infra_type)

| Type | 埋設深度 | KAMI color |
|---|---|---|
| `water` | 1.2m | `#3b82f6` |
| `sewage` | 3.0m | `#78716c` |
| `gas` | 1.5m | `#f59e0b` |
| `electric` | 0.8m | `#eab308` |
| `telecom` | 0.6m | `#10b981` |
| `subway` | 15.0m | `#6366f1` |
| `district_heating` | 1.0m | `#ef4444` |

## External Sources (fallback only)

Nominatim (OSM), Open-Meteo, ip-api, OSM Overpass, MLIT (国土数値情報), GTFS, AIS, ADS-B, OpenChargeMap, OpenFreeMap, Mapillary, Mapraly, Murakumo Vision (qwen3-vl-8b), Sentinel-2 L2A (ESA), Landsat C2L2 (USGS), Sentinel-1 GRD SAR (ESA), HLS (NASA), Copernicus DEM, NAIP (USDA) — 全衛星ソース無料

## Sentinel L7 Pipeline (ADR-2604271800)

**Status**: scaffolded 2026-04-27. **Not yet deployed** — pending `pnpm db:migrate` of `20260427210000_seed_maps_sentinel_bpmn_actors.ts` and helm upgrade of `mitama-udf-pool` (RW Smooth Scaling Gate must pass first).

**Pipeline (yoro-symmetric L7)**:

```
sentinelIngest.bpmn   (timer R/PT24H)
  ──► maps.sentinel.stac.search       (LangChain STAC POST /search)
        Element84 (S-2 L2A, no auth) + Copernicus Dataspace (S-1 GRD, OAuth)
  ──► generic.db.insert × N            (vertex_repo_record,
                                         collection com.etzhayyim.apps.maps.satelliteScene)
  ──► generic.audit.emit               (com.etzhayyim.apps.maps.sentinel.ingest)

sentinelAnalyze.bpmn  (xrpc POST com.etzhayyim.apps.maps.sentinelAnalyze)
  ──► generic.db.select                (load satelliteScene by sceneUri)
  ──► maps.sentinel.runpod.analyze     (LangChain chain → RunPod sync poll)
        models: sentinel2_change_siamese | sentinel2_landuse_unet | sentinel1_flood_unet
  ──► generic.db.insert                (vertex_repo_record,
                                         collection com.etzhayyim.apps.maps.satelliteAnalysis)
  ──► generic.audit.emit               (com.etzhayyim.apps.maps.sentinel.analyze)
```

**Files** (all live in this repo as of 2026-04-27):

| File | Purpose |
|---|---|
| `90-docs/adr/2604271800-maps-l8-sentinel-pipeline.md` | ADR (decision + scope) |
| `00-contracts/lexicons/com/etzhayyim/apps/maps/sentinelIngest.json` | XRPC procedure schema |
| `00-contracts/lexicons/com/etzhayyim/apps/maps/sentinelAnalyze.json` | XRPC procedure schema |
| `etzhayyim-root/00-contracts/bpmn/com/etzhayyim/maps/sentinelIngest.bpmn` | Timer-start BPMN |
| `etzhayyim-root/00-contracts/bpmn/com/etzhayyim/maps/sentinelAnalyze.bpmn` | XRPC-triggered BPMN |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/primitives/maps_sentinel.py` | LangServer primitives (STAC + RunPod + LangChain) |
| `30-graph/graph-schema/migrations/20260427210000_seed_maps_sentinel_bpmn_actors.ts` | BPMN registry seed |
| `50-infra/vultr/mitama-udf-pool/{templates/zeebe-worker.yaml,values.yaml}` | env wiring (RUNPOD / Copernicus secrets) |

**Persistence (Phase 1)**: `vertex_repo_record` only — no schema migration. Phase 2 (separate ADR) will promote to typed `vertex_satellite_scene` / `vertex_satellite_analysis` with bbox geom + COG URL columns. Held until live RW cluster footprint is back inside license caps.

**RunPod endpoint**: `RUNPOD_ENDPOINT_ID_MAPS` is **distinct** from yoro's `RUNPOD_ENDPOINT_ID` (yoro = chat, maps = vision/SAR). Endpoint provisioning is captured in a follow-up under `60-apps/etzhayyim-project-maps/runpod-endpoint/` (not in this repo yet).

**AOI bootstrap**: 12 KAMI layer coordinator centroids — Tokyo / Osaka / Ise / Hakata / Sendai / Naha / Sapporo / Niigata / Hiroshima / Shiogama / Kagoshima / Niihama bays. Override via `MAPS_SENTINEL_AOIS` env (semicolon-separated bbox CSV).

**Graceful degradation**: when `SENTINEL_HUB_CLIENT_*` is absent, the primitive silently skips Sentinel-1 and runs S-2-only (no auth needed). When `RUNPOD_KEY` / `RUNPOD_ENDPOINT_ID_MAPS` is absent, `sentinelAnalyze` returns `ok:false` with the missing-config error — BPMN OCEL audit captures the gap.

## Build & Deploy

```bash
# maps-ui (TS native → account-level Worker)
cd 60-apps/etzhayyim-project-maps/wasm/maps-ui-uqpel6i6
etzhayyim deploy

# maps-collection-control-plane (TS native → account-level Worker)
cd 60-apps/etzhayyim-project-maps/wasm/maps-collection-control-plane-v1m9k2q8
etzhayyim deploy
```

## Current Status (2026-04-13)

### Verified (E2E)

- Both components deployed: TS native, account-level Worker
- maps-ui: **134 XRPC commands** (14 WIT domains + analytics), deduplicated
- maps-collection: 10 XRPC commands (source/job/dataset/stats)
- Write: `sdk.pds.createRecord()` → PDS → graph Worker consumer → `vertex_spatial` (RisingWave)
- Read: `createKyselyDb(env.HYPERDRIVE).selectFrom("vertex_spatial")`
- Social: `sdk.pds.createRecord("app.bsky.feed.post", ...)`
- Heartbeat: OK (both components)
- handleComAtprotoSyncSubscribeReposCommit: OK (maps.* + ipaddress.ip_geo + site.wet/wat/geoRecord cross-app)
- 14 source DIDs: geocode, weather, ip_geolocation, infrastructure, tile, street_view, planet, user_post, mapraly, vision, satellite, **seismic**, **gtfs**, **adsb**
- 980 Multi-DIDs: 11 layer coordinators, 48 JP regions, 195 sovereign countries, 50 ports, 40 airports, 14 vertical zones, 34 natural zones
- **12 Overpass entity types** (spot×3 + building + road + station + port + evCharger + parking + busStop + river + mountain)
- **14 processGeoRecord handlers**: seismicEvent, municipality, gtfsStop, gtfsRoute, satelliteScene, adminArea2, airport, port, road, river, mountain, building, place, aircraft
- **11 derive.social rules** in kotodama.jsonld
- Web crawl NER pipeline: site.wet → Murakumo NER → webCrawlGeoEntity → **proper graph node write + social**
- **56 geo crawl target domains** (JP GIS/Transport/Hazard/Municipal/Airport/Port + Global GIS/Transport/Hazard/Satellite)
- p10-tables alignment: all 58 maps labels mapped to `vertex_spatial` (no consumer drops)
- Post EXIF geolocation: handleCommit auto-extracts lat/lng from app.bsky.feed.post image embeds
- Mapraly ingest: collection_job pattern, POI/route import, source DID tracking
- Murakumo Vision: image analysis → entity extraction (collection_job → VisionResult)
- Satellite: STAC catalog ingest (Sentinel-2/Landsat), scene import, Vision analysis pipeline
- Real-time feeds: USGS seismic (M4.5+/significant, alternating), OpenSky ADS-B (8-tile JP coverage), GTFS-JP (47 prefs cycling)
