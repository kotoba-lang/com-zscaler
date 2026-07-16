# kenchi (検地) — Worldwide Real-Estate Valuation (External-Market Transparency)

**DID**: `did:web:kenchi.etzhayyim.com` · published `did:web:etzhayyim.github.io:com-etzhayyim-kenchi`
**Namespace**: `com.etzhayyim.kenchi.*`
**ADR**: ADR-2606272030 (R0 scaffold)
**Status**: R0 scaffold (2026-06-27) — 6 cells path-reserved + 4 Lexicon skeletons
**Parent ADRs**: ADR-2605192245 (Land Trust sovereignty — the inalienable-land boundary), ADR-2605192330 (extended land sovereignty), ADR-2605262900 (toritate — sibling boundary)
**Implementation**: `git@github.com:com-junkawasaki/kenchi-clj` (langgraph-clj engine)

> **検地** (*Taikō Kenchi*) — the cadastral land survey that first measured and
> ledgered land value across an entire realm. This actor is that survey for the
> planet's **external market**: measure every parcel from every open authority,
> reconcile the disagreement, and write a provenance-stamped result to an open
> ledger — while leaving the religious-corp's own **inalienable** Land Trust
> deliberately unpriced (it has no market price, by constitution).

## Overview

Public-good worldwide real-estate valuation + transparency substrate. It fuses
many *disagreeing* independent authorities — recorded sales (HM Land Registry,
MLIT 不動産取引価格情報), public assessments/indices (OECD, BIS), and open-crawl
listing prices (Common Crawl) — into provenance-stamped, uncertainty-quantified
**external-market** valuations and aggregate region reports. No single source is
trusted; the £ point is anchored only by recorded comps, indices merely
corroborate, and the publish gate refuses false precision.

## Identity (CRITICAL — IMMUTABLE)

- **PROVENANCE-OR-SILENCE** (G3) — never publish a point without **≥3 independent
  recorded comps** AND **≥2 independent authorities** AND a fresh anchor. Below
  threshold → a wide MRV band or `insufficient-evidence`, never false precision.
- **DERIVED / AGGREGATE license discipline** (G4) — ToS-restricted inputs publish
  **derived-only**; per-parcel-barred jurisdictions publish **aggregate-only**
  (`regionReport`). Raw republication of restricted data is PROHIBITED.
- **INALIENABLE-LAND EXCLUSION** (G5) — kenchi **NEVER** assigns a market price to
  the etzhayyim Land Trust's inalienable holdings (ADR-2605192245 — inalienable
  land has no market price). kenchi values the **external** market only; the
  internal commons-asset value (non-market imputed STOCK, ACCESS-NOT-TITLE) is
  `toritate.commons_asset_value`, not kenchi.
- **NO-PII** (G6) — a parcel is a place, not a person. No owner linkage, no
  individual wealth surveillance.
- **Murakumo-only inference** (G7) — ToS-restricted inputs never leave the mesh;
  only derived records are published.
- **Open-source + open-data-first** (G8) — no closed AVM / closed tooling.

## 6 Pregel Cells (R0 path-reserved over the engine)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `kenchi_ingest` | naphtali | continuous | multi-authority fetch + open-crawl listings → stamped Observations |
| `kenchi_fusion` | naphtali | continuous | robust price-comp ensemble + CI; indices corroborate only |
| `kenchi_provenance_governor` | naphtali | continuous | G3 gate + outlier reject + license clear → publish \| MRV |
| `kenchi_publish` | judah | continuous | cleared record → PDS/Aozora + kqe-assert! to Datom |
| `kenchi_region_aggregate` | judah | continuous | H3 median+quartiles → regionReport (AGGREGATE-ONLY) |
| `kenchi_flywheel` | judah | periodic | backtest vs realized sales → recalibrate reliability |

## 4 Lexicons (`com.etzhayyim.kenchi.*`)

`valuation` · `regionReport` · `provenanceAttestation` · `sourceLicense` —
under `00-contracts/lexicons/com/etzhayyim/kenchi/`.

## Sibling boundary — toritate (執帳)

kenchi (external market) and toritate (internal on-chain accounting + the
inalienable Land Trust's non-market commons-asset value) share a **one-way**
boundary: kenchi may provide external comparables for Land Registry acquisition
due-diligence, but the moment land enters the inalienable Trust it leaves
kenchi's scope entirely (G5 / toritate N12). The two actors never double-count.

## Tests

`bb run_tests.clj` → `methods/test_charter_gates.clj` pins the gates at the schema
layer (PROVENANCE-OR-SILENCE, derived/aggregate license, NO-PII, inalienable-land
exclusion, no single-source oracle). Pure stdlib, standalone-runnable.
