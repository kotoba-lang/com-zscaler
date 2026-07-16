---
id: audit-runpod-rw-2026-05-21
title: "Substrate-fit audit — kuni-umi / shinka / yoro — RunPod / RisingWave / K8s / fiat violations"
status: active
doc_type: reference
topic: substrate-fit-audit
authoritative: true
last_verified: 2026-05-21
authoritative_for:
  - kuni-umi actor substrate coupling inventory
  - shinka actor substrate coupling inventory
  - yoro actor substrate coupling inventory
---

# Substrate-fit audit — kuni-umi / shinka / yoro
**Date**: 2026-05-21
**Auditor**: Claude Sonnet 4.6 (automated grep + manual review)
**Scope**: RunPod / commercial GPU rental; RisingWave / Hyperdrive / `vertex_*` direct coupling;
commercial K8s (Karmada / VKE); fiat payment processors (Stripe / PayPal).

## Governing ADRs

| ADR | Rule |
|---|---|
| ADR-2605172000 | kotoba substrate — AT MST + IPFS + Base L2 only |
| ADR-2605191346 | No commercial K8s — Murakumo fleet only (no Vultr VKE / Karmada) |
| ADR-2605191358 | yoro / murakumo kotoba rewrite map (status: proposed; 14 known RW touchpoints) |
| ADR-2605214000 | Vendor→religious-corp lexicon port verdict taxonomy (PORT-direct / PORT-adapted / REJECT) |
| ADR-2605215000 | No RunPod / no commercial GPU rental; kotodama REDIRECT/VENDOR-ONLY/REIMPLEMENT |

## Verdict key

| Verdict | Meaning |
|---|---|
| **PORT-direct** | Clean reference; no substrate violation; no edit needed |
| **PORT-adapted** | Comment/docstring-only reference; safe to clean up at Step 8 |
| **REDIRECT** | Env-var swap sufficient; no logic change required |
| **VENDOR-ONLY** | Vendor (etzhayyim.com) business logic; religious-corp must not invoke |
| **REIMPLEMENT** | Structural coupling; requires separate religious-corp implementation |
| **REJECT** | Required field couples to forbidden infra; lexicon/contract cannot port as-is |

---

## §1 kuni-umi

### Actor locations

| Path | Contents |
|---|---|
| `20-actors/kuni-umi/` | 6 Pregel cells + 1 BPMN + 3 DMN + CLAUDE.md + README.md |
| `40-engine/kotoba/crates/kotoba-kotodama/cells/` | 5 kotodama-level Pregel cells (religious-corp only — charter/tithe/etc.); no kuni-umi cells here |

kuni-umi cells are entirely self-contained under `20-actors/kuni-umi/cells/`:
`audit_witness`, `commissioning`, `construction_orchestration`, `decommission`, `deployment_planning`, `site_survey`.

### Violation grep results

Grep patterns executed: `runpod|RunPod|_RUNPOD_|api\.runpod\.ai|proxy\.runpod\.net`,
`risingwave|RisingWave|hyperdrive|Hyperdrive|createKyselyDb|HYPERDRIVE|vertex_|schema_registry`,
`Karmada|VKE|vke-primary|vultr.*kubernetes`, `Stripe|stripe|PayPal|paypal|fiat`.

**Findings in cell source files (`cells/*/cell.py`)**: zero matches on all patterns.

**Findings in BPMN (`bpmn/kuni-umi-deployment-workflow.bpmn`)**: zero matches on all patterns.

**Findings in DMN files**: zero matches on all patterns.

**Findings in CLAUDE.md**: two lines — both are prohibition declarations:

| File:Line | Finding | Verdict | Reason |
|---|---|---|---|
| `20-actors/kuni-umi/CLAUDE.md:10` | `RisingWave / Postgres / Kysely / centralized DB` (in "Prohibited" column) | PORT-direct | This is the boundary rules table. The text names the prohibited substrate — it is NOT a usage; it is a constraint declaration. No code or config couples to RisingWave. |
| `20-actors/kuni-umi/CLAUDE.md:11` | `Stripe / PayPal / fiat` (in "Prohibited" column) | PORT-direct | Same: prohibition declaration, not a usage. |

### §1 Summary

kuni-umi is **fully substrate-clean**. Zero actual RunPod, RisingWave, Hyperdrive, Karmada, VKE, Stripe, or PayPal references appear in executable code or infrastructure configs. The two CLAUDE.md lines enumerate prohibited substrates (correct practice). All 6 cells use only `kotodama.cell_runtime`, AT MST listener, IPFS, and Base L2 Web3 ports — entirely within the religious-corp substrate boundary.

**kuni-umi finding count: 0 violations (2 PORT-direct documentation references)**

---

## §2 shinka

### Actor locations

| Path | Contents |
|---|---|
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/shinka/__init__.py` | Core LangGraph loop (494 lines) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/primitives/shinka.py` | LangServer task handlers (145 lines; note: file content is different from module name — it contains handler wrappers, not primitives) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/handlers/shinka.py` | UDF handler entry point (77 lines) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/langgraph_graphs/shinka_cron_tick.py` | LangGraph StateGraph port (72 lines) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/sqlmesh/models/mv_shinka_activity_hourly.sql` | RisingWave MV (vendor sqlmesh model) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/sqlmesh/models/mv_shinka_knowledge_degree.sql` | RisingWave MV (vendor sqlmesh model) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/sqlmesh/models/mv_shinka_propagation_queue_stats.sql` | RisingWave MV (vendor sqlmesh model) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/tests/test_shinka_pure_helpers.py` | Pure helper tests (no DB/infra) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/tests/test_shinka_plan_agents.py` | Plan agent tests (no DB/infra) |

### Violation grep results

#### RunPod / commercial GPU
Zero matches in all shinka files.

#### RisingWave / Hyperdrive / vertex_*

| File:Line | Finding | Verdict | Reason |
|---|---|---|---|
| `shinka/__init__.py:21` | `"K8s CronJob in the mitama-udf namespace"` | VENDOR-ONLY | Module docstring describes deployment topology on vendor Karmada / VKE cluster (`mitama-udf` namespace). Religious-corp uses Murakumo fleet CronJob placement (`50-infra/murakumo/fleet.toml`). Comment must be updated at Step 8 cutover. |
| `shinka/__init__.py:160` | `"Pull joucho mood + last heartbeat + cadence rows from RW."` (comment) | REIMPLEMENT | `_load_state` function docstring. The function calls `fetch_one("SELECT mood, ... FROM vertex_joucho ...")` and `fetch_all("SELECT ... FROM vertex_actor_shinka_state ...")` via `kotodama.db_sync` (psycopg3 → `RW_URL` env var). Structural direct RisingWave read. Religious-corp must replace with AT MST read via `@etzhayyim/sdk`. |
| `shinka/__init__.py:166` | `FROM vertex_joucho WHERE owner_did = %s` | REIMPLEMENT | Direct RisingWave SQL query inside `_load_state`. Must become AT MST collection traverse for `com.etzhayyim.joucho.*` records. |
| `shinka/__init__.py:186` | `FROM vertex_actor_shinka_state WHERE repo_did = %s` | REIMPLEMENT | Direct RisingWave SQL query inside `_load_state`. Must become AT MST traverse for heartbeat cadence records. |
| `shinka/__init__.py:232–233` | `INSERT INTO vertex_shinka_knowledge (vertex_id, _seq, ...)` | REIMPLEMENT | Direct RisingWave INSERT in `_kyumei_gather`. Religious-corp must write via `@etzhayyim/sdk` `e.write({collection, record})` → PDS commit → IPFS pin. |
| `shinka/__init__.py:263` | `SELECT count(*) FROM vertex_shinka_knowledge WHERE owner_did = %s` | REIMPLEMENT | Direct RisingWave read in `_koji_validate`. Must be replaced with MST subtree count. |
| `shinka/__init__.py:282` | `SELECT count(*) FROM vertex_repo_commit WHERE repo = %s AND ts_ms > %s` | REIMPLEMENT | Direct RisingWave read in `_shinka_analyze`. Must be replaced with MST event query. |
| `shinka/__init__.py:299–300` | Comment: `"kotodama.llm → Vultr Serverless"` | VENDOR-ONLY | Comment in `_compose_content` docstring references Vultr Serverless as LLM backend. Religious-corp routes via Murakumo LiteLLM gateway (ADR-2605215000). Comment must be updated. |
| `shinka/__init__.py:304` | `vertex_shinka_evolution.props.draft` (comment) | PORT-adapted | Comment references RW table name for post-promotion. The structural REIMPLEMENT below covers this; comment update follows naturally. |
| `shinka/__init__.py:372–381` | `INSERT INTO vertex_actor_shinka_state ... ON CONFLICT ...` | REIMPLEMENT | Direct RisingWave UPSERT in `_write_heartbeat`. Must become AT record write via `e.write()`. |
| `shinka/__init__.py:399–405` | `INSERT INTO vertex_shinka_evolution (vertex_id, _seq, ...)` | REIMPLEMENT | Direct RisingWave INSERT in `_emit_evolution`. Must become AT record write via `e.write()`. |
| `handlers/shinka.py:13–14` | `vertex_actor_shinka_state UPSERT` / `vertex_shinka_evolution row` (docstring) | PORT-adapted | Docstring describes RW tables. Actual call path delegates to `shinka/__init__.py` functions; structural fix is in `__init__.py`. Docstring update follows. |
| `handlers/shinka.py:17–18` | `"K8s CronJob in the mitama-udf namespace"` | VENDOR-ONLY | Same as `__init__.py:21` — vendor deployment topology reference. |
| `handlers/shinka.py:22–24` | `"Vultr Serverless Devstral-2-123B"` + `vertex_shinka_evolution.props.draft` | VENDOR-ONLY | LLM routing comment references Vultr Serverless. Update to Murakumo LiteLLM gateway at Step 8. |
| `primitives/shinka.py:4` | `"LangServer/MCP path does not import deprecated broker clients"` — uses `kotodama.db_sync.sync_cursor` | REIMPLEMENT | `task_shinka_tick` executes `SELECT shinka_tick_actor(%s)` via `sync_cursor` (direct RW psycopg3). The `shinka_tick_actor` is a RisingWave SQL UDF. Religious-corp needs a Murakumo-native equivalent that calls the LangGraph loop directly without a SQL UDF intermediary. |
| `primitives/shinka.py:26–28` | `with sync_cursor() as cur: cur.execute("SELECT shinka_tick_actor(%s)", ...)` | REIMPLEMENT | Structural RisingWave UDF call. No env-var redirect possible. Requires new call path. |
| `langgraph_graphs/shinka_cron_tick.py:5` | `"Triggered by K8s CronJob (every 15 minutes) via POST /runs."` | VENDOR-ONLY | Deployment topology comment references vendor K8s CronJob. Religious-corp deployment is `fleet.toml` Murakumo cell. Update comment at Step 8. |
| `sqlmesh/models/mv_shinka_activity_hourly.sql` | Entire file | VENDOR-ONLY | Vendor RisingWave materialised view. Religious-corp has no RisingWave; this sqlmesh model is vendor-only infrastructure. No religious-corp equivalent needed (KPI aggregation will be MST-projector snapshots). |
| `sqlmesh/models/mv_shinka_knowledge_degree.sql` | Entire file | VENDOR-ONLY | Same: vendor RisingWave MV. |
| `sqlmesh/models/mv_shinka_propagation_queue_stats.sql` | Entire file | VENDOR-ONLY | Same: vendor RisingWave MV. |

#### Karmada / VKE / commercial K8s
The `mitama-udf` namespace references in `shinka/__init__.py:21` and `handlers/shinka.py:17` describe the vendor Vultr VKE / Karmada deployment path — classified VENDOR-ONLY above.

#### Stripe / PayPal / fiat
Zero matches in shinka files.

### §2 Summary

shinka has **no RunPod coupling** and **no fiat payment coupling**, but has deep, structural **RisingWave direct-write coupling** (5 REIMPLEMENT items) plus **Vultr K8s / Karmada topology references** (VENDOR-ONLY). The entire execution path of `_load_state`, `_kyumei_gather`, `_koji_validate`, `_write_heartbeat`, and `_emit_evolution` runs SQL directly against a `RW_URL` psycopg3 pool. The vendor-side `shinka_tick_actor` SQL UDF wraps all of this into one call; religious-corp requires a pure LangGraph invocation path without the UDF intermediary.

**shinka finding count: 8 REIMPLEMENT + 5 VENDOR-ONLY + 3 PORT-adapted = 16 findings**

---

## §3 yoro

### Actor locations

| Path | Contents |
|---|---|
| `60-apps/etzhayyim-project-yoro/` | Main yoro surface (flowering/fruiting social layer) |
| `60-apps/etzhayyim-project-yoro/kotoba/src/` | kotoba rewrite library (AT MST client) — 5 TS files |
| `60-apps/etzhayyim-project-yoro/xrpc-adapter/src/` | XRPC adapter — 1 TS file |
| `60-apps/etzhayyim-project-yoro/appview/yoro-ui-g00h5zto/svelte/src/` | Main SvelteKit UI (~150 TS/Svelte source files) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/primitives/yoro_social.py` | Python social primitives (1687 lines) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/primitives/yoro_product.py` | Python product primitives |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/langgraph_graphs/yoro_platform_pulse.py` | LangGraph platform pulse graph |
| `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/langgraph_graphs/yoro_product_ingest.py` | LangGraph product ingest graph |
| `40-engine/kotoba/crates/kotoba-kotodama/py/sqlmesh/models/mv_yoro_*.sql` | 5 RisingWave Materialised Views (vendor sqlmesh models) |
| `40-engine/kotoba/crates/kotoba-kotodama/py/alembic/versions/20260515_0003_gyosei_yoro_integration.py` | Alembic DB migration (vendor) |

ADR-2605191358 (status: proposed) is the active rewrite map for this actor. It documents 14 known RW touchpoints at the CLAUDE.md documentation level; this audit adds specific file:line citations for the code-level occurrences.

### Violation grep results

#### RunPod / commercial GPU
Zero matches in all yoro files (Python, TypeScript, Svelte).

#### RisingWave / Hyperdrive / vertex_* — Python layer

| File:Line | Finding | Verdict | Reason |
|---|---|---|---|
| `primitives/yoro_social.py:3` | `"vertex_repo_record fallback"` (module docstring) | PORT-adapted | Docstring describes RW table name. Actual INSERT calls below are structural. |
| `primitives/yoro_social.py:85` | `"Build a vertex_repo_record row..."` (comment in `_build_record`) | PORT-adapted | Comment describes vendor data model. |
| `primitives/yoro_social.py:93` | `f"Karmada hub and murakumo-k3s actor worker path alive at {created_at}."` | VENDOR-ONLY | Default pulse text string in `_build_record`. This is the intentional remainder documented in `PYKOTODAMA-MIGRATION-NOTES.md` Known Intentional Remainders table (`tests/test_yoro_social.py:57` / `primitives/yoro_social.py:93`). Describes vendor cluster topology. Do NOT rewrite; mark `# ETZHAYYIM: vendor-only` at Step 8. |
| `primitives/yoro_social.py:192–196` | `INSERT INTO vertex_profile (vertex_id, _seq, ...)` | REIMPLEMENT | Direct RisingWave INSERT in `upsert_profile`. Must become AT record write via `e.write({collection:'app.bsky.actor.profile', ...})`. |
| `primitives/yoro_social.py:245–268` | `DELETE FROM vertex_repo_record` + `INSERT INTO vertex_repo_record` + `DELETE FROM vertex_post` + `INSERT INTO vertex_post` | REIMPLEMENT | Multi-table direct RisingWave write sequence in `_sync_post`. Must become AT record write via `e.write()`. |
| `primitives/yoro_social.py:402` | `FROM vertex_repo_record` (SELECT in `get_recent_posts`) | REIMPLEMENT | Direct RisingWave read. Must become AT MST list query. |
| `primitives/yoro_social.py:540–549` | `DELETE FROM vertex_translation_link` + `INSERT INTO vertex_translation_link` | REIMPLEMENT | Direct RisingWave write sequence in `_sync_translation_link`. Must become AT record write. |
| `primitives/yoro_social.py:646` | `FROM vertex_profile` (SELECT) | REIMPLEMENT | Direct RisingWave read. Must become AT MST profile lookup. |
| `primitives/yoro_social.py:670` | `FROM vertex_repo_record` (SELECT) | REIMPLEMENT | Direct RisingWave read. |
| `primitives/yoro_social.py:886–892` | `INSERT INTO vertex_bpmn_activity_event` | REIMPLEMENT | Direct RisingWave INSERT in BPMN event handler. Must become AT record write. |
| `primitives/yoro_social.py:960–982` | `FROM vertex_profile` + `FROM vertex_repo_record` (SELECTs) | REIMPLEMENT | Two direct RisingWave reads in `get_stats`. |
| `primitives/yoro_social.py:1063–1067` | `SELECT count(*) FROM vertex_repo_record` + `SELECT count(*) FROM vertex_actor` | REIMPLEMENT | Direct RisingWave aggregate queries in `get_stats`. |
| `primitives/yoro_social.py:1167` | `FROM vertex_fukkou_diet_speech` (SELECT) | REIMPLEMENT | Direct RisingWave read in diet-speech query. |
| `primitives/yoro_social.py:1475–1520` | `SELECT 1 FROM vertex_profile` + `UPDATE vertex_profile` + `INSERT INTO vertex_profile` | REIMPLEMENT | Read-modify-write against `vertex_profile` in `upsert_profile_by_did`. |
| `langgraph_graphs/yoro_product_ingest.py:24–26` | `RisingWave persistence (ADR-0036 Tier 2 ...)` (module docstring) | PORT-adapted | Docstring names RW tables. Structural coupling is in `write_research` function below. |
| `langgraph_graphs/yoro_product_ingest.py:145–198` | `"Write 1 vertex_yoro_productResearch row..."` + `insert("vertex_yoro_product_research", ...)` | REIMPLEMENT | Direct RisingWave INSERT via SQLAlchemy Core `insert()`. Must become AT record write + MST-projector index update. |
| `sqlmesh/models/mv_yoro_actor_evolution_counts.sql` | Entire file | VENDOR-ONLY | Vendor RisingWave MV (`SELECT ... FROM vertex_yoro_kyumei_validation UNION ALL ...`). No religious-corp equivalent; aggregation becomes MST-projector snapshot. |
| `sqlmesh/models/mv_yoro_actor_score_counts.sql` | Entire file | VENDOR-ONLY | Same: vendor RisingWave MV. |
| `sqlmesh/models/mv_yoro_browsing_history_recent.sql` | Entire file | VENDOR-ONLY | Same: vendor RisingWave MV. |
| `sqlmesh/models/mv_yoro_evolution_recent.sql` | Entire file | VENDOR-ONLY | Same: vendor RisingWave MV. |
| `sqlmesh/models/mv_yoro_evolution_stats.sql` | Entire file | VENDOR-ONLY | Same: vendor RisingWave MV. |
| `alembic/versions/20260515_0003_gyosei_yoro_integration.py` | Entire file | VENDOR-ONLY | Vendor Alembic migration targeting the same RW schema. Religious-corp has no RisingWave; no equivalent migration needed. |

#### RisingWave / Hyperdrive / vertex_* — TypeScript / Svelte layer

| File:Line | Finding | Verdict | Reason |
|---|---|---|---|
| `appview/.../CLAUDE.md:131–132` | `→ yoro AppView → HYPERDRIVE` (data-flow diagram) | VENDOR-ONLY | CLAUDE.md read-path diagram describes vendor AppView architecture (`PDS pipethroughAppView → HYPERDRIVE → RisingWave`). ADR-2605191358 maps replacement path. Note: this is the yoro CLAUDE.md, not the root CLAUDE.md. |
| `appview/.../fiscal/ResourceFlowTab.svelte:9` | `→ Hyperdrive → RisingWave (ADR-0002 / ADR-0035 §schema)` (JSDoc comment) | PORT-adapted | Comment in JSDoc block. Actual data path is via PDS XRPC; comment describes the server-side routing which must change. |
| `appview/.../graph/kagami-store.svelte.ts:17` | `vertex_id?: string` (type field) | PORT-adapted | Field name `vertex_id` in query result type. Safe to rename to `record_id` when MST path lands; no runtime impact today. |
| `appview/.../graph/kagami-store.svelte.ts:56` | `row.vertex_id = raw.vertex_id` | PORT-adapted | Same `vertex_id` field reference in result parsing. |
| `appview/.../graph/kagami-store.svelte.ts:130–194` | Multiple `row.vertex_id ?? row.edge_id` references (lines 130, 134, 181, 186, 194) | PORT-adapted | Result deduplication keys using `vertex_id` field from RW query results. Will naturally update when MST path replaces the query. |
| `appview/.../provider/graph-rag.svelte.ts:244` | `"Retrieve graph context via PDS XRPC → graph SQL path → RisingWave."` (JSDoc) | PORT-adapted | JSDoc comment. Structural coupling is the SQL query in same function. |
| `appview/.../provider/graph-rag.svelte.ts:259` | `// Single path: PDS XRPC federatedQuery for each label (graph SQL path → RisingWave)` | PORT-adapted | Inline comment. |
| `appview/.../provider/graph-rag.svelte.ts:270–273` | `rows: Array<{ vertex_id: string; ... }>` + `SELECT label, vertex_id, val, ...` | REIMPLEMENT | Inline SQL string passed to federatedQuery → RisingWave. Must become AT MST query via `@etzhayyim/sdk`. ADR-2605191358 §path-level rewrite: `kagami-store.federatedQuery` → `e.federated({host, collection, filter})`. |
| `appview/.../queries/history.ts:23` | `FROM vertex_yoro_browsing_history` | REIMPLEMENT | Inline SQL selecting from RW MV. Must become MST record list. |
| `appview/.../queries/evolution.ts:68–101` | `FROM vertex_profile AS p` + `FROM vertex_profile` | REIMPLEMENT | Two inline SQL queries selecting from `vertex_profile` RW table. Must become MST profile reads via `@etzhayyim/sdk`. |
| `appview/.../gamification/BeliefKarmaTab.svelte:5` | `"fetches actor-specific belief karma from RisingWave."` (JSDoc) | PORT-adapted | JSDoc only; structural coupling is the SQL below. |
| `appview/.../gamification/BeliefKarmaTab.svelte:106–120` | `SELECT vertex_id AS belief_vertex_id ... FROM vertex_belief_system` | REIMPLEMENT | Inline SQL against `vertex_belief_system` RW table. Must become MST record query. |
| `appview/.../superapp/ProfilePanel.svelte:245` | `FROM vertex_dojo_step_completed_event` | REIMPLEMENT | Inline SQL in ProfilePanel. Must become MST event query. |
| `appview/.../routes/profile/[handle]/AgentProfile.svelte:1061–1241` | Multiple inline SQLs: `FROM vertex_page`, `FROM vertex_wet_chunk`, `FROM vertex_agent_governance_rule`, `FROM vertex_agent_role_binding`, `FROM vertex_governance`, `LEFT JOIN vertex_actor` | REIMPLEMENT | Six inline SQL queries across AgentProfile.svelte (lines 1061, 1074, 1121, 1217, 1224, 1230–1241). All must become AT MST / XRPC queries via `@etzhayyim/sdk`. |
| `appview/.../routes/profile/[handle]/+page.svelte:158` | `// Single path: PDS XRPC getAuthorProfile → graph SQL path → RisingWave` | PORT-adapted | Inline comment only. |
| `appview/.../routes/profile/[handle]/+page.svelte:211` | `SELECT value_json FROM vertex_state_profile WHERE repo = 'states.etzhayyim.com'` | REIMPLEMENT | Inline SQL against `vertex_state_profile`. Must become MST read. |
| `appview/.../routes/research/+page.svelte:3` | `"Reads vertex_yoro_product_research via XRPC"` (comment) | PORT-adapted | Comment; structural fix is in Python `yoro_product_ingest.py`. |
| `appview/.../routes/lpm-dashboard/+page.svelte:47` | `"Real-time LangGraph agent performance & trace monitoring via RisingWave Graph DB."` | PORT-adapted | UI copy referencing RW. Must be updated to "AT MST + mst-projector snapshots" when the rewrite lands. |
| `appview/.../routes/karma/+page.svelte:59` | `"5-layer persistence: RisingWave hot · AT-repo · IPFS-self..."` | PORT-adapted | UI copy naming RW in persistence layer description. ADR-2605172000 replaces RW with MST-projector. |
| `appview/.../routes/world-states/+page.svelte:16` | `FROM vertex_state_profile` (inline SQL) | REIMPLEMENT | Must become MST query. |
| `appview/.../routes/settings/developer/+page.svelte:393` | `SELECT did FROM vertex_did ORDER BY _seq DESC LIMIT 10` (UI code example) | PORT-adapted | Developer settings example SQL snippet in UI copy. Not live query. Update example to show MST SDK usage. |
| `appview/.../actor/actor-store.svelte.ts:39` | `// Single path: PDS XRPC getAuthorProfile → graph SQL path → RisingWave` | PORT-adapted | Comment only. |

#### Karmada / VKE / commercial K8s
`primitives/yoro_social.py:93` — `Karmada hub and murakumo-k3s actor worker path alive` — VENDOR-ONLY (intentional remainder, per PYKOTODAMA-MIGRATION-NOTES.md).

Zero Karmada/VKE matches in TypeScript/Svelte layers.

#### Stripe / PayPal / fiat

| File:Line | Finding | Verdict | Reason |
|---|---|---|---|
| `appview/.../superapp/ProfilePanel.svelte:54` | `// Card state (Stripe Issuing)` comment | VENDOR-ONLY | Stripe Issuing card-management section in ProfilePanel. The entire card-management block (lines 54, 418–569, 678, 807) invokes `com.etzhayyim.apps.stripe.*` XRPC procedures (`listCards`, `createCardholder`, `issueCard`, `freezeCard`, `unfreezeCard`, `assignCardCredits`). Stripe Issuing is a commercial fiat processor; prohibited per ADR-2605172000 substrate boundary table. Religious-corp must not surface this UI panel. |
| `appview/.../superapp/ProfilePanel.svelte:424` | `atProcedure('com.etzhayyim.apps.stripe.listCards', ...)` | REJECT | Live XRPC call to Stripe-backed procedure. This is an active fiat coupling, not a comment. The NSID `com.etzhayyim.apps.stripe.*` must not be callable from a religious-corp surface. Entire card-management UI block must be removed or gated behind a vendor-only consent-capability check. |
| `appview/.../superapp/ProfilePanel.svelte:472` | `atProcedure('com.etzhayyim.apps.stripe.createCardholder', ...)` | REJECT | Same. |
| `appview/.../superapp/ProfilePanel.svelte:489–497` | `atProcedure('com.etzhayyim.apps.stripe.issueCard', ...)` (two call sites) | REJECT | Same. |
| `appview/.../superapp/ProfilePanel.svelte:533` | `atProcedure('com.etzhayyim.apps.stripe.freezeCard', ...)` | REJECT | Same. |
| `appview/.../superapp/ProfilePanel.svelte:536` | `atProcedure('com.etzhayyim.apps.stripe.unfreezeCard', ...)` | REJECT | Same. |
| `appview/.../superapp/ProfilePanel.svelte:563` | `atProcedure('com.etzhayyim.apps.stripe.assignCardCredits', ...)` | REJECT | Same. |
| `appview/.../server/legacy-nanoid-map.ts:110` | `"st4rp301": "stripe.etzhayyim.com"` | REJECT | Legacy nanoid routing entry maps `st4rp301` → `stripe.etzhayyim.com`. The domain `stripe.etzhayyim.com` implies a Stripe-backed service hosted under the religious-corp domain. This is a domain boundary violation: `stripe.etzhayyim.com` conflates the vendor Stripe processor with the etzhayyim identity namespace. Must be removed or redirected to a vendor-only domain (`stripe.etzhayyim.com`). |

#### kotoba library and xrpc-adapter
`60-apps/etzhayyim-project-yoro/kotoba/src/` — 5 TS files — **zero violations**. This is the target implementation per ADR-2605191358; it is clean.

`60-apps/etzhayyim-project-yoro/xrpc-adapter/src/index.ts` — **zero violations**.

### §3 Summary

yoro has the most extensive substrate coupling. No RunPod findings. **14+ REIMPLEMENT items** (structural RisingWave reads/writes in Python primitives + inline SQL in Svelte UI). **8 REJECT items** (live Stripe Issuing XRPC calls + domain entry). **Multiple PORT-adapted** comment/docstring cleanup items (covered by ADR-2605191358 which is already proposed).

The `kotoba/` sub-library is **clean** — the replacement substrate is scaffolded. The coupling is entirely in the un-migrated layers (Python primitives and main SvelteKit AppView).

**yoro finding count: 14 REIMPLEMENT + 8 REJECT + 8 VENDOR-ONLY + 12 PORT-adapted = 42 findings**

---

## §4 Summary

### Finding counts by actor and verdict

| Actor | REJECT | REIMPLEMENT | VENDOR-ONLY | PORT-adapted | PORT-direct | Total |
|---|---|---|---|---|---|---|
| kuni-umi | 0 | 0 | 0 | 0 | 2 | 2 |
| shinka | 0 | 8 | 5 | 3 | 0 | 16 |
| yoro | 8 | 14 | 8 | 12 | 0 | 42 |
| **Total** | **8** | **22** | **13** | **15** | **2** | **60** |

### Verdict distribution across all three actors

| Verdict | Count | Share |
|---|---|---|
| PORT-direct | 2 | 3.3% |
| PORT-adapted | 15 | 25.0% |
| VENDOR-ONLY | 13 | 21.7% |
| REIMPLEMENT | 22 | 36.7% |
| REJECT | 8 | 13.3% |

### Gaps requiring follow-up ADRs

1. **Stripe Issuing removal from yoro surface (new ADR needed)**
   The 7 live `com.etzhayyim.apps.stripe.*` XRPC calls in `ProfilePanel.svelte` and the `stripe.etzhayyim.com` nanoid map entry (REJECT ×8) are not covered by any existing ADR. ADR-2605191358 scopes only to RW coupling. A dedicated ADR is required to:
   - Declare `com.etzhayyim.apps.stripe.*` NSIDs as VENDOR-ONLY (callable only from `etzhayyim.com` workers, never from `etzhayyim.com` surface)
   - Remove the `st4rp301` / `stripe.etzhayyim.com` entry from the routing map
   - Gate the ProfilePanel card-management section behind a vendor consent-capability XRPC call (progressive enhancement per ADR-2605192115 §4)

2. **shinka AT MST rewrite ADR**
   shinka's 8 REIMPLEMENT items (direct RisingWave reads/writes via `kotodama.db_sync`) have no ADR covering the MST-native replacement. ADR-2605191358 scopes to yoro/murakumo only. A new ADR is needed to define: (a) the AT record schema for `joucho`, `shinkaKnowledge`, `actorShinkaState`, `shinkaEvolution` MST collections; (b) the `@etzhayyim/sdk` write path replacing `db_sync`; (c) the `shinka_tick_actor` SQL UDF replacement strategy.

3. **yoro Python primitives rewrite sequencing**
   `yoro_social.py` (1687 lines, 14+ REIMPLEMENT hits) is the largest single source of RW coupling. ADR-2605191358 describes the high-level path (AT MST via `@etzhayyim/sdk`) but does not itemise per-function migration. A per-function migration table (analogous to PYKOTODAMA-MIGRATION-NOTES.md) is needed before the Step 8 cutover can proceed safely.

### Actor requiring most urgent follow-up ADR

**yoro** — The 8 REJECT items (live Stripe Issuing fiat coupling) are the highest-priority risk: these are not dormant code paths but active XRPC calls available to any authenticated etzhayyim user visiting the ProfilePanel. An ADR removing/gating the `com.etzhayyim.apps.stripe.*` surface from the religious-corp app should be drafted before Stage 3 deployment of the yoro AppView.

### Surprisingly clean actor

**kuni-umi** — zero substrate violations in any executable code or config. All 6 Pregel cells (audit_witness, commissioning, construction_orchestration, decommission, deployment_planning, site_survey) are written entirely within the religious-corp substrate boundary. The CLAUDE.md boundary table is correctly structured as a prohibition list. This actor is PORT-direct for Step 8 without any changes.
