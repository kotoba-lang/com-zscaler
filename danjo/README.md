# danjo (弾正) — Public-Accountability Oversight Substrate

**DID**: `did:web:danjo.etzhayyim.com`
**Namespace**: `com.etzhayyim.danjo.*`
**ADR**: ADR-2605301600 (R0 scaffold)
**Status**: R0 scaffold (2026-05-30) — 6 cells path-reserved + 4 Lexicon skeletons
**Primary input**: `com.etzhayyim.gov.dataset.*` corpus (ADR-2605263900)
**Parent ADRs**: ADR-2605263900 (open-gov corpus), ADR-2605262130 (kotoba), ADR-2605192100 (Mission Charter §1.12), ADR-2605192200 (Charter Rider), ADR-2605192300 (Council 5-of-7), ADR-2605215000 (Murakumo-only inference)

## Overview

danjo is the **kotoba-native public-accountability oversight substrate**.
It ingests the **already-IPFS-pinned** open-government corpus — for Japan:
**国会会議録** (Diet statements), **予算書** (accounting / budget) and
**政府調達** (procurement) per ADR-2605263900 — into **kotoba EAVT**, and
emits **factual, source-cited, NON-adjudicating discrepancy observations**
plus periodic aggregate transparency reports.

**Observation scope includes 大麻政策立法過程** (cannabis-policy legislative
process) as a **non-adjudicating, both-views-neutral** index — the
`cannabis-policy-legislative-trace` method (`methods/v1-jp-seed.json`) traces
国会会議録 mentions + the 大麻取締法 → 大麻草の栽培の規制に関する法律 (令和5年法律第84号)
statutory change into a source-cited timeline that produces datoms only, takes
**no 推進/反対 stance** (G4), and routes legal characterization to chigiri. It is the
state-watching companion to the nusa (幣) heritage actor (ADR-2606039800) and
moushibumi's neutral public-comment support (ADR-2605312400).

> **The censor's eye, never the censor's sword.** The name 弾正 evokes the
> Nara/Heian 律令制 Censorate (弾正台 Danjōdai) that monitored official
> misconduct — but §1.12 / G11 strip the historical coercive power
> entirely. danjo observes and publishes; it holds no coercive power,
> refers to no state coercion as an internal dependency, and adjudicates
> nothing.

## Identity (CRITICAL — IMMUTABLE)

- **NON-adjudicating** (G4; UPL-equivalent, like chigiri G14 / toritate
  G5) — danjo emits FACTUAL cross-reference observations only. It MUST
  NOT assert that a crime / law violation / 不正 occurred. Every
  `discrepancyObservation` carries `nonAdjudicatingNotice=true`. Legal
  characterization is routed to external counsel via chigiri + Public
  Fund (Council Lv6+).
- **Passive-only ingestion** (G3) — danjo reads ONLY the pre-published,
  IPFS-pinned `gov.dataset.*` corpus. NO live portal scraping, NO
  per-query API hits, NO non-public sources, NO whistleblower intake
  (Charter Rider §2(c) covert-ops avoidance). danjo does **not** re-fetch
  from government portals — `kotodama.organism.sensors.gov.*` already
  did that, passively, upstream.
- **Source-provenance mandatory** (G5) — every observation cites ≥2
  upstream `gov.dataset.*` record CIDs. No inference-only allegation.
- **Open method** (G6) — every detector heuristic is published as a
  `methodNote` (open, versioned). No closed / secret scoring; the public
  can audit the detector itself, not only its output.
- **Transparent Religious Force discipline** (G11; §1.12) — observation +
  transparent publication ONLY. No coercion, no state-coercion
  dependency, no covert operation. 1 SBT = 1 vote governs named-party
  publication.

## 6 Pregel Cells (R0 path-reserved)

All cells path-reserved under `40-engine/kotoba/crates/kotoba-kotodama/cells/danjo_*/`. Cell
modules created at R1 ratification, import-time
`RuntimeError("danjo R0 scaffold: activate via Council ADR + R1 ratification")`.

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `danjo_diet_statement_index` | reuben | continuous | `gov.dataset.parliamentRecord` (JP 国会会議録) → kotoba EAVT datoms (member ↔ statement ↔ topic ↔ session ↔ date) |
| `danjo_procurement_graph` | reuben | continuous | `gov.dataset.procurementRecord` (JP 政府調達) → datoms (authority ↔ award ↔ awardeeLei ↔ amount ↔ date) |
| `danjo_budget_ledger` | reuben | continuous | `gov.dataset.budgetRecord` (JP 予算書) → datoms (appropriation ↔ outlay ↔ recipientLei ↔ program) |
| `danjo_crossref_engine` | gad | continuous | join indices + `corp.ownershipEdge` (UBO) + `corp.leiReference` → `crossReferenceLink` + candidate `discrepancyObservation` |
| `danjo_statement_consistency` | gad | continuous | Diet statements vs budget/procurement reality → `discrepancyObservation` (statement-vs-record divergence) |
| `danjo_oversight_report` | naphtali | periodic (event) | aggregate observations → `oversightReport` + Council Lv6+ ≥3 attestation |

The cross-reference graph lives in **kotoba QuadStore (EAVT)** per
ADR-2605262130. No RisingWave, no projection layer (same discipline as
the tadori sibling, ADR-2605301400).

## 4 Lexicons under `com.etzhayyim.danjo.*`

| Lexicon | Description |
|---|---|
| `discrepancyObservation` | Factual, source-cited, NON-adjudicating anomaly. `severity` enum; `sourceRecordCids[]` (≥2, G5); `methodNoteCid` (G6); `nonAdjudicatingNotice=true` (G4) |
| `crossReferenceLink` | Typed factual edge between two gov.dataset records (or a gov.dataset record and a corp registry entity), citing the public basis of the link |
| `oversightReport` | Periodic aggregate transparency report; Council Lv6+ ≥3 attestation chain; IPFS-pinned (replication ≥2) |
| `methodNote` | Open, versioned definition of one detector heuristic (the public can audit the detector) |

See `/00-contracts/lexicons/com/etzhayyim/danjo/README.md` for canonical schemas.

## Constitutional Gates (G1–G13) — IMMUTABLE R0–R3

See ADR-2605301600 §4. Key: **G3** passive-only ingestion · **G4**
non-adjudicating (UPL-equivalent) · **G5** source-provenance mandatory ·
**G6** open method · **G8** no commercial gov-intel terminals · **G10**
aggregate-first + severity-gated naming · **G11** Transparent Religious
Force discipline.

## Non-Goals (N1–N12) — EXCLUDED from R0–R3

See ADR-2605301600 §5. Key: NOT a prosecutor (N1) · NOT a court /
adjudicator (N2) · NOT surveillance (N3) · NOT a whistleblower intake
(N5) · NOT a replacement for 会計検査院 (N10) · NOT a defamation vector
(N11).

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-30 | Scaffold (this commit) |
| **R1** | post-Bootstrap-Council ratify | 3 ingest cells build kotoba EAVT datoms over the JP corpus; crossReferenceLink + methodNote schemas reviewed; NO named allegations |
| **R2** | post-R1 + 30-day public objection | +crossref_engine + statement_consistency; first (aggregate) discrepancyObservation records |
| **R3** | post-R2 + Council Lv7+ unanimity | +oversight_report; first aggregate oversightReport (JP FY); named-party path (G10) battle-tested; multi-jurisdiction extension |

## Cross-actor Relationships

| Actor / substrate | Direction | Purpose |
|---|---|---|
| `gov.dataset.*` (ADR-2605263900) | → (read) | Primary input: parliamentRecord / budgetRecord / procurementRecord / statisticsObservation / openDatasetAttestation |
| `corp.{leiReference,ownershipEdge}` (ADR-2605263800) | → (read) | Entity / UBO resolution for cross-reference links |
| `toritate` (ADR-2605262900) | ↔ | **Boundary**: toritate = religious-corp's OWN on-chain books; danjo = the STATE's published books. Cross-reference where a vendor appears in both |
| `chigiri` (ADR-2605262700) | → | Legal-characterization + external-counsel routing (UPL boundary, G4); `chigiri.data_privacy` for DSARs (G9) |
| `ossekai` (ADR-2605264000) | → | danjo `oversightReport` → ossekai aggregate-anonymized §1.12 publication |
| `kataribe` (press) | → | danjo `oversightReport` is a citable primary source for press / publishing |
| `kotoba` (ADR-2605262130) | ↔ | EAVT QuadStore stores the cross-reference graph; kotoba-kqe arrangements for hot-path |
| `tadori` (ADR-2605301400) | ∥ | Sibling kotoba-native investigation actor (on-chain crypto tracing); shared EAVT pattern, disjoint domain |

## R0 Status

**Scaffold only.** No cells exist yet (W1). No ingestion runs, no
observations are produced, no inference occurs until Council
ratification. Lexicon schemas are skeleton only — required-field
validation lands at R1 Council attestation review.

## Related Files

- `/20-actors/danjo/manifest.jsonld`
- `/20-actors/danjo/CLAUDE.md`
- `/20-actors/danjo/methods/` (open, versioned detector heuristics — `v1-jp-seed`, 6 seeds; G6 open method)
- `/70-tools/scripts/lint/no-danjo-adjudication.mjs` (G4 + G8 constitutional lint, green at R0)
- `/00-contracts/lexicons/com/etzhayyim/danjo/` (4 Lexicons + README)
- `/90-docs/adr/2605301600-danjo-public-accountability-oversight-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605263900-public-data-open-government-ipfs-ingestion.md` — open-gov corpus (primary input)
- `/90-docs/adr/2605262130-kotoba-storage-substrate-unification.md` — kotoba substrate
- `/90-docs/adr/2605192100-etzhayyim-mission-charter.md` — §1.12 Transparent Religious Force + §2(c) covert-ops avoidance
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — toritate (boundary sibling)
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — chigiri (UPL boundary)
- `/90-docs/adr/2605301400-tadori-onchain-tracing-actor-and-kotoba-eavt-migration.md` — tadori (kotoba-native sibling)
- `/CHARTER-RIDER.md` §2 — 8 prohibited categories (esp. §2(c) covert-ops + §2(e) anti-gatekeeping)
- `/CLAUDE.md` — Religious-corp status table
