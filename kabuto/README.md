# kabuto 兜 — World Public-Company Supply-Chain Knowledge Graph

**Tier-B actor · R0 design-only · ADR-2606022000**

kabuto 兜 (named for 兜町 / Kabuto-chō, Tokyo's financial district) datafies the world's
**public (exchange-listed) companies** — the company itself (name, ticker, exchange, LEI/ISIN,
sector), its **registered HQ address** and **public contact**, the first-class **supply edges**
(supplier → customer) that wire the global supply chain, and **BPMN** process templates — into the
kotoba Datom log, and surfaces where production **concentrates** onto a single-source supplier, a
sector, or one jurisdiction.

It is the **public-company / supply-chain** member of the observation upper layer, alongside
`tsumugi` 紡ぎ (産霊 power-entity graph), `watatsuna` 綿津綱 (submarine-cable graph),
`danjo` 弾正 (public accountability), `kanae` 鼎 (fiscal flows), `tadori` 辿 (on-chain tracing).
It reuses the shared `org.corp.*` id space.

## The constitutional frame (read first)

The output ranks supply **concentration by fragility so the chain can be made more robust** — buyers
diversify, the public holds corporate power accountable. It is a **resilience + transparency map,
never a target-list.**

- **G2 resilience-not-interdiction** — concentration is routed to diversification + accountability,
  never a "who to hit" / raid / takeover map (Charter Rider **§2(d)**).
- **G1 public-only** — listed companies + public-record facts (exchange listings, GLEIF LEI,
  company IR pages, filings, press). Personal PII, non-public commercial terms, and trade secrets
  are **out of scope and must not be ingested.**
- **G4 no adjudication** — kabuto states public facts + computed concentration; it does not rule on
  legality, antitrust, or sanctions (UPL boundary — sibling of danjo).

## Substrate

- **State**: kotoba Datom log (ADR-2605312345) — IPFS block backend, MST ingress, Base L2 anchor. No SQL, no RisingWave.
- **Vocabulary**: [`00-contracts/schemas/public-company-ontology.kotoba.edn`](../../00-contracts/schemas/public-company-ontology.kotoba.edn)
- **Render**: the in-browser **kotoba-wasm node** (ADR-2606013600) — `/search`, `/actors`, and the supply-chain viz query the Datom log client-side, no server round-trip.
- **Large assets** (filings, org charts): DataLad → IPFS under `80-data/public-company` (**no git-lfs**, G8).
- **Inference / narration**: Murakumo-only (ADR-2605215000).

## Layout

```
20-actors/kabuto/
├── manifest.jsonld                       # DID, cells, gates
├── README.md                             # this file
├── CLAUDE.md                             # agent reference
├── data/
│   ├── seed-public-companies.kotoba.edn  # 1,719 companies · 361 supply edges · 259 HQ · 155 contacts · 205 market caps · 2 BPMN seeds (:representative; grows each /loop)
│   └── companies.merged.kotoba.edn       # GENERATED: seed + ingest bridge (dedup)
├── methods/
│   ├── kabuto_edn.py                     # shared minimal EDN reader + classifier (stdlib)
│   ├── ingest.py                         # R1 — GLEIF/EDGAR/exchange → kotoba EAVT bridge (offline default; live G7-gated)
│   ├── analyze.py                        # supply-chain concentration analyzer (stdlib)
│   ├── bpmn.py                           # per-company BPMN 2.0 XML emitter + :company.process datoms
│   └── social.py                         # atproto-compatible post composer + kotoba-server publisher (G11-gated)
├── viz/
│   ├── _template.htm                     # viewer template (__KABUTO_DATA__ token)
│   ├── build_viz_data.py                 # analyzer → payload + self-contained force-graph
│   ├── supply-chain.json                 # GENERATED: viz payload (kotoba-wasm / kami-engine consumable)
│   └── supply-chain.htm                  # GENERATED: self-contained supply-chain map (data inlined)
└── out/
    ├── intel-report.md                   # aggregate-first concentration report
    ├── supply-criticality.kotoba.edn     # derived datoms (:derived; not re-ingested)
    ├── processes.kotoba.edn              # :company.process datoms with BPMN content-CIDs
    └── bpmn/*.bpmn                        # per-company BPMN 2.0 XML (bpmn-js renderable)
```

## Run

```bash
cd 20-actors/kabuto
python3 methods/ingest.py                       # R1: bridge data/ingest/*.json + seed → companies.merged (offline default)
python3 methods/analyze.py                       # → concentration report + derived datoms
python3 methods/bpmn.py                           # → per-company BPMN 2.0 (with BPMNDI layout) + process datoms
python3 viz/build_bpmn_manifest.py                # → viz/bpmn-manifest.json (featured set, served at /actor-bpmn/kabuto.json)
python3 viz/build_viz_data.py                     # → viz/supply-chain.htm (open in a browser)
python3 methods/social.py --dry-run               # compose atproto posts (dry-run)
```

`python3 methods/analyze.py` with no argument runs the **seed** graph alone (no ingest needed).

### Result (seed)

- **1,719** public companies · **361** disclosed supply edges · **15** sectors · **116** countries · **259** HQ addresses · **205** market caps
  (growing each `/loop` iteration toward full global coverage — all 15 sectors balanced; 116 countries
  across every macro-region; incl. Japanese sōgō shōsha, commodity traders/agribusiness, and deep
  fab-input suppliers — wafers, OSAT assembly/test, specialty gases, photoresist, mask-blanks,
  substrates, carbon fiber).
- Regional-bloc: **East Asia ~55%** of disclosed load; **cross-bloc exposure ~58%** crosses a region.
- Commodity HHI: **mask-blank / substrate / composite 1.0** (single disclosed supplier — HOYA / Ibiden /
  Toray), gas 0.41, lithography 0.44 — the deep redundancy priorities.
- **Composite resilience score** (capstone of 7 metrics): most-fragile customer is **Qualcomm**.
- **Data-coverage self-audit** (G5 honesty): the report states exactly what the seed carries (HQ /
  contact / edge / market-cap coverage %) — absence = "not yet ingested", never "does not exist".
- **14** single-source dependencies (≥0.7 criticality) — the redundancy gaps. The headline:
  **ASML → TSMC / Samsung / Intel** (sole EUV lithography, 0.95) and **TSMC → Apple / NVIDIA**
  (foundry, 0.95) — the same concentration the industry already watches.
- Top supplier jurisdictions by Σ criticality: **TW**, **JP**, **US** — the geographic
  concentration surface, routed to diversification.

### Live atproto posting (G11)

`methods/social.py` composes `app.bsky.feed.post`-shaped records (aggregate-first, public facts
only), runs a Charter Rider §2(a)-(h) content scan on every body, and — when
`KABUTO_LIVE_POST=1` + `KOTOBA_ENDPOINT=...` + operator auth are set — POSTs them to kotoba-server
`com.etzhayyim.apps.kotoba.atproto.repo.write`, writing into the kotoba Datom log (queryable by the
in-browser kotoba-wasm node + federated over AT Protocol). Default is dry-run.

```bash
KABUTO_LIVE_POST=1 KOTOBA_ENDPOINT=https://kotoba.etzhayyim.com \
  KABUTO_OPERATOR_TOKEN=… python3 methods/social.py --limit 5
```

### Visualization

`viz/supply-chain.htm` is **self-contained** (data inlined — no build step, no external fetch, open
via `file://`): a force-directed supply-chain graph (companies coloured by sector, sized by
out-degree; edges weighted by criticality; click a company → HQ + contact). Served at
`etzhayyim.com`, the in-browser **kotoba-wasm node** queries the live supply graph instead
(ADR-2606013600); the inlined payload is the offline data contract. A resilience/accountability
surface, never a target-list (G2).

### BPMN on the profile page (`プロセス` tab)

The profile at `https://etzhayyim.com/profile/did:web:etzhayyim.com:actor:kabuto` renders kabuto's
BPMN process models read-only via **bpmn-js**, through a **generic mechanism** in the yoro
`AgentProfile` component — *any* actor that publishes a BPMN manifest gets a **`プロセス` tab**
(no per-actor front-end code). Discovery order:

1. `actor.bpmnUrl` (explicit field on the PDS profile record), else
2. `<app-base>/_app/bpmn.json` (the actor's own app origin), else
3. `/actor-bpmn/<handle>.json` (same-origin static fallback, appview-bundled).

`viz/build_bpmn_manifest.py` emits the manifest `{ total, processes: [{ id, name, company, kind,
cid, xml }] }`. It inlines a bounded **featured** set (notable companies) — the full ~1.7k templates
would be multi-MB — and reports `total`. The generated BPMN now carries a full **BPMNDI** layout
(left-to-right lane) so it renders directly in any BPMN viewer. The kabuto manifest ships to the
appview at `public/actor-bpmn/kabuto.json`. **HONEST (G5)**: these are `:synthesized` generic
procurement/disclosure templates — NOT a company's actual internal process.

Front-end: `BpmnDiagram.svelte` (read-only `NavigatedViewer`) + a `bpmnTab` in `AgentProfile.svelte`.

## Display on etzhayyim.com

Registered in `INFRA_ACTORS` (`50-infra/etzhayyim-did-web/src/registry/infra-actors.ts`) and the
`actors-v1` profile seed → resolvable as **`did:web:etzhayyim.com:actor:kabuto`** at
`https://etzhayyim.com/actor/kabuto/did.json`, and discoverable at **`https://etzhayyim.com/search`**
+ `/actors` via the in-browser kotoba-wasm node (seeded into `kotoba/seed-datoms.json` so it renders
even when the live server is cold).

## Honesty (R0)

Bounded illustrative seed of real public companies — **not** exhaustive coverage. HQ coordinates
rounded to the campus/city. Supplier edges are public/disclosed `:representative` estimates, not an
exhaustive bill of materials; criticality is a bounded estimate, never a contract figure. BPMN
templates are `:synthesized` generic models. **"Register ALL public companies" is the R1 goal** —
full GLEIF / SEC EDGAR / exchange-listing universe ingest (millions of LEIs) is **G7** Council +
operator gated. Live atproto posting is **G11** operator-gated.

## Roadmap

- **R0** (this increment): ontology + actor scaffold + runnable 119-company seed analyzer +
  per-company BPMN + self-contained viz + atproto social path + kotoba-native lexicons +
  `/search` + `/actors` browser-native registration. ✅
- **R1** (post-Council): live GLEIF/EDGAR/exchange full-universe ingest (public sources only,
  operator-gated); continuous live atproto posting; kami-engine WASM 3D supply-globe.
- **R2**: cross-actor composition — kabuto concentration → tsumugi 取-concentration release,
  danjo accountability, himawari/hikari first-party provenance.
