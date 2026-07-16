# etzhayyim-project-hanrei — Global Case Law, Legislation & Judicial Intelligence

## App Identity

→ nanoid / domain: `deps.toml [[mitama_actors]]`

| Key | Value |
|---|---|
| **AT bot DID** | `did:web:hanrei.etzhayyim.com` |
| **Runtime** | **TS Native** (`src/app.ts` + `@etzhayyim/kotodama-host-sdk` → esbuild bundle) |
| **Data store** | **W Protocol Event Stream** — Write: `sdk.pds.dispatch({ type: "com.atproto.repo.createRecord", ... })`, Read: `createKyselyDb(env.HYPERDRIVE).selectFrom(...)` |
| **UI mode** | `appview` (Protocol Canvas card, zero frontend) |

## Coverage

| Category | Count | Details |
|---|---|---|
| **National jurisdictions** | 75 | East Asia 6, Southeast Asia 8, South Asia 4, Oceania 2, North America 3, South America 5, Western Europe 11, Nordics 5, Central/Eastern Europe 12, Southern Europe 4, Eastern Europe 4, Middle East 5, Africa 7 |
| **International courts** | 8 | ICJ, ICC, ECHR, CJEU, IACHR, ACHPR, ITLOS, WTO AB |
| **Legal systems** | 4 | civil_law (47), common_law (15), islamic_law (1), mixed (12) |
| **Japan court DIDs** | 6 | supreme, ip_high, high, district, family, summary_court |
| **Japan source DIDs** | 3 | kanpo (gazette), egov (legislation), wikidata (courts) |
| **判例DID** | N | `did:web:hanrei.etzhayyim.com:hanrei:{rkey}` — 事件番号・裁判年月日・裁判所で一意識別 |
| **判決DID** | N | `did:web:hanrei.etzhayyim.com:hanketsu:{rkey}` — 判例に紐づく判決全文 (1判例:N判決) |
| **Citation graph** | edges | `com.etzhayyim.hanrei.citationEdge` — 判例間引用関係 |
| **Total** | 83 jurisdictions + 6 JP courts + 3 JP sources + 判例/判決 DID |

## Data Sources

### Japan (Deep Coverage — 1次ソース)

| Source | URL | Method | Writer DID |
|---|---|---|---|
| **最高裁判所** | `courts.go.jp/app/hanrei_jp/search2` | Collection Job (browser_automation) | `did:web:hanrei.etzhayyim.com:court:supreme` |
| **知的財産高等裁判所** | `courts.go.jp/app/hanrei_jp/search5` | Collection Job (browser_automation) | `did:web:hanrei.etzhayyim.com:court:ip_high` |
| **高等裁判所** | `courts.go.jp/app/hanrei_jp/search3` | Collection Job (browser_automation) | `did:web:hanrei.etzhayyim.com:court:high` |
| **地方裁判所** | `courts.go.jp/app/hanrei_jp/search4` | Collection Job (browser_automation) | `did:web:hanrei.etzhayyim.com:court:district` |
| **家庭裁判所** | `courts.go.jp/app/hanrei_jp/search6` | Collection Job (browser_automation) | `did:web:hanrei.etzhayyim.com:court:family` |
| **簡易裁判所** | `courts.go.jp/app/hanrei_jp/search7` | Collection Job (browser_automation) | `did:web:hanrei.etzhayyim.com:court:summary_court` |
| **官報** | `kanpou.npb.go.jp` | Collection Job (browser_automation) | `did:web:hanrei.etzhayyim.com:source:kanpo` |
| **e-Gov法令検索** | `elaws.e-gov.go.jp/api/1/lawdata` | Collection Job (api) | `did:web:hanrei.etzhayyim.com:source:egov` |
| **e-Gov法令API** | `laws.e-gov.go.jp/api/1/` | Collection Job (api, CC BY 4.0) | `did:web:hanrei.etzhayyim.com:source:egov` |
| **Wikidata SPARQL** | `query.wikidata.org/sparql` | Collection Job (sparql, CC0) | `did:web:hanrei.etzhayyim.com:source:wikidata` |

### Global Jurisdictions (per-jurisdiction DID: `did:web:hanrei.etzhayyim.com:jurisdiction:{iso3}`)

Each jurisdiction has 3 data sources: case database, legislation database, official gazette. All use Collection Job pattern. Jurisdiction metadata (court levels, legal system, primary law, source URLs) is in-memory authoritative (`jurisdictions[]` array in `app.ts`). Graph records are written via `register_jurisdictions` command.

## Write Path

`writeBuffer.push({ type: "com.atproto.repo.createRecord", payload: { collection, recordJson } })` → HTTP batch-flush → PDS `writeRecord()` → yata mergeRecord

Social posts: `writeBuffer.push({ type: "app.bsky.feed.post", payload: { text, opts } })`

## Graph Labels (collectionToLabel 自動変換)

| Collection | SQL Label |
|---|---|
| `com.etzhayyim.hanrei.case_record` | `CaseRecord` |
| `com.etzhayyim.hanrei.collection_job` | `CollectionJob` |
| `com.etzhayyim.hanrei.jurisdiction` | `Jurisdiction` |
| `com.etzhayyim.hanrei.digest` | `Digest` |
| `com.etzhayyim.hanrei.egov_law` | `EgovLaw` |
| `com.etzhayyim.hanrei.caseParty` | `CaseParty` |
| `com.etzhayyim.hanrei.hanreiRecord` | `HanreiRecord` |
| `com.etzhayyim.hanrei.hanketsuRecord` | `HanketsuRecord` |
| `com.etzhayyim.hanrei.citationEdge` | `CitationEdge` |

## Connected Actors

| Actor | Relation | Description |
|---|---|---|
| `saiban.etzhayyim.com` | hanrei → jiken link | 判例から事件DIDへの紐づけ (saiban が jiken を管理) |
| `lawfirm.etzhayyim.com` | hanrei → case-law search | lawfirm の search-case-law が hanrei を cross-actor invoke |
| `natural-person.etzhayyim.com` | hanrei → person extraction | CaseRecord から人物抽出 → natural-person に Invoke |

## Commands (XRPC)

### Japan-Specific Collection

| Command | Description |
|---|---|
| `collectCases` | Collection Job for courts.go.jp (search pages) |
| `collectCaseDetail` | Collect single case full text (detail page URL) |
| `collectCasesBatch` | Batch collect case detail pages (max 50) |
| `collectGazette` | Collection Job for 官報 |
| `collectLegislation` | Collection Job for e-Gov |
| `registerCourtProfiles` | Register JP court DIDs |

### Global Jurisdiction Management

| Command | Description |
|---|---|
| `registerJurisdictions` | Register all 83 jurisdiction DIDs + write Jurisdiction records |
| `listJurisdictions` | List jurisdictions (filter by legal_system, pagination) |
| `getJurisdiction` | Get jurisdiction details by ISO-3 code |
| `searchJurisdictions` | Search by name/system/primary law |
| `compareJurisdictions` | Compare legal systems across jurisdictions (max 10) |
| `coverageStats` | Global coverage statistics |

### Global Collection (per-jurisdiction)

| Command | Description |
|---|---|
| `collectJurisdictionCases` | Collect cases for a jurisdiction (iso3) |
| `collectJurisdictionLegislation` | Collect legislation for a jurisdiction |
| `collectJurisdictionGazette` | Collect gazette for a jurisdiction |

### Open Data Collection

| Command | Description |
|---|---|
| `collectEgovLaws` | Collection jobs for e-Gov法令API (CC BY 4.0, categories 1-4: 憲法/法律/政令/勅令) |
| `collectWikidataCourts` | Collection jobs for Wikidata SPARQL (CC0, courts/legal systems/international courts) |

### Person Extraction (→ natural-person)

| Command | Description |
|---|---|
| `extractCasePersons` | LLM で CaseRecord から人物抽出 → natural-person に Invoke (batch/single) |

### Unified Search

| Command | Description |
|---|---|
| `searchDecisions` | Cross-source search (CaseRecord + EgovLaw, filters: query/court/jurisdiction/date/type/source) |

### Queries

| Command | Description |
|---|---|
| `listCases` | List cases (court/type/level filter) |
| `getCase` | Get case by ID |
| `searchCases` | Search by title/summary/case_number |
| `listCourts` | List Japanese court entities |
| `listGazetteEntries` | List gazette entries |
| `listLaws` | List legislation |
| `listSources` | List all sources |
| `getDigest` | Daily digest |
| `seedCases` | Seed landmark cases |

## Current Status (2026-03-28)

### Verified (E2E)

- App deployed: TS native, account-level Worker
- All 27 XRPC commands operational (24 original + 3 open data: collect_egov_laws, collect_wikidata_courts, search_decisions)
- Write: `sdk.pds.dispatch({ type: "com.atproto.repo.createRecord", ... })` → PDS commit pipeline → graph Worker → Hyperdrive RisingWave
- Social: `sdk.pds.dispatch({ type: "app.bsky.feed.post", ... })`
- Read: `createKyselyDb(env.HYPERDRIVE).selectFrom(...)` → Hyperdrive RisingWave
- Heartbeat: OK
- 5 seed cases written + social announced
- 83 jurisdiction definitions (75 national + 8 international courts)
- Collection jobs created for Brazil, India, France, Germany (verified)

### Known Limitations

1. **yata `value_b64` opaque storage**: properties indexed only by rkey/collection/repo
2. **yata cold start**: first query after container sleep ~3s
3. **2-pass overhead**: SQL query via handler 2回実行

## Build & Deploy

```bash
cd 60-apps/etzhayyim-project-hanrei/wasm/etzhayyim-wasm-hanrei-jp-h4nr31jp
etzhayyim deploy                     # TS native mode
```
