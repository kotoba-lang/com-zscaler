# 20-actors/narashi — CLAUDE.md

> World economic-inequality-indicator observation substrate. Tier-B, R0 design-only. ADR-2607101800.
> Read the `etzhayyim/root` repo-root `CLAUDE.md` first; this file only adds actor-local rules.

## Identity

- **DID**: `did:web:narashi.etzhayyim.com`
- **Glyph**: 均 — *level / equal / average*. Measures the degree of unevenness; does not itself level
  anything (same neutral-measurement register as kanae's 鼎の軽重を問う — "weighing," not "judging").
- **Role**: the inequality-*outcome* sibling of `kanae` (which tracks fiscal *flow*). Reads kanae's
  aid/loan `fundFlowEdge` datoms read-only for G8 non-causal cross-reference; never writes to kanae's
  graph. Lineage: `kanae` (fiscal flow, primary cross-reference peer), `danjo` (non-adjudicating
  engine pattern), `global-energy-datoms` (the "-datoms" projection pattern this actor's ingest cell
  follows, generalized from energy to inequality-outcome indicators).

## What narashi is, in one line

It reads the inequality indicators the World Bank / UN Statistics Division / Our World in Data have
**already published** (Gini, poverty headcount ratio, income share, SDG 10) and registers them as
content-addressed Datoms. It is **not** a redistribution mechanism, a policy advisor, or a
country-ranking tool.

## Hard rules (constitutional — do not weaken)

1. **Primary published indicators ONLY (G3).** Ingest only pre-published, IPFS-pinned World Bank WDI /
   UN SDG API / Our World in Data snapshots (the same DataLad raw datasets `org.worldbank.api` /
   `org.un.unstats` / `org.ourworldindata` already carry for energy indicators). No live scraping, no
   per-query API, no non-public sources.
2. **Non-adjudicating (G4).** narashi records disclosed metric values + their trend. It does **not**
   rank jurisdictions "good/bad," assign blame for a Gini level, or recommend policy. The `indicator`
   enum on `metricObservation` carries no merit/ranking token.
3. **Non-causal cross-reference (G8).** When `narashi_cross_reference` juxtaposes a `metricObservation`
   trend against a kanae `fundFlowEdge` (aid-disbursement/loan/intergovernmental-transfer) in the same
   jurisdiction, the resulting `crossReferenceNote.causalClaim` is a required schema `const false`.
   narashi never asserts that a fund flow caused (or failed to cause) an inequality-metric change.
4. **Not a redistribution mechanism (N1).** That role is reserved to the Liberation Ladder
   (ADR-2605261000 in `etzhayyim/root`), scoped to Adherent SBT holders only. narashi has no Public
   Fund allocation, moves no funds, and does not extend Liberation Ladder benefits to anyone.
5. **Sourcing honesty (G5).** Every `metricObservation` carries `sourceRecordCids[]` with ≥2 upstream
   CIDs, or exactly 1 with `singleSourced=true` explicit (some indicator-jurisdiction-year triples are
   published by only one Tier-A source — state that, never hide it).
6. **kotoba-native (substrate boundary).** State = kotoba Datom log. No SQL / RisingWave / Lance as
   canonical store (G2).
7. **Murakumo-only (G7).** Any LLM narration routes through the Murakumo fleet (ADR-2605215000).
8. **Aggregate-only (G9).** Never ingests or derives individual/household-level data — WDI/SDG/OWID
   indicators are national/sub-national aggregates by construction; this is stated as an explicit
   invariant, not left implicit.
9. **Outward-gated activation (G-council).** All 3 cells are R0 path-reserved and raise on first
   invocation until Council Lv6+ ≥3 ratification + 30-day public objection period (ADR-2607101800 §7).

## Vocabulary

`00-contracts/lexicons/com/etzhayyim/narashi/` (in `etzhayyim/root`):
- `metricObservation` — one `(indicator, jurisdiction, period, value, sourceRecordCids[], methodNoteCid)`
  fact. `indicator` ∈ `{gini, poverty-headcount-ratio-international, poverty-headcount-ratio-national,
  income-share-bottom40, income-share-top10, sdg10-shared-prosperity-premium}`.
- `crossReferenceNote` — narashi-side pairing with a kanae `fundFlowEdge`; `causalClaim` const `false`.
- `metricNarrative` — Murakumo-only factual trend description; `nonAdjudicatingNotice` const `true`,
  `murakumoInferenceAttestation` required.
- `methodNote` — open, versioned normalization heuristic (G6).

## Cells (R0 path-reserved — all raise until Council activation)

- `narashi_metric_ingest` (reuben, continuous) — WDI/SDG/OWID snapshot → kotoba EAVT `metricObservation`.
- `narashi_cross_reference` (reuben, periodic) — `metricObservation` + kanae `fundFlowEdge` (read-only)
  → `crossReferenceNote` (G8).
- `narashi_narrative` (gad, periodic) — `metricObservation` subgraph → Murakumo `metricNarrative` (G4+G7).

## Build & Deploy

**R0 status**: Scaffold only. No live ingest, no live actuation.

```bash
bb run_tests.clj
```

## Related Files

- `manifest.jsonld` — DID + cell registry + gates G1–G9 + non-goals N1–N8
- `/90-docs/adr/2607101800-narashi-global-inequality-observation-tier-b-actor-r0.md` — Master ADR
  (in `etzhayyim/root`)
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — Liberation Ladder (the N1
  boundary this actor does not cross)
- `/90-docs/adr/2605302300-kanae-global-fiscal-flow-visualization-tier-b-actor-r0.md` — kanae (primary
  cross-reference peer)
- `/CLAUDE.md` (in `etzhayyim/root`) — Religious-corp status table
