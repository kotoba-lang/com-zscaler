# etzhayyim-project-jp-ashiba

jp-ashiba.etzhayyim.com — 足場レンタル・サブスクリプション AI Agent (performerType: service)

## Overview

建設現場向け仮設足場のレンタル・サブスクリプション管理を AI Agent が自律運営するプラットフォーム。足場資材の在庫管理、現場配送スケジューリング、組立/解体の工程管理、月額サブスクリプション課金、安全点検記録を一元管理。

## Business Domain

### 足場レンタル (単発)

- 現場ごとに足場種別・数量・期間を指定してレンタル契約
- 配送→組立→使用→解体→回収の lifecycle 管理
- 延長・追加・部分返却に対応

### 足場サブスクリプション (月額定額)

- 月額定額で一定量の足場資材をプール利用
- 利用量超過分は従量課金 (kakin 連携)
- tier: starter (小規模工務店) / standard (中規模建設) / enterprise (ゼネコン)

## Architecture

```
建設会社 / 工務店 (XRPC client)
  │
  ▼
jp-ashiba.etzhayyim.com (App Worker)
  ├─ 足場資材カタログ (種別・規格・在庫)
  ├─ レンタル契約 lifecycle (見積→契約→配送→回収)
  ├─ サブスクリプション管理 (tier・利用量・更新)
  ├─ 現場スケジューリング (配送・組立・解体)
  ├─ 安全点検 (法定点検記録・不良品管理)
  ├─ GovernanceGate (RBAC + contract + trust)
  ▼
atproto.etzhayyim.com/xrpc/{NSID} (W Protocol Event Stream)
  ├─ graph query layer (RisingWave Hyperdrive)
  └─ kakin.etzhayyim.com (課金連携)
```

## performerType: service

- sensitivity: internal (契約・在庫情報)
- firehose: enabled (公開足場カタログ、安全情報)
- follow approval: public
- DM: enabled (顧客対応)

## Component

| Component | nanoid | 役割 |
|---|---|---|
| `etzhayyim-wasm-jp-ashiba-a5h1ba8k` | `a5h1ba8k` | **足場レンタル・サブスク本体** — 6 domain (catalog/rental/subscription/schedule/inspection/analytics) |

## Domain WIT (Lexicon)

**権威ソース**: `60-apps/etzhayyim-project-jp-ashiba/wit/jp-ashiba/package.wit`

AT Lexicon namespace: `com.etzhayyim.apps.jpAshiba.*`

| WIT interface | Lexicon prefix | 主要 record kinds |
|---|---|---|
| `catalog` | `com.etzhayyim.apps.jpAshiba.scaffoldItem` | 足場資材マスタ (種別・規格・単価) |
| `rental` | `com.etzhayyim.apps.jpAshiba.rentalContract` | レンタル契約 lifecycle |
| `subscription` | `com.etzhayyim.apps.jpAshiba.subscriptionPlan` | サブスク tier・利用量・更新 |
| `schedule` | `com.etzhayyim.apps.jpAshiba.siteSchedule` | 現場配送・組立・解体スケジュール |
| `inspection` | `com.etzhayyim.apps.jpAshiba.inspectionRecord` | 法定安全点検・不良品報告 |
| `analytics` | `com.etzhayyim.apps.jpAshiba.analyticsEvent` | KPI events (稼働率・回転率) |

## Data Model (W Protocol Event Stream)

### scaffoldItem (足場資材マスタ)

| Field | Type | Description |
|---|---|---|
| `itemId` | string | nanoid |
| `category` | enum | `kusabi` (くさび緊結式) / `waku` (枠組足場) / `tankan` (単管足場) / `tsuriBashira` (吊り足場) / `idoShiki` (移動式足場) |
| `spec` | string | 規格 (例: "W900×L1800", "φ48.6") |
| `unitPrice` | number | 日額単価 (¥) |
| `totalStock` | number | 総在庫数 |
| `availableStock` | number | 利用可能在庫数 |
| `condition` | enum | `good` / `fair` / `needsRepair` / `retired` |
| `jisStandard` | string | JIS A 8951 等の適合規格 |
| `weightKg` | number | 単品重量 (kg) |

### rentalContract (レンタル契約)

| Field | Type | Description |
|---|---|---|
| `contractId` | string | nanoid |
| `customerDid` | string | 顧客 DID |
| `siteAddress` | string | 現場住所 |
| `siteGeo` | object | `{lat, lng}` (maps.etzhayyim.com 連携) |
| `items` | array | `[{itemId, quantity, days}]` |
| `status` | enum | `quote` / `confirmed` / `delivered` / `inUse` / `dismantling` / `returned` / `completed` / `cancelled` |
| `startDate` | string | ISO 8601 |
| `endDate` | string | ISO 8601 |
| `totalAmount` | number | 合計金額 (¥) |
| `depositAmount` | number | 保証金 (¥) |

### subscriptionPlan (サブスクリプション)

| Field | Type | Description |
|---|---|---|
| `subscriptionId` | string | nanoid |
| `customerDid` | string | 顧客 DID |
| `tier` | enum | `starter` / `standard` / `enterprise` |
| `monthlyQuota` | object | `{kusabi: N, waku: N, tankan: N}` 種別別月間上限 |
| `currentUsage` | object | 当月利用量 |
| `monthlyFee` | number | 月額料金 (¥) |
| `overage_rate` | number | 超過単価 (¥/個/日) |
| `renewalDate` | string | ISO 8601 次回更新日 |
| `status` | enum | `active` / `paused` / `cancelled` |

### siteSchedule (現場スケジュール)

| Field | Type | Description |
|---|---|---|
| `scheduleId` | string | nanoid |
| `contractId` | string | 紐づくレンタル契約 |
| `taskType` | enum | `delivery` / `assembly` / `inspection` / `disassembly` / `pickup` |
| `scheduledDate` | string | ISO 8601 |
| `assignedCrewDid` | string | 担当作業班 DID |
| `status` | enum | `scheduled` / `inProgress` / `completed` / `delayed` |

### inspectionRecord (安全点検)

| Field | Type | Description |
|---|---|---|
| `inspectionId` | string | nanoid |
| `contractId` | string | 紐づくレンタル契約 |
| `inspectorDid` | string | 点検者 DID |
| `inspectionType` | enum | `daily` / `weekly` / `postStorm` / `preUse` |
| `checklist` | array | `[{item, result, note}]` |
| `overallResult` | enum | `pass` / `conditionalPass` / `fail` |
| `defects` | array | `[{part, severity, photo_cid}]` |
| `inspectedAt` | string | ISO 8601 |

## Subscription Tiers

| Tier | 月額 | 月間足場枠 | 配送回数 | 安全点検 | サポート |
|---|---|---|---|---|---|
| starter | ¥80,000 | くさび 50セット or 単管 100本 | 2回/月 | セルフ (チェックリスト提供) | Community |
| standard | ¥250,000 | くさび 200セット or 単管 500本 | 8回/月 | 週次 AI + 月次有資格者 | Email 24h SLA |
| enterprise | Custom | Custom | 無制限 | 常駐点検員 | 専任 1h SLA |

## Commands

| Command | NSID | Handler | 説明 |
|---|---|---|---|
| `list-catalog` | `com.etzhayyim.apps.jpAshiba.listCatalog` | cmdListCatalog | 足場資材カタログ一覧 |
| `get-item` | `com.etzhayyim.apps.jpAshiba.getItem` | cmdGetItem | 資材詳細取得 |
| `check-availability` | `com.etzhayyim.apps.jpAshiba.checkAvailability` | cmdCheckAvailability | 在庫・空き確認 |
| `create-quote` | `com.etzhayyim.apps.jpAshiba.createQuote` | cmdCreateQuote | 見積作成 |
| `confirm-rental` | `com.etzhayyim.apps.jpAshiba.confirmRental` | cmdConfirmRental | レンタル契約確定 |
| `extend-rental` | `com.etzhayyim.apps.jpAshiba.extendRental` | cmdExtendRental | レンタル期間延長 |
| `return-rental` | `com.etzhayyim.apps.jpAshiba.returnRental` | cmdReturnRental | 返却手続き |
| `subscribe` | `com.etzhayyim.apps.jpAshiba.subscribe` | cmdSubscribe | サブスク契約開始 |
| `change-tier` | `com.etzhayyim.apps.jpAshiba.changeTier` | cmdChangeTier | tier 変更 |
| `cancel-subscription` | `com.etzhayyim.apps.jpAshiba.cancelSubscription` | cmdCancelSubscription | サブスク解約 |
| `schedule-delivery` | `com.etzhayyim.apps.jpAshiba.scheduleDelivery` | cmdScheduleDelivery | 配送スケジュール作成 |
| `record-inspection` | `com.etzhayyim.apps.jpAshiba.recordInspection` | cmdRecordInspection | 点検記録登録 |
| `report-defect` | `com.etzhayyim.apps.jpAshiba.reportDefect` | cmdReportDefect | 不良品報告 |
| `get-usage-summary` | `com.etzhayyim.apps.jpAshiba.getUsageSummary` | cmdGetUsageSummary | サブスク利用量サマリ |

## Multi-DID Actor Composition

| DID | Role | 責務 |
|---|---|---|
| `did:web:jp-ashiba.etzhayyim.com` | controller | Primary app DID |
| `did:web:jp-ashiba.etzhayyim.com:actor:estimator` | 見積 AI | 現場条件→最適足場種別・数量の提案、価格算出 |
| `did:web:jp-ashiba.etzhayyim.com:actor:scheduler` | 配送計画 AI | 配送ルート最適化、作業班アサイン、天候考慮 |
| `did:web:jp-ashiba.etzhayyim.com:actor:inspector` | 安全点検 AI | 点検チェックリスト生成、写真 AI 解析、不良予測 |
| `did:web:jp-ashiba.etzhayyim.com:actor:inventory` | 在庫管理 AI | 在庫最適化、補充タイミング予測、稼働率分析 |
| `did:web:jp-ashiba.etzhayyim.com:actor:support` | 顧客対応 AI | 問い合わせ対応、FAQ、エスカレーション判定 |

## Cross-App Integration

| App | 連携内容 |
|---|---|
| `kakin.etzhayyim.com` | サブスク課金・従量課金・請求書発行 |
| `maps.etzhayyim.com` | 現場位置情報・配送ルート最適化 |
| `jinushi.etzhayyim.com` | 現場土地登記情報との照合 |
| `ops.etzhayyim.com` | プロジェクト管理連携 |
| `yotei.etzhayyim.com` | 配送・作業スケジュール連携 |
| `photos.etzhayyim.com` | 点検写真保存・AI 解析 |

## Regulatory Compliance

| 法令 | 内容 |
|---|---|
| 労働安全衛生法 | 足場の組立等作業主任者選任義務 (§14) |
| 労働安全衛生規則 | 足場の安全基準 (§559-§575) |
| 厚生労働省告示 | 足場先行工法ガイドライン |
| JIS A 8951 | 鋼管足場 |
| JIS A 8952 | 枠組足場用部材 |
| 仮設工業会認定 | くさび緊結式足場認定基準 |

## Data Access (W Protocol Event Stream)

- **Write**: `sdk.pds.dispatch({ type: "com.atproto.repo.createRecord", collection: kind, record: payload })` → PDS → graph write path
- **Read**: `sdk.graph.query(G("ScaffoldItem").match({ ... }).return("prop"))` (SQL builder)
- **DO SQLite / KV 直接 write 禁止**

## Contract

`contract-category: service-agreement` (足場レンタル・サブスクリプション利用規約 + 労働安全衛生法準拠)
