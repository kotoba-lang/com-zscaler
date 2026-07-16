# 20-actors/business-manager

> **kotoba-native (ADR-2606072000).** Canonical manifest is now `manifest.edn`; data model in
> `kotoba/schema.edn`; logic + tests in `py/` (15 green). Internal-only, append-only double-entry
> on the kotoba Datom log, audited by `toritate`. The legacy `actor-manifest.jsonld` (RisingWave/
> Cypher) is DEPRECATED (`DEPRECATED-jsonld.md`). The T1 MCP-Compose description below is historical.

**T1 MCP-Compose Actor。** Corporate ERP intelligence (GL/AP/AR, HR, procurement, budget)。日本会計年度 (4月始まり)。

→ nanoid / domain: `deps.toml [[mitama_actors]]`
→ Pipeline 定義: `20-actors/business-manager/actor-manifest.jsonld`
→ T3 fallback runbook: `60-apps/etzhayyim-project-business-manager/CLAUDE.md`

## Identity

| Key | Value |
|---|---|
| **AT bot DID** | `did:web:business-manager.etzhayyim.com` |
| **executionTier** | T1 (PDS Shared Executor — Worker 不要) |
| **performerType** | `service` |
| **uiType** | `yoro` |
| **governance** | `internal` |

## Capabilities

`graph.query`, `graph.write`, `agent.chat`, `derive:social`

## Graph Labels

`JournalEntry`, `Invoice`, `Employee`, `PurchaseOrder`, `BudgetAllocation`

## Approval Thresholds

| Type | Threshold |
|---|---|
| Journal entry | >1,000,000 JPY → approval-required |
| Purchase order | >5,000,000 JPY → approval-required |

## Triggers

- `subscribeRepos`: `com.etzhayyim.apps.businessManager.{journalEntry,invoice,employee,purchaseOrder,budgetAllocation}`
- `xrpc`: `listJournalEntries`, `createJournalEntry`, `createPurchaseOrder`, `coverage.get`
- `cron`: `0 9 * * 1` (weekly budget report), `0 */6 * * *` (coverage snapshot)
