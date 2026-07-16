# kabuto 兜 — agent reference

> World public-company supply-chain knowledge graph. Tier-B, R0 design-only. ADR-2606022000.
> Read the repo-root `CLAUDE.md` first; this file only adds actor-local rules.

## Identity

- **DID**: `did:web:etzhayyim.com:actor:kabuto` (resolvable via INFRA_ACTORS).
- **Glyph**: 兜 — 兜町 (Kabuto-chō), Tokyo's financial district / the Japanese "Wall Street".
  The corporate-markets sibling of the intel-weaver family.
- **Role**: the *public-company supply-chain* face of the observation upper layer, alongside
  `tsumugi` 紡ぎ (産霊 power-entity graph), `watatsuna` 綿津綱 (submarine-cable graph),
  `danjo` 弾正 (public accountability), `kanae` 鼎 (fiscal flows), `tadori` 辿 (on-chain tracing).
  kabuto reuses the shared `org.corp.*` id space, so a company here can be the same entity
  tsumugi already holds as a power-organism.

## Hard rules (constitutional — do not weaken)

1. **Resilience + transparency, not interdiction (G2).** Every output is framed toward supply
   diversification and corporate-power accountability. Never a "who to hit" / raid / takeover
   map. Mirrors Charter Rider **§2(d)** (infrastructure attack) + the tsumugi/watatsuna lineage.
2. **Public companies, public-record data only (G1).** Ingest only listed companies and
   public-record facts: name, ticker, exchange, LEI/ISIN, registered HQ, published IR contact,
   sector, and DISCLOSED supplier relationships. **Forbidden inputs**: personal PII, non-public
   commercial terms / contract prices, trade secrets.
3. **No adjudication (G4).** kabuto states public facts + computed concentration. It does not
   rule on legality, antitrust, or sanctions (UPL boundary — sibling of danjo). Concentration is
   an observation, not a verdict.
4. **Sourcing honesty (G5).** Every node/edge carries `:*/sourcing` ∈
   `:authoritative | :representative | :synthesized`. Supplier edges are `:representative`
   (disclosed/public, NOT an exhaustive bill of materials). `:supply.edge/criticality` is a
   bounded estimate, **never a contract figure**. BPMN templates are `:synthesized`. Absence ≠
   non-existence — it means "not yet ingested".
5. **kotoba-native (substrate boundary).** State = kotoba Datom log. No SQL / RisingWave / Lance
   as canonical store. Read path = kotoba-kqe arrangements over the Datom log.
6. **Browser-native render (G10).** `/search`, `/actors`, and the supply-chain viz run in the
   in-browser **kotoba-wasm node** (ADR-2606013600) — client-side query, no server round-trip.
7. **No git-lfs (G8).** Filings / org-chart assets → DataLad → IPFS under `80-data/public-company`.
8. **Murakumo-only (G6).** Any LLM narration routes through the Murakumo fleet (ADR-2605215000).
9. **Outward-gated INGEST (G7).** Live full-universe registry fetch (GLEIF LEI / SEC EDGAR /
   exchange listings — millions of LEIs) requires `KABUTO_OPERATOR_GATE` + Council. R0 ships a
   bounded real seed only.
10. **Outward-gated PUBLISH (G11).** Live atproto social posting requires `KABUTO_LIVE_POST=1` +
    `KOTOBA_ENDPOINT` + operator auth. Default is dry-run. Every post body passes a Charter Rider
    §2(a)-(h) content scan before it is eligible to publish.

## Vocabulary

`00-contracts/schemas/public-company-ontology.kotoba.edn`:
- `:company/*` — a listed company (id, name, ticker, exchange, LEI/ISIN, country, sector, status).
- `:company.address/*` — first-class registered HQ address (street/city/region/country/postal/lat/lon).
- `:company.contact/*` — public corporate/IR contact only (website, ir-url, ir-email, ir-phone).
- `:supply.edge/*` — first-class directed edge: supplier → customer, with commodity + criticality.
- `:company.process/*` — BPMN 2.0 process ref (procurement/disclosure), bpmn-cid anchors the XML.
- derived (`:supply/*`) — single-source, sector-concentration, jurisdiction-load. Computed by
  `analyze.py`, flagged `:derived`, **never re-ingested as fact**.

## Cells

- `cell:kabuto.ingest` → `methods/ingest.py` — public registry → kotoba EAVT bridge (offline
  default; live G7-gated). Documents the GLEIF/EDGAR/exchange full-universe path.
- `cell:kabuto.analyze` → `methods/analyze.py` (stdlib). classify → in/out degree → single-source →
  sector × commodity concentration → jurisdiction load. Aggregate-first. Idempotent.
- `cell:kabuto.autorun` → `methods/autorun.py` (+ `methods/kotoba.py`). The autonomous
  Murakumo-fleet heartbeat — the same shape shionome/ipaddress/yabai/sukashi/watatsuna/watari use.
  Each cycle observes the OFFLINE merged graph → classify → analyze → **persists a content-addressed
  transaction** (graph datoms + derived `:supply/*`) to the append-only **local** kotoba Datom log
  (`methods/kotoba.py`), linking the previous tx's CID into a verifiable commit-DAG. **G2 holds by
  construction**: only resilience/accountability framing (concentration, single-source, tier-depth,
  cross-bloc corridors) is representable — no raid/takeover/target attr. **Determinism note**:
  kabuto's analyze builds `intermediaries`/`tier_depth` by iterating PYTHONHASHSEED-randomized
  `set`s, so `autorun._canonical_order` sorts the datoms by canonical JSON before hashing — making
  the CID reproducible / resume-safe across processes (EAVT is an unordered set, so order carries no
  meaning). Fleet cells `kabuto_company_ingest` (cron 24) + `kabuto_concentration_weave` (cron 29) +
  `kabuto_supply_persist` (cron 34) on `zebulun` — see `50-infra/murakumo/fleet.toml`. Live
  GLEIF/EDGAR-universe ingest + live-node push + social posting stay Council + operator gated
  (G7/G11). Invariants guarded by `methods/test_autorun.py` (commit-DAG verify, tamper-detect,
  canonical-order determinism, append-only, derived-flagging, **G2 resilience-not-target-list**,
  no-external-I/O).

  ```bash
  python3 methods/autorun.py --cycles 3 --fresh   # AUTONOMOUS heartbeat → LOCAL kotoba Datom log
  ```
- `cell:kabuto.bpmn` → `methods/bpmn.py` — per-company BPMN 2.0 XML (well-formed, bpmn-js
  renderable) + `:company.process` datoms with content-CIDs. `:synthesized` generic templates.
- `cell:kabuto.social` → `methods/social.py` — aggregate-first company/edge/report →
  `app.bsky.feed.post` → Charter §2 scan → kotoba-server `atproto.repo.write`. G11-gated.
- `cell:kabuto.viz` → `viz/build_viz_data.cljc` — supply-chain force-graph payload + kotoba Datom
  array (browser-native via the kotoba-wasm node). The BPMN-tab manifest is
  `viz/build_bpmn_manifest.cljc`. Both ported to cljc (ADR-2606160842); the JSON/HTML render legs
  were not ported. Logic verified via: bb test:kabuto.

## Lexicons (kotoba-native)

`com.etzhayyim.kabuto.{registerCompany,registerSupplyEdge,publishIntelReport,publishSupplyChainViz,socialPost}`
— `00-contracts/lexicons/com/etzhayyim/kabuto/`.

## Run

```bash
cd 20-actors/kabuto
python3 methods/ingest.py                       # G7: bridge data/ingest/*.json + seed (offline default)
python3 methods/analyze.py                       # → out/intel-report.md + out/supply-criticality.kotoba.edn
python3 methods/bpmn.py                           # → out/bpmn/*.bpmn + out/processes.kotoba.edn
# viz payload + BPMN manifest builders ported to cljc (viz/*.cljc, ADR-2606160842); the
# JSON/HTML render legs were not ported. Logic verified via: bb test:kabuto
python3 methods/social.py --dry-run               # compose atproto posts (dry-run; G11 gate for live)
```

`python3 methods/analyze.py` with no argument runs the **seed** graph alone (no ingest needed).

## Honesty (R0)

Bounded illustrative seed of **1,719 real public companies** (incl. delisted retained as history) + **361 disclosed supplier edges** across
**15 sectors** and **116 countries** (growing each `/loop` iteration toward full global coverage) — **not** exhaustive coverage. HQ coordinates rounded to the campus/city. Supplier edges
are public/disclosed `:representative` estimates, not an exhaustive BOM; criticality is a bounded
estimate, never a contract figure. BPMN templates are `:synthesized` generic models, not a company's
actual internal process. "Register ALL public companies" is the **R1** goal — full GLEIF/EDGAR/
exchange-universe ingest (millions of LEIs) is **G7** Council + operator gated. Live atproto posting
is **G11** operator-gated.
