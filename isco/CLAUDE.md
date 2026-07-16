# etzhayyim-project-open-isco — ISCO Workforce Coordinator Rules

ISCO project 固有のルール。共通ルールは `60-apps/CLAUDE.md` と `70-tools/CLAUDE.md` を参照。

## Overview

ILO ISCO-08 全分類を **1 app (coordinator) × 619 Multi-DIDs** で管理する。

## CRITICAL: Architecture — 1 App × Multi-DID

→ `etzhayyim dodaf tv1 query --id etzhayyim-project-open-isco-architecture-1-app-×-multi-did` / MCP `etzhayyim.dodaf.tv1.query`

## Single App

| App | Nanoid | DID | Role |
|---|---|---|---|
| workforce-coordinator | `wfc8k3n1` | `did:web:isco.etzhayyim.com` | 619 DID 管理、全 ISCO-08 コマンド |

## DID Path Convention

```
did:web:isco.etzhayyim.com:occupation:{code}
```

| 例 | Level | Name |
|---|---|---|
| `did:web:isco.etzhayyim.com:occupation:2` | major | Professionals |
| `did:web:isco.etzhayyim.com:occupation:25` | submajor | ICT Professionals |
| `did:web:isco.etzhayyim.com:occupation:251` | minor | Software and Applications Developers |
| `did:web:isco.etzhayyim.com:occupation:2512` | unit | Software Developers |

## WIT Structure

| WIT | パス | 内容 |
|---|---|---|
| `etzhayyim:isco-workforce-coordinator` | `wasm/*/wit/world.wit` | coordinator world (contract + capability export) |
| `kotodama:isco-workforce-flow@1.0.0` | `wit/isco-workforce-flow/package.wit` | workforce-mobility, workforce-compensation, workforce-skills |

## Commands

| Command | Description |
|---|---|
| `list` | List occupation groups (filter by `level`, `parent` code) |
| `get` | Get occupation by ISCO code with children |
| `search` | Search occupations by name or code (static, instant) |
| `tree` | Classification tree from any code (configurable depth) |
| `create` | Create occupation record linked to ISCO code |
| `stats` | Classification statistics and DID registration status |
| `describe` | Agent capabilities |
| `summarize` | LLM summary of occupation group (use_case: shinka) |
| `wave` | Social greeting |

## Heartbeat

初回 heartbeat で `registerAllDids()` → 619 DID を一括登録。以降は standard shinka heartbeat。

## Evolution Team

Evolution team (mk/po/bm/qa/eng) は coordinator 内の path-based DID として管理。個別 Worker 不要。

## Workforce Flow WIT (Resource Flow)

`kotodama:isco-workforce-flow@1.0.0` — ISCO 固有の資源フロー。

| Interface | Resource Class | 用途 |
|---|---|---|
| `workforce-mobility` | ヒト | 職種間異動、キャリア転換 |
| `workforce-compensation` | カネ | 給与、研修費、福利厚生 |
| `workforce-skills` | スキル | スキルギャップ分析、研修パスウェイ |
