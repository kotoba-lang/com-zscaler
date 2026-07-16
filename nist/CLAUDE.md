# nist.etzhayyim.com — NIST Cybersecurity Framework Intelligence

NIST CSF 2.0 の 6 Function + Tier Gap + CMMC L2 + SP 1302 Community Profile を Multi-DID logical actor として管理。106 subcategory 全量 + cross-framework mapping。

## App Identity

→ nanoid / domain: `deps.toml [[mitama_actors]]`

| Key | Value |
|---|---|
| **AT bot DID** | `did:web:nist.etzhayyim.com` |
| **Runtime** | Logical Actor (Worker なし、SQL `:Actor` node) |
| **UI** | yoro (profile-based) |

## Architecture — Logical Actor Only

全 actor は SQL graph node として存在。PDS が XRPC 代理応答。データは RisingWave graph。

```
did:web:n1st0csf.etzhayyim.com                          → root coordinator
  ├─ did:web:n1st0csf.etzhayyim.com:csf:govern          → GV (ガバナンス)
  ├─ did:web:n1st0csf.etzhayyim.com:csf:identify        → ID (識別)
  ├─ did:web:n1st0csf.etzhayyim.com:csf:protect         → PR (防御)
  ├─ did:web:n1st0csf.etzhayyim.com:csf:detect          → DE (検知)
  ├─ did:web:n1st0csf.etzhayyim.com:csf:respond         → RS (対応)
  ├─ did:web:n1st0csf.etzhayyim.com:csf:recover         → RC (復旧)
  ├─ did:web:n1st0csf.etzhayyim.com:tier:gap            → Tier 3→4 gap analysis
  ├─ did:web:n1st0csf.etzhayyim.com:cmmc:level2         → CMMC L2 (SP 800-171)
  └─ did:web:n1st0csf.etzhayyim.com:sp:communityProfile → SP 1302 community profiles
```

## Multi-DID Actor Definitions (10 actors)

| DID Path | Role | Description |
|---|---|---|
| (root) | Coordinator | CSF 2.0 framework coordinator。cross-function aggregation |
| `csf:govern` | Govern (GV) | GV.OC/RM/RR/PO/OV/SC — 31 subcategories |
| `csf:identify` | Identify (ID) | ID.AM/RA/IM — 22 subcategories |
| `csf:protect` | Protect (PR) | PR.AA/AT/DS/PS/IR — 28 subcategories |
| `csf:detect` | Detect (DE) | DE.CM/AE — 17 subcategories |
| `csf:respond` | Respond (RS) | RS.MA/AN/CO/MI — 18 subcategories |
| `csf:recover` | Recover (RC) | RC.RP/CO — 10 subcategories |
| `tier:gap` | Tier Gap | Tier 3→4 gap per subcategory × 6 dimension = 636 gap records |
| `cmmc:level2` | CMMC L2 | SP 800-171 r2, 14 families, 110 practices, CSF cross-mapping |
| `sp:communityProfile` | SP 1302 | Sector-specific community profile templates |

## Graph Labels

| Label | Count | Properties |
|---|---|---|
| `NistCsfFunction` | 6 | `code`, `name`, `description`, `version`, `ownerDid` |
| `NistCsfCategory` | 22 | `code`, `name`, `functionCode`, `subcategoryCount` |
| `NistCsfSubcategory` | 106 | `code`, `name`, `categoryCode`, `version` |
| `NistTierGap` | 636 | `subcategoryCode`, `categoryCode`, `dimension`, `tier3State`, `tier4State`, `ownerDid` |
| `NistCmmcFamily` | 14 | `code`, `name`, `practiceCount`, `csfPrimary`, `level`, `framework` |
| `NistCmmcPractice` | 110 | `code`, `sp800171`, `familyCode`, `csfPrimaryMapping`, `level` |
| `NistCmmcCsfMapping` | ~150 | `cmmcFamily`, `csfSubcategoryCode`, `relationship`, `gap` |
| `NistCommunityProfile` | 6+ | `name`, `sector`, `tier`, `highPriority`, `description` |
| `NistCsfProfile` | 0+ | `name`, `tier`, `targetState`, `currentState` |
| `NistCsfAssessment` | 0+ | `subcategoryCode`, `score`, `evidence`, `assessedAt` |

## Graph Relationships

```sql
-- Core taxonomy
(:Actor)-[:MANAGES]->(:NistCsfFunction)
(:NistCsfFunction)-[:HAS_CATEGORY]->(:NistCsfCategory)
(:NistCsfCategory)-[:HAS_SUBCATEGORY]->(:NistCsfSubcategory)

-- Tier gap
(:NistCsfSubcategory)-[:HAS_TIER_GAP]->(:NistTierGap)

-- CMMC mapping
(:NistCmmcFamily)-[:CONTAINS]->(:NistCmmcPractice)
(:NistCmmcPractice)-[:MAPS_TO]->(:NistCsfSubcategory)
(:NistCmmcCsfMapping {gap:true})-[:NOT_COVERED]->(:NistCsfSubcategory)

-- Community profile
(:NistCommunityProfile)-[:PRIORITIZES]->(:NistCsfCategory)

-- Assessment
(:NistCsfAssessment)-[:ASSESSES]->(:NistCsfSubcategory)
(:NistCsfProfile)-[:COVERS]->(:NistCsfSubcategory)
```

## W Protocol Event Stream

| Record Kind (camelCase) | Description |
|---|---|
| `com.etzhayyim.apps.nist.csfFunction` | CSF 2.0 Function definition |
| `com.etzhayyim.apps.nist.csfCategory` | CSF 2.0 Category definition |
| `com.etzhayyim.apps.nist.csfSubcategory` | CSF 2.0 Subcategory definition |
| `com.etzhayyim.apps.nist.tierGap` | Tier 3→4 gap per subcategory × dimension |
| `com.etzhayyim.apps.nist.cmmcFamily` | CMMC L2 family (14) |
| `com.etzhayyim.apps.nist.cmmcPractice` | CMMC L2 practice (110) |
| `com.etzhayyim.apps.nist.cmmcCsfMapping` | CMMC→CSF cross-mapping |
| `com.etzhayyim.apps.nist.communityProfile` | SP 1302 community profile |
| `com.etzhayyim.apps.nist.csfProfile` | Organization CSF Profile |
| `com.etzhayyim.apps.nist.csfAssessment` | Subcategory assessment result |

## Seed Script

`npx tsx 60-apps/etzhayyim-project-nist/seed.ts` — 全 ~1,060 records を PDS XRPC 経由で登録。

## Cross-actor Integration

| Target | Method | Direction | Purpose |
|---|---|---|---|
| scap.etzhayyim.com | `runScan` | nist → scap | NVD/OVAL スキャン実行 (ID.RA, DE.CM) |
| completer.etzhayyim.com | `evaluate` | completer → nist | CSF subcategory 準拠評価 |
| yabai.etzhayyim.com | `ingestThreatIntel` | yabai → nist | 脅威情報 → DE.AE |
| ct-monitor.etzhayyim.com | `pollVulnFeeds` | ct-monitor → nist | CVE/KEV → ID.RA |
| sbom.etzhayyim.com | `getBlastRadius` | nist → sbom | 影響範囲 → RS.AN |
| trust.etzhayyim.com | `evaluateTrust` | nist → trust | CSF tier → trust score 反映 |

## Cross-Framework Mapping

| Target Framework | Relationship | Purpose |
|---|---|---|
| CMMC 2.0 Level 2 | bidirectional | SP 800-171 r2 practice mapping (110 practices) |
| ISO 27001:2022 | bidirectional | ISMS control mapping |
| CIS Controls v8 | bidirectional | Implementation guidance |
| SOC 2 Type II | nist → soc2 | Trust Services Criteria mapping |
| NIST SP 800-53 r5 | nist → sp800 | Detailed control expansion |
| MITRE ATT&CK | detect/respond → attack | Technique coverage analysis |

## CMMC L2 Gap Summary

CSF subcategories NOT covered by CMMC L2 (主な gap):
- **GV 全体 (31)**: CMMC はガバナンス要件を直接定義しない
- **RC 全体 (10)**: 復旧計画は CMMC L2 スコープ外
- **ID.IM (4)**: 改善プロセスは CMMC L2 で部分的
- **ID.AM 一部**: 資産管理の一部サブカテゴリ
- **合計 ~50 subcategories** が CMMC L2 でカバーされない
