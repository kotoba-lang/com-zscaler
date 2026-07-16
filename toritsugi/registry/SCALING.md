# toritsugi procedure-registry — Scaling Strategy (1,700+ 自治体)

Per ADR-2605312030 §2 (coded procedure registry) + §8 (roadmap). The R0 seed
holds **6 national-template procedures** (`procedures.seed.json`). Japan has
**1,741 市区町村** (as of the seed date; verify — it drifts with 合併), and most
citizen procedures vary per-自治体 in 窓口 / 様式 / 手数料 / online channel. This
file is the **design spec** for how the registry scales from 6 templates to
nationwide coverage **without** violating any R0/constitutional invariant. It is
design-only — no scaled data is shipped at R0.

> **Honest scope**: nothing here is built. This is the curation *plan* the R1+
> `toritsugi_procedure_registry` cell + maintainers will follow. Mapping 1,741
> 自治体 × N procedures is a large living dataset, explicitly **not** an R0/R1
> deliverable (ADR-2605312030 §"Consequences").

## The core problem: national statute vs per-自治体 execution

A procedure has two layers:

- **National layer (statute-level)** — 根拠法令, the procedure's existence, the
  statutory deadline, the document *types* required. Stable; one record per
  procedure nationwide. The 6 seed entries are exactly this layer.
- **Municipal layer (execution-level)** — the concrete 窓口 / 住所 / 様式 PDF /
  online portal URL / 手数料 (varies ±¥100s) / per-自治体 quirks. Volatile;
  potentially 1,741 variants per procedure.

Mixing the two into one flat record (the himotoki target-registry shape) does
**not** scale: 6 procedures × 1,741 自治体 = ~10,000 rows, most differing only
in 窓口/URL, all needing independent G14 verification + freshness tracking.

## Decision: two-tier registry (templateProcedure + municipalBinding)

Split the registry along the layer boundary. **Do not** denormalize.

1. **`procedure` (national template)** — the existing
   `com.etzhayyim.toritsugi.procedure` lexicon, unchanged. One record per
   procedure (the 6 seeds + future national procedures). Holds 根拠法令 /
   statutory deadline / required-document *types* / channel *kind*. The
   `authority` field stays generic (e.g. "市区町村") and `onlineUrl` documents
   that the concrete 窓口 "resolves at guide time" (already the seed convention).

2. **`municipalBinding` (per-自治体 overlay) — FUTURE lexicon (R2+, not yet
   created)** — one record per (procedure × 自治体) that actually needs an
   override: concrete 窓口/住所, 自治体 online-portal URL, 様式 CID, fee, local
   quirks, its own `verificationStatus` + `lastVerified`. Keyed by a stable
   自治体 code (全国地方公共団体コード / JIS X 0402). **Sparse**: only create a
   binding when a member actually needs that 自治体 — never bulk-enumerate all
   1,741 (mirrors himotoki G8 anti-mass-enumeration + toritsugi G12
   data-minimization).

`toritsugi_guide` resolution order: member's 自治体 → look up
`municipalBinding(procedure, 自治体code)` → if present, overlay its concrete
fields on the national `procedure`; if absent, fall back to the national
template + tell the member "窓口は自治体ごとに要確認" (honest, never fabricate a
窓口 — G8).

## Curation strategy (demand-driven, not crawl-driven)

| Principle | Rule |
|---|---|
| **Demand-driven** | A `municipalBinding` is created/verified only when a consenting member in that 自治体 needs that procedure (G12). No speculative nationwide crawl. |
| **Template-first** | National `procedure` template is the floor; a member is always served (template + "要確認") even with zero bindings. Coverage degrades gracefully, never to a hard fail. |
| **Verification inherits VERIFICATION.md** | Each `municipalBinding` runs the same 10-point checklist + `.go.jp`/official-`.lg.jp` provenance fail-closed rule (see `VERIFICATION.md`). 自治体 sources are `*.lg.jp` (local-gov) — extend the provenance allow-list to `.lg.jp` when the binding lexicon lands. |
| **No PII in bindings** | A `municipalBinding` is OPEN procedural data (窓口/様式/手数料) — never member data. Member PII stays in `com.etzhayyim.encrypted.*` (G6) as today. |
| **Freshness budget** | Per-binding `freshnessWindowDays` (180 default). Stale binding ⇒ treated as unverified for dispatch (G14) ⇒ guide falls back to national template, not a stale 窓口. |
| **Source priority** | (1) the 自治体's own `*.lg.jp` page; (2) マイナポータル ぴったりサービス (national online-application aggregator — authoritative for which procedures are e-fileable per 自治体); (3) デジタル庁 / 所管省庁 national page. Never a third-party blog (G8). |

## マイナポータル ぴったりサービス as a scaling lever

For online-fileable procedures, マイナポータル's ぴったりサービス already
normalizes "which 自治体 supports online filing of which procedure" at national
scale. The R2+ `procedure_registry` cell can consult it (read-only, official
source) to populate `municipalBinding.onlineUrl` + capability flags instead of
crawling 1,741 sites — a single authoritative pivot. Still G14-gated
(maintainer-verified before any live submission); still no 代行 without the R3
gate.

## What is explicitly NOT done here (honest)

- The `municipalBinding` lexicon is **not created** (R2+ scope; would be a new
  `com.etzhayyim.toritsugi.municipalBinding` schema with its own invariants test
  + guard checks). Creating it now would ship an unused schema.
- No 自治体 code table, no ぴったりサービス integration, no bindings — all R2+.
- `.lg.jp` is **not yet** in the provenance allow-list of
  `test_seed_all_unverified_and_cited` (the seed is national `.go.jp` only);
  widen it together with the binding lexicon, not before.

## Roadmap alignment (ADR-2605312030 §8)

- **R1** — national `procedure` registry live + verification flow (current shape;
  no municipal layer).
- **R2** — introduce `municipalBinding` lexicon + demand-driven binding creation
  for the first 自治体 a member needs; ぴったりサービス read integration.
- **R3** — multi-jurisdiction (the same template/binding split generalizes:
  template = jurisdiction statute, binding = local execution).
