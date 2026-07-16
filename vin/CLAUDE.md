# vin.etzhayyim.com — VIN / Vehicle Registration Intelligence

全世界の車両識別番号 (VIN) とナンバープレートの収集・正規化・cohort DID 登録。

## Architecture

| 項目 | 値 |
|---|---|
| **Runtime** | Single Worker |
| **performerType** | `service` (default sensitivity: `restricted`) |
| **UI** | appview (Protocol Canvas card UI) |
| **Data** | SQL graph (yata Workers RPC) — Role-based DID hierarchy (Shannon 最適) |
| **WIT export** | `etzhayyim:vin/vehicle-registry@1.0.0`, `vin-decoder@1.0.0`, `plate-resolver@1.0.0` |
| **Domain** | `vin.etzhayyim.com` |
| **Source type** | **1次ソース** (NHTSA API, 国交省リコール, EU RAPEX, WMI DB) + **2次ソース** (livecam OCR) |

## DID Structure (Role-based, Option C)

Shannon 最適: 全 agent を vin 内に集約。

### DID Hierarchy

| Role | DID Path | 概数 | 生成 |
|---|---|---|---|
| **Standard** | `did:web:vin.etzhayyim.com:standard:iso3779` | ~5 | 静的 |
| **Authority** | `did:web:vin.etzhayyim.com:authority:unece` | ~3 | 静的 |
| **Allocator** | `did:web:vin.etzhayyim.com:allocator:sae` | ~10 | 静的 |
| **Jurisdiction** | `did:web:vin.etzhayyim.com:jurisdiction:jpn` | ~200 | 静的 |
| **Manufacturer** | `did:web:vin.etzhayyim.com:manufacturer:toyota` | ~数千 | 動的 (WMI feed) |
| **WMI** | `did:web:vin.etzhayyim.com:wmi:jtd` | ~40,000 | 動的 |
| **Vehicle** | `did:web:vin.etzhayyim.com:vehicle:vin{hex}` | on-demand | on-demand (観測時) |
| **Plate** | `did:web:vin.etzhayyim.com:plate:{jurisdiction}:{encoded}` | on-demand | on-demand (OCR/登録時) |
| **Cohort** | `did:web:vin.etzhayyim.com:cohort:{cohortId}` | on-demand | 動的 (fleet/camera group) |

### VIN Encoding (alpha-start 準拠)

- VIN: `vin` prefix + uppercase。`JTDKN3DU5A0123456` → `vinJTDKN3DU5A0123456`
- WMI: 3文字コード。`JTD` (Toyota Japan)
- Plate: jurisdiction + encoded。`jpn:shinagawa330sa1234`

## Governance Hierarchy

```
ISO (standard:iso3779, standard:iso3780)
  └─ UNECE WP.29 (authority:unece)
       └─ SAE International (allocator:sae)        ← WMI 割当
       └─ 各国運輸局
            ├─ NHTSA (jurisdiction:usa)             ← VIN decode API, recall
            ├─ 国交省 (jurisdiction:jpn)            ← 車検証, リコール
            ├─ DVLA (jurisdiction:gbr)
            ├─ KBA (jurisdiction:deu)
            └─ ... (~200 jurisdictions)
                 └─ Manufacturer (manufacturer:*)
                      └─ Vehicle (vehicle:vin*)
                           └─ Plate (plate:*:*)
```

## SQL Graph Model

### Node Labels

| Label | Key Fields | Writer DID |
|---|---|---|
| `Vehicle` | vin, make, model, year, plant, wmi, vds, vis | `vehicle:*` |
| `VinRecord` | vin, raw_response, decoded_at, source | `vehicle:*` |
| `LicensePlate` | plate, jurisdiction, format, first_seen, last_seen | `plate:*` |
| `Manufacturer` | name, country, wmi_codes, vehicle_count | `manufacturer:*` |
| `WmiCode` | code, manufacturer, vehicle_type, region | `wmi:*` |
| `RecallCampaign` | campaign_id, manufacturer, subject, date, affected_count | `jurisdiction:*` |
| `VehicleCohort` | cohort_did, label, member_count, created_at | `cohort:*` |
| `JurisdictionRegistry` | country_code, authority_name, plate_format, vin_required | `jurisdiction:*` |

### Edge Types

```sql
(:Vehicle)-[:MANUFACTURED_BY]->(:Manufacturer)
(:Vehicle)-[:HAS_WMI]->(:WmiCode)
(:Vehicle)-[:HAS_PLATE]->(:LicensePlate)
(:Vehicle)-[:MEMBER_OF]->(:VehicleCohort)
(:Vehicle)-[:SUBJECT_OF]->(:RecallCampaign)
(:LicensePlate)-[:REGISTERED_TO]->(:Vehicle)
(:LicensePlate)-[:ISSUED_BY]->(:JurisdictionRegistry)
(:Manufacturer)-[:REGISTERED_IN]->(:JurisdictionRegistry)
(:WmiCode)-[:ASSIGNED_TO]->(:Manufacturer)
(:RecallCampaign)-[:ISSUED_BY]->(:JurisdictionRegistry)
```

## Seed Order (逆トポロジカルソート → coverage 100%)

Graph edge 依存を解析し、leaf (被依存 0) から順に seed する。

```
L0  JurisdictionRegistry ─────────────────────────────────┐
L0  VehicleCohort ─────────────────────────────────────────┤
    ↓                                                      │
L1  Manufacturer ──── [:REGISTERED_IN] → JurisdictionReg   │
L1  RecallCampaign ── [:ISSUED_BY]     → JurisdictionReg   │
    ↓                                                      │
L2  WmiCode ────────── [:ASSIGNED_TO]  → Manufacturer      │
    ↓                                                      │
L3  Vehicle ────────── [:MANUFACTURED_BY] → Manufacturer   │
    │                  [:HAS_WMI]         → WmiCode        │
    │                  [:MEMBER_OF]       → VehicleCohort ─┘
    │                  [:SUBJECT_OF]      → RecallCampaign
    ↓
L4  LicensePlate ──── [:REGISTERED_TO] → Vehicle
    │                  [:ISSUED_BY]     → JurisdictionRegistry
L4  VinRecord ──────── (1:1 with Vehicle)
```

### Seed Pipeline (逆トポロジカル順)

| Phase | Label | 概数 | Source | DID Role |
|---|---|---|---|---|
| **L0-a** | `JurisdictionRegistry` | ~200 | 静的 seed (ISO 3166-1) | `jurisdiction:*` |
| **L0-b** | `VehicleCohort` | on-demand | API / livecam | `cohort:*` |
| **L1-a** | `Manufacturer` | ~数千 | WMI DB bulk + NHTSA | `manufacturer:*` |
| **L1-b** | `RecallCampaign` | ~数万 | NHTSA API, 国交省 | `jurisdiction:*` |
| **L2** | `WmiCode` | ~40,000 | SAE WMI registry | `wmi:*` |
| **L3** | `Vehicle` | on-demand | VIN decode trigger | `vehicle:*` |
| **L4-a** | `LicensePlate` | on-demand | OCR / 登録 | `plate:*` |
| **L4-b** | `VinRecord` | on-demand | decode 結果 raw | `vehicle:*` |

### Seed Cron Schedule

```
Phase L0-a: 0 3 1 * *    (月次 — jurisdiction 変動少)
Phase L1-a: 0 3 * * 0    (週次 — WMI DB 更新追従)
Phase L1-b: 0 4 * * *    (日次 — recall campaign sync)
Phase L2:   0 3 * * 0    (週次 — L1-a と同日、依存順)
Phase L3+:  on-demand     (VIN decode / plate OCR 発火)
```

### Coverage 100% 条件

全ノードが以下を満たす:
1. **下位 Layer の全依存先が存在** (dangling edge なし)
2. **DID が発行済み** (`did:web:vin.etzhayyim.com:{role}:{id}`)
3. **repo + collection フィールドが設定済み** (ActorCoverageSnapshot で検証)

```sql
// coverage gap 検出 (dangling edge)
MATCH (v:Vehicle)-[:MANUFACTURED_BY]->(m)
WHERE NOT m:Manufacturer
RETURN v.vin AS orphan_vin, 'missing Manufacturer' AS gap

UNION

MATCH (v:Vehicle)-[:HAS_WMI]->(w)
WHERE NOT w:WmiCode
RETURN v.vin AS orphan_vin, 'missing WmiCode' AS gap

UNION

MATCH (p:LicensePlate)-[:REGISTERED_TO]->(v)
WHERE NOT v:Vehicle
RETURN p.plate AS orphan_vin, 'missing Vehicle' AS gap

UNION

MATCH (p:LicensePlate)-[:ISSUED_BY]->(j)
WHERE NOT j:JurisdictionRegistry
RETURN p.plate AS orphan_vin, 'missing JurisdictionRegistry' AS gap
```

## Plate Format (Multi-Jurisdiction)

| Jurisdiction | Format | Example | Encoding |
|---|---|---|---|
| JPN | `{地名}{分類番号}{かな}{番号}` | 品川 330 さ 1234 | `jpn:shinagawa330sa1234` |
| USA | `{state}-{plate}` | CA-8ABC123 | `usa:ca_8abc123` |
| EU/DEU | `{city}-{alpha}{num}` | M-AB 1234 | `deu:m_ab1234` |
| GBR | `{area}{age}{random}` | AB12 CDE | `gbr:ab12cde` |
| KOR | `{num}{hangul}{num}` | 12가 3456 | `kor:12ga3456` |

## Cohort DID Registration

livecam (YOLO車両検出 + OCR) → VIN actor へ cohort 登録リクエスト。

```
cohort DID: did:web:vin.etzhayyim.com:cohort:fleet_etzhayyim_tokyo
  └─ member: did:web:vin.etzhayyim.com:vehicle:vinJTDKN3DU5A0123456
  └─ member: did:web:vin.etzhayyim.com:vehicle:vinWBAJA5C51KB123456
```

用途:
- Fleet 管理 (社用車グループ)
- Camera zone 集計 (特定カメラの観測車両群)
- Recall 対象グループ
- 保険プール

## Lexicon NSIDs

```
com.etzhayyim.apps.vin.vehicle               # vehicle record (VIN decoded)
com.etzhayyim.apps.vin.vinRecord             # raw VIN decode result
com.etzhayyim.apps.vin.licensePlate          # license plate record
com.etzhayyim.apps.vin.manufacturer          # manufacturer record
com.etzhayyim.apps.vin.wmiCode               # WMI code record
com.etzhayyim.apps.vin.recallCampaign        # recall campaign
com.etzhayyim.apps.vin.cohortRegistration    # cohort DID registration event
com.etzhayyim.apps.vin.jurisdictionRegistry  # jurisdiction registry metadata
```

## Commands

| command | 説明 | MCP |
|---|---|---|
| `decode_vin` | VIN デコード (ISO 3779, WMI → メーカー/車種/年式/工場) | Y |
| `lookup_plate` | ナンバープレート検索 (jurisdiction-aware) | Y |
| `get_vehicle` | 車両詳細 (VIN key) | Y |
| `list_vehicles` | 車両一覧 | Y |
| `search_vehicles` | 車両検索 (make/model/year) | Y |
| `get_manufacturer` | メーカー詳細 (WMI codes, vehicle count) | Y |
| `list_manufacturers` | メーカー一覧 | Y |
| `collect_recall` | リコールキャンペーン収集 (NHTSA API, 国交省) | Y |
| `get_vehicle_history` | 車両履歴 (plate 変更, recall, cohort) | Y |
| `register_cohort` | cohort DID に車両を登録 | Y |
| `list_cohort` | cohort メンバー一覧 | Y |
| `seed_manufacturers` | 主要メーカー + WMI シード | Y |

## Cross-actor Integration

| Direction | Target | Method | Purpose |
|---|---|---|---|
| <- Receives | livecam.etzhayyim.com | `cohortRegistration` | カメラ OCR → plate → cohort DID 登録 |
| -> Followed by | maps.etzhayyim.com | `ComAtprotoSyncSubscribeRepos` | vehicle/plate geolocation |
| -> Followed by | yabai.etzhayyim.com | `ComAtprotoSyncSubscribeRepos` | 盗難車/不審車両アラート |
| <- Invokes | livecam.etzhayyim.com | `decode_vin` / `lookup_plate` | plate OCR 後の vehicle resolve |
| <- Invokes | maps.etzhayyim.com | `get_vehicle` | spatial vehicle query |
| <-> Bidirectional | tsukuru.etzhayyim.com | `graph.query` cross-app edge | 製造 → 車両 traceability |
| -> Publishes | resource-flow.etzhayyim.com | `shipmentVolume` | 出荷台数 → flow 可視化 |

## Extended Graph: Production Topology `[DESIGN]`

出荷台数 cohort × 車両タイプ vertex × 工場生産ライン → resource flow path planner。

### 追加 Node Labels

| Label | Key Fields | Writer DID | Source |
|---|---|---|---|
| `VehicleType` | type_code, body_style, segment, fuel_type, drive_type | `manufacturer:*` | OICA / メーカー公式 |
| `ProductionPlant` | plant_code, name, country, lat, lng, capacity_annual | `manufacturer:*` | メーカー IR / OICA |
| `ProductionLine` | line_id, plant_code, vehicle_types, throughput_per_hour | `manufacturer:*` | メーカー生産計画 |
| `ShipmentVolume` | jurisdiction, year, month, vehicle_type, volume, source | `jurisdiction:*` | OICA / 各国統計局 |
| `ShipmentCohort` | cohort_did, label, jurisdiction, year, vehicle_type, volume | `cohort:*` | 集計生成 |

### 追加 Edge Types

```sql
// Vehicle ↔ VehicleType
(:Vehicle)-[:IS_TYPE]->(:VehicleType)
(:VehicleType)-[:PRODUCED_ON]->(:ProductionLine)

// Production topology
(:ProductionLine)-[:BELONGS_TO]->(:ProductionPlant)
(:ProductionPlant)-[:OPERATED_BY]->(:Manufacturer)
(:ProductionPlant)-[:LOCATED_IN]->(:JurisdictionRegistry)

// Shipment flow
(:ShipmentVolume)-[:FROM_PLANT]->(:ProductionPlant)
(:ShipmentVolume)-[:TO_JURISDICTION]->(:JurisdictionRegistry)
(:ShipmentVolume)-[:OF_TYPE]->(:VehicleType)

// Cohort aggregation
(:ShipmentCohort)-[:AGGREGATES]->(:ShipmentVolume)
(:Vehicle)-[:MEMBER_OF]->(:ShipmentCohort)
```

### DID 追加

| Role | DID Path | 概数 | 生成 |
|---|---|---|---|
| **VehicleType** | `did:web:vin.etzhayyim.com:type:{segment}:{body}` | ~200 | 静的 (OICA segment) |
| **Plant** | `did:web:vin.etzhayyim.com:plant:{manufacturer}:{code}` | ~3,000 | 動的 (メーカー IR) |
| **Line** | `did:web:vin.etzhayyim.com:line:{plant}:{lineId}` | ~15,000 | 動的 |
| **ShipmentCohort** | `did:web:vin.etzhayyim.com:cohort:shipment:{jurisdiction}:{year}:{type}` | 動的 | 集計時生成 |

### 拡張 Seed Order (逆トポロジカル)

```
L0   JurisdictionRegistry ──────────────────────────────────────┐
L0   VehicleCohort / ShipmentCohort ────────────────────────────┤
     ↓                                                          │
L1   Manufacturer ────── [:REGISTERED_IN] → JurisdictionReg     │
L1   VehicleType ──────── (standalone, OICA segment taxonomy)   │
L1   RecallCampaign ──── [:ISSUED_BY] → JurisdictionReg         │
     ↓                                                          │
L2   WmiCode ──────────── [:ASSIGNED_TO] → Manufacturer         │
L2   ProductionPlant ──── [:OPERATED_BY] → Manufacturer         │
     │                    [:LOCATED_IN]  → JurisdictionReg      │
     ↓                                                          │
L3   ProductionLine ───── [:BELONGS_TO] → ProductionPlant       │
     │                                                          │
     ↓                                                          │
L4   Vehicle ──────────── [:MANUFACTURED_BY] → Manufacturer     │
     │                    [:HAS_WMI] → WmiCode                  │
     │                    [:IS_TYPE] → VehicleType               │
     │                    [:MEMBER_OF] → ShipmentCohort ────────┘
     ↓
L5   LicensePlate ──────  [:REGISTERED_TO] → Vehicle
L5   VinRecord ──────────  (1:1 with Vehicle)
L5   ShipmentVolume ─────  [:FROM_PLANT] → ProductionPlant
     │                     [:TO_JURISDICTION] → JurisdictionReg
     │                     [:OF_TYPE] → VehicleType
```

### Resource Flow as Path Planner

path_lexicon `flow-capacity` (#8) + `dependency-dag` (#7) を適用。

**Flow model**: 工場 → 出荷先 jurisdiction の resource flow。

```
Source:  ProductionPlant (capacity = capacity_annual)
Edge:    ShipmentVolume  (flow = volume per month)
Sink:    JurisdictionRegistry (demand = 登録台数)

Constraint:
  - flow-capacity: Σ outflow(plant) ≤ capacity_annual
  - dependency-dag: ProductionLine → VehicleType 制約 (ラインが作れる車種)
  - constraint-history: recall → 生産停止 (path-dependent blocker)
```

**Path planner integration** (`30-graph/graph-planner`):

| path_lexicon concept | VIN domain mapping |
|---|---|
| `state-expansion` | (plant, line, month) → 累積生産台数 state |
| `accumulated-cost` | 輸送コスト + 関税 (tsukuru trade-compliance) |
| `constraint-history` | recall → line 停止、部品不足 → throughput 低下 |
| `flow-capacity` | plant capacity → jurisdiction demand (max-flow) |
| `dependency-dag` | L0→L1→L2→L3→L4→L5 seed 順序 |
| `multi-agent-path` | 複数メーカーの同一 jurisdiction 向け出荷競合 |

### Cohort 自動生成 (出荷台数ベース)

OICA 年間出荷統計 (~95M 台/年, ~200 jurisdiction) → ShipmentCohort を自動生成。

```sql
// 出荷統計 → ShipmentCohort 自動生成
MATCH (sv:ShipmentVolume {year: $year})
WITH sv.jurisdiction AS jur, sv.vehicle_type AS vtype, sum(sv.volume) AS total
MERGE (sc:ShipmentCohort {
  cohortDid: 'did:web:vin.etzhayyim.com:cohort:shipment:' + jur + ':' + toString($year) + ':' + vtype
})
SET sc.label = jur + ' ' + toString($year) + ' ' + vtype,
    sc.jurisdiction = jur, sc.year = $year, sc.vehicle_type = vtype,
    sc.volume = total, sc.repo = $did, sc.updated_at = datetime()
```

**規模感 (2025 OICA ベース)**:

| Jurisdiction | 年間出荷 (万台) | Cohort 数 (type × year) |
|---|---|---|
| CHN | ~2,700 | ~50/year |
| USA | ~1,600 | ~40/year |
| JPN | ~450 | ~30/year |
| DEU | ~370 | ~25/year |
| IND | ~500 | ~35/year |
| KOR | ~370 | ~25/year |
| ... | ... | ... |
| **合計** | **~9,500** | **~600/year** (200 jurisdiction × ~3 type avg) |

### Integration Test Scenario

```
[Test: E2E Production → Shipment → Cohort → Vehicle → Plate]

1. Seed L0: JurisdictionRegistry (jpn, usa, deu)
2. Seed L1: Manufacturer (Toyota, BMW, Ford) + VehicleType (sedan, suv, truck)
3. Seed L2: WmiCode (JTD, WBA, 1FA) + ProductionPlant (元町, Dingolfing, Flat Rock)
4. Seed L3: ProductionLine (元町#1→sedan, Dingolfing#3→suv)
5. Inject ShipmentVolume: 元町 → jpn, 10000 sedan, 2025-01
6. Assert: ShipmentCohort auto-created (did:web:vin.etzhayyim.com:cohort:shipment:jpn:2025:sedan)
7. Decode VIN: JTDKN3DU5A0123456 → Toyota sedan
8. Assert: Vehicle → [:IS_TYPE] → VehicleType(sedan)
9. Assert: Vehicle → [:MEMBER_OF] → ShipmentCohort(jpn:2025:sedan)
10. Register Plate: jpn:shinagawa330sa1234 → Vehicle
11. Assert: full path exists:
    ProductionPlant(元町) → ProductionLine(#1) → VehicleType(sedan)
    → Vehicle(JTDKN...) → LicensePlate(品川330さ1234)
    → ShipmentCohort(jpn:2025:sedan) → ShipmentVolume(10000)
12. Path planner: max-flow query
    MATCH path = (pp:ProductionPlant)-[:HAS_LINE]->(:ProductionLine)
      -[:PRODUCES]->(:VehicleType)<-[:IS_TYPE]-(v:Vehicle)
      -[:MEMBER_OF]->(sc:ShipmentCohort)
    WHERE pp.name = '元町' AND sc.jurisdiction = 'jpn'
    RETURN count(v) AS actual_flow, pp.capacity_annual AS capacity
13. Social post: "Integration test passed: 元町 → jpn sedan flow verified"
```

### tsukuru.etzhayyim.com Cross-App Edge

```sql
// tsukuru ProductionOrder → vin Vehicle traceability
MATCH (po:ProductionOrder {orderId: $orderId})  // tsukuru graph
MATCH (v:Vehicle {vin: $vin})                    // vin graph
MERGE (po)-[:PRODUCED_VEHICLE]->(v)

// vin ProductionPlant ↔ tsukuru Manufacturer (shared DID)
// did:web:tsukuru.etzhayyim.com:manufacturer:toyota
// did:web:vin.etzhayyim.com:manufacturer:toyota
// → G() cross-app query で解決
```
