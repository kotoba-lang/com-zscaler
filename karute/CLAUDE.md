# karute — 電子カルテ (EMR / FHIR R5)

## Overview

FHIR R5 互換の電子カルテ。Patient / Encounter / SOAP / Observation / Condition / MedicationRequest / ServiceRequest を扱う。

- **URL**: https://karute.etzhayyim.com
- **API**: https://karu7t3e.etzhayyim.com/xrpc
- **Nanoid**: `karu7t3e`
- **DID**: `did:web:karute.etzhayyim.com`

## Substrate Compliance (CRITICAL)

| 規約 | 適用 |
|---|---|
| kotoba substrate (ADR-2605172000) | ✅ AT MST + IPFS + Base L2 のみ。RisingWave / Postgres 不使用 |
| 暗号化 envelope (ADR-2605181100) | ✅ 全 PHI は `com.etzhayyim.encrypted.record` envelope。XChaCha20-Poly1305 + Signal key-wrap |
| Payments on-chain (ADR-2605172100) | ✅ 自費診療は USDC + ERC-4337。保険請求 (社保/国保) は vendor 側 (`iryo.etzhayyim.com`) progressive enhancement |
| Charter Rider v2.0 (ADR-2605192200) | ✅ Apache 2.0 + Rider |
| 3軸 split (ADR-2605172400) | Liability=etzhayyim (PHI custody は 患者 DID 主体) / Custody=etzhayyim (PDS + IPFS 自己保管) / Settlement=etzhayyim (USDC) → 3軸とも clean → etzhayyim/root |

**禁止**:
- 平文 PHI を MST 上の任意 record に書く (患者氏名・生年月日・症状・処方含む)
- `@noble/ciphers` / `@signalapp/libsignal-client` の app code 直接 import (`@etzhayyim/sdk` 経由のみ)
- 保険請求 / Stripe / fiat processor 統合 (vendor 側にて)

## Architecture

```
Browser (SuperApp Mobile-First, max-w-[600px])
  ├─ HTML/JS → karute.etzhayyim.com (CF Worker edge proxy)
  └─ API → karu7t3e.etzhayyim.com/xrpc → Envoy Gateway → K8s LangServer Pod
              ↓
       Pipelines (@actor-manifest.jsonld):
         ├─ encrypted.write → @etzhayyim/sdk (XChaCha20-Poly1305 + Signal keyWrap)
         ├─ graph.write → public meta only (innerType pointer, encryptedCid, occurredAt, *Did)
         └─ agent.chat → 相互作用 check / SOAP 要約 / FHIR Bundle 生成
```

## Record Topology

### Encrypted envelope (PHI)

| Inner type | 用途 | FHIR R5 mapping |
|---|---|---|
| `com.etzhayyim.karute.patient` | 患者デモグラ・連絡先・アレルギー | `Patient` |
| `com.etzhayyim.karute.encounter` | 受診 (外来・入院・往診) | `Encounter` |
| `com.etzhayyim.karute.soapNote` | SOAP/経過記録 | `Composition` (section: SOAP) |
| `com.etzhayyim.karute.observation` | バイタル・検査結果 | `Observation` |
| `com.etzhayyim.karute.condition` | 病名・問題リスト | `Condition` |
| `com.etzhayyim.karute.medicationRequest` | 処方 | `MedicationRequest` |
| `com.etzhayyim.karute.serviceRequest` | 検査・画像オーダー・処置オーダー | `ServiceRequest` |
| `com.etzhayyim.karute.dispenseRecord` | 薬剤師調剤記録 (ADR-2605231400 ext) | `MedicationDispense` |

### Consent capability (non-PHI delegation)

| Collection | NSID | 用途 |
|---|---|---|
| consent capability | `com.etzhayyim.consent.capability` | Ed25519-signed delegation token (granter/grantee/purpose/scope/expiresAt). public record, not PHI. ADR-2605231400. |

すべて `com.etzhayyim.encrypted.record` envelope の `innerType` に入る。CID は ciphertext over。AAD = cid_self_ref。

### Public meta (graph index)

PHI を露出しない範囲で、検索・タイムライン構築に必要な最小フィールドのみ graph node として index する:

| Node label | Index keys | Purpose |
|---|---|---|
| `KarutePatient` | `patientDid`, `_seq` | 患者一覧 |
| `KaruteEncounter` | `patientDid`, `occurredAt`, `encounterType` | 受診タイムライン |
| `KaruteSoapNote` | `patientDid`, `encounterDid`, `occurredAt`, `authorDid` | SOAP 一覧 |
| `KaruteObservation` | `patientDid`, `encounterDid`, `loincCode`, `occurredAt` | バイタル/検査タイムライン |
| `KaruteCondition` | `patientDid`, `icd10`, `status` | 問題リスト |
| `KaruteMedicationRequest` | `patientDid`, `prescriberDid`, `status`, `interactionFlags` | 処方一覧 |
| `KaruteServiceRequest` | `patientDid`, `status`, `category` | オーダー一覧 |

**LOINC / ICD-10 / RxNorm コードは平文 OK** (識別性なし、terminology binding 経由)。
**患者氏名・生年月日・住所・症状記述は absolute プレーン書込み禁止**。

### Public (no PHI)

| Collection | NSID | 用途 |
|---|---|---|
| terminologyBinding | `com.etzhayyim.apps.karute.terminologyBinding` | LOINC/ICD-10/SNOMED/RxNorm/JLAC10 binding |
| coverageSnapshot | `com.etzhayyim.apps.karute.coverageSnapshot` | 統計 (患者数・受診数・処方数) |
| referral | `com.etzhayyim.apps.karute.referral` | 紹介状 metadata (本文は encrypted envelope) |

## XRPC API

| Method | Type | Purpose |
|---|---|---|
| `createPatient` | procedure | 新患登録 (encrypted) |
| `createEncounter` | procedure | 受診登録 |
| `createSoapNote` | procedure | SOAP 記録 + 相互作用 check |
| `createObservation` | procedure | バイタル/検査結果記録 |
| `createCondition` | procedure | 問題リスト追加 |
| `createMedicationRequest` | procedure | 処方発行 (相互作用 check 必須) |
| `createServiceRequest` | procedure | 検査/画像/処置オーダー |
| `getPatient` | query | 患者詳細 (read-cap 必須) |
| `listPatients` | query | 患者一覧 (public meta のみ) |
| `getEncounter` | query | 受診詳細 |
| `listEncounters` | query | 受診一覧 |
| `listSoapNotes` | query | SOAP 一覧 |
| `listObservations` | query | バイタル/検査一覧 |
| `listMedications` | query | 現行処方一覧 |
| `listOrders` | query | オーダー一覧 |
| `getChartSummary` | query | LLM 生成タイムライン要約 (public meta only) |
| `exportFhirBundle` | query | FHIR R5 Bundle export (要 read-cap) |
| `createDispense` | procedure | 薬剤師調剤記録 (encrypted; ADR-2605231400) |
| `listDispenses` | query | 調剤履歴一覧 (public meta) |
| `grantConsent` | procedure | consent capability 発行 |
| `revokeConsent` | procedure | consent capability 取消 |
| `listConsent` | query | 発行済 capability 一覧 |
| `requestIryoBilling` | procedure | iryo.etzhayyim.com (vendor) への保険請求 bridge |
| `healthKarute` | query | health check |

## FHIR R5 Mapping Rule

internal lexicon ↔ FHIR R5 resource は 1:1。Export 時は:

```
GET /xrpc/com.etzhayyim.apps.karute.exportFhirBundle?patientDid=...&recipientDid=...
  ↓ encrypted.read (recipientDid の read-cap 検証)
  ↓ inner records → FHIR R5 Resource transformation
  ↓ wrap in FHIR Bundle (type: collection)
  ↓ application/fhir+json 返却
```

terminology code system URIs:
- LOINC: `http://loinc.org`
- ICD-10-CM: `http://hl7.org/fhir/sid/icd-10-cm`
- ICD-10-JP: `urn:oid:1.2.392.200119.4.504.4` (日本独自)
- SNOMED CT: `http://snomed.info/sct`
- RxNorm: `http://www.nlm.nih.gov/research/umls/rxnorm`
- JLAC10 (日本臨床検査): `urn:oid:1.2.392.200119.4.504.7`

## Cross-Project Dependencies

| 連携先 | 用途 | Direction |
|---|---|---|
| `matrix.etzhayyim.com` | 受診/オーダー通知 (PHI redacted) | karute → matrix (Invoke) |
| `yabai.etzhayyim.com` | 海外紹介の制裁スクリーニング | karute → yabai (Invoke) |
| `trust.etzhayyim.com` | 医療従事者 DID trust score | karute → trust (Invoke) |
| `credits.etzhayyim.com` | second-opinion compute credit 付与 | karute → credits (Invoke) |
| `legal-entity.etzhayyim.com` | 医療機関法人確認 | karute → legal-entity (Invoke) |
| `iryo.etzhayyim.com` (vendor) | 保険請求 (DPC/DRG) — progressive enhancement | karute → iryo (consent capability) |

## Clinician Roles

| Role | Capabilities |
|---|---|
| MD | create/read/update all kinds. Rx prescriber |
| NP | create/read SOAP, observation, serviceRequest. Rx 共同署名のみ |
| RN | create/read observation, serviceRequest 実施記録 |
| PHARM | read Rx, create dispenseRecord (Phase 2) |
| ADMIN | read public meta only |
| PATIENT | read own. export FHIR Bundle to external system |

## UI (SuperApp Mobile-First)

- max-w-[600px] mobile width 統一
- SuperAppTabBar: **Home / Chart / Orders / Talk**
- Sidebar 禁止
- Home: 本日の受診予定 + 未処理オーダー + アラート
- Chart: 患者一覧 → 患者詳細 (timeline) → SOAP/Rx/Vitals 入力
- Orders: 検査・処方・処置の状態追跡
- Talk: Matrix (科内 / 院内 / 患者-医師 chat、全 E2E)

## Build & Deploy

```bash
cd 60-apps/etzhayyim-project-karute/appview/etzhayyim-wasm-karute-karu7t3e/svelte
pnpm install && pnpm build
cd ..
etzhayyim build
etzhayyim deploy --smoke-url https://karu7t3e.etzhayyim.com/health
```

## References

- ADR-2605231100 (karute EMR Phase 1) — `90-docs/adr/2605231100-karute-emr-phase1.md`
- ADR-2605181100 (encrypted records + Signal keywrap)
- ADR-2605172000 (kotoba substrate)
- ADR-2605172100 (payments on-chain)
- ADR-2605192100 (etzhayyim mission charter)
