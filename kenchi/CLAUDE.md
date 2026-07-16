# 20-actors/kenchi — CLAUDE.md

## Identity

- **Name**: kenchi (検地 — Taikō Kenchi, the realm-wide cadastral land-value survey)
- **DID**: `did:web:kenchi.etzhayyim.com` (published `did:web:etzhayyim.github.io:com-etzhayyim-kenchi`)
- **ADR**: ADR-2606272030 (R0 scaffold, 2026-06-27)
- **Parent ADRs**: ADR-2605192245 (Land Trust sovereignty), ADR-2605192330 (extended land sovereignty), ADR-2605262900 (toritate sibling), ADR-2605192200 (Charter Rider), ADR-2605215000 (Murakumo-only inference)
- **Sibling**: toritate (執帳) — internal on-chain accounting + non-market commons-asset value
- **Status**: R0 scaffold — 6 cells path-reserved + 4 Lexicon skeletons
- **Implementation**: `com-junkawasaki/kenchi-clj` (langgraph-clj engine)
- **Form**: 公共財 external-market valuation + transparency substrate (NOT a brokerage / AVM vendor / appraisal firm / 法人格)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

kenchi is an **external-market** real-estate valuation + transparency substrate,
NOT a brokerage, NOT a closed AVM, NOT an advice renderer. Six discipline
boundaries are structural:

1. **PROVENANCE-OR-SILENCE (G3)** — no point estimate without ≥3 independent
   recorded comps AND ≥2 independent authorities AND a fresh anchor. Indices
   corroborate but never vote the £ level. Below threshold → MRV band /
   `insufficient-evidence`. The gate is the product.
2. **DERIVED / AGGREGATE license (G4)** — ToS-restricted inputs publish
   derived-only; per-parcel-barred jurisdictions publish aggregate-only
   (`regionReport`). Raw republication of restricted data PROHIBITED.
3. **INALIENABLE-LAND EXCLUSION (G5)** — the load-bearing etzhayyim boundary.
   kenchi MUST NOT assign a market price to the Land Trust's inalienable
   holdings (ADR-2605192245 — inalienable land has no market price). `assetClass`
   is exactly `external-market`. The internal commons-asset value (non-market
   imputed STOCK, ACCESS-NOT-TITLE) belongs to `toritate.commons_asset_value`.
4. **NO-PII (G6)** — a parcel is a place, not a person. No owner field, no
   individual wealth surveillance.
5. **Murakumo-only inference (G7)** — ToS-restricted inputs never leave the mesh.
6. **Open-source + open-data-first (G8)** — no closed AVM / closed tooling.

## Why a separate actor from toritate

toritate already carries N12 ("NOT a Land Trust valuation engine — inalienable
land has no market price") and the commons-asset STOCK. kenchi is its mirror for
the **outside world**: it values the external market (for transparency + Land
Registry acquisition due-diligence), and the G5 exclusion guarantees the two
never collide on the inalienable Trust. External price ≠ internal access value.

## Editing rules

- The lexicons live in `00-contracts/lexicons/com/etzhayyim/kenchi/`. The
  charter-gate test (`methods/test_charter_gates.clj`) pins the gates against
  them — keep them in sync. Run `bb run_tests.clj` before committing.
- Never add an `owner`/`person`/PII field to `valuation` (G6). Never add a
  `license` value outside `{open, derived-only}` to a *published* record (G4).
  Never broaden `assetClass` beyond `external-market` (G5).
- The compute lives in `com-junkawasaki/kenchi-clj`; this cell is the
  published identity + charter + contracts. Don't fork the engine here.
