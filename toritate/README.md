# toritate (執帳) — Non-profit Religious-Corp Accounting + Audit Substrate

**DID**: `did:web:toritate.etzhayyim.com`
**Namespace**: `com.etzhayyim.toritate.*`
**ADR**: ADR-2605262900 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 5 Lexicon skeletons
**Cross-actor sibling**: chigiri (ADR-2605262700; tax_receipt boundary, 2-way)
**Parent ADRs**: ADR-2605192145 (Public Fund), ADR-2605192130 (Tithe), ADR-2605192300 (Council 5-of-7), ADR-2605192245 (Land Trust), ADR-2605172100 (Payments on-chain)

## Overview

Religious-corp accounting + audit substrate. Aggregates on-chain
financial flows (TitheRouter + Public Fund Safe + Council Safe + Land
Registry) into transparent reports, runs continuous categorization +
anomaly detection on the on-chain ledger, supports annual transparency
reports for member + public consumption, and orchestrates data
preparation for external auditor engagement when jurisdictional
compliance requires it.

## Identity (CRITICAL — IMMUTABLE)

- **100% on-chain transparency** (G3 + G4) — toritate MUST NOT maintain
  an off-chain primary ledger. All financial state derives from
  on-chain transactions on Base L2.
- **No commercial accounting software** (G8) — QuickBooks / Xero /
  FreeAgent / Wave / FreshBooks / Sage / Zoho Books are PROHIBITED
  per Charter Rider §2(e) anti-gatekeeping + §2(c) vendor data-
  sovereignty (vendor closed query-tracking exposes member financial
  posture).
- **No tax advice** (G5; UPL-equivalent) — toritate does NOT render
  tax advice or accounting opinion. External opinion (e.g., US
  501(c)(3) equivalent-determination opinion letter) is contracted
  through Public Fund Safe per Council Lv6+ approval. Toritate
  prepares the data package; the opinion is rendered by external
  counsel.
- **No payroll** (G12; per ADR-2605262700 G13) — volunteer ≠ employee
  per Liberation Ladder L0..L6. Subsistence flow ≠ wage. `payroll` is
  NOT a valid `ledgerEntry.category` enum value.

## 6 Pregel Cells (R0 path-reserved)

All cells path-reserved under `40-engine/kotoba/crates/kotoba-kotodama/cells/toritate_*/`.
Cell modules created at R1 ratification, import-time
`RuntimeError("toritate R0 scaffold: activate via Council ADR + R1 ratification")`.

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `toritate_tithe_accounting` | gad | continuous | TitheRouter tx → income summary entry |
| `toritate_public_fund_accounting` | gad | continuous | Public Fund Safe disbursement → grant summary entry |
| `toritate_council_compensation` | gad | continuous | Council Safe tx → operational expense (typically zero) |
| `toritate_steward_subsistence_accounting` | gad | continuous | chigiri.stewardLaborAttestation → L0..L6 subsistence flow summary |
| `toritate_transaction_ledger` | reuben | continuous | raw on-chain Base L2 tx parsing + categorization |
| `toritate_annual_audit_report` | reuben | annual (event) | aggregate cells (1-5) → annual transparency report + Council ≥3 attestation |

## 5 Lexicons under `com.etzhayyim.toritate.*`

| Lexicon | Description |
|---|---|
| `financialAttestation` | Per-period (daily / monthly / quarterly / annual) summary attestation |
| `ledgerEntry` | Single on-chain transaction; category enum; amount; counterparty DID; supporting CID |
| `annualReport` | Annual transparency report; Council ≥3 attestation chain |
| `auditObservation` | Anomaly / finding; routes to Council mediation if critical |
| `externalAuditorEngagement` | External auditor contract record (Public Fund Safe contract CID + scope + Council Lv6+) |

See `/00-contracts/lexicons/com/etzhayyim/toritate/README.md` for canonical schemas.

## Constitutional Gates (G1–G12) — IMMUTABLE R0–R3

See ADR-2605262900 §5. Key:

- **G3** 100% on-chain transparency
- **G4** USDC + Base L2 canonical SoT
- **G5** UPL-equivalent (no accounting / tax opinion)
- **G6** Annual audit ≥3 Council Lv6+ attestations
- **G7** Murakumo-only inference
- **G8** No commercial accounting software (QuickBooks / Xero / FreeAgent / Wave / FreshBooks / Sage / Zoho Books PROHIBITED)
- **G10** No donor PII (aggregate + pseudonymous DIDs only)
- **G11** Read-only on financial chain (no transaction approval)
- **G12** No payroll category

## Non-Goals (N1–N12) — EXCLUDED from R0–R3

See ADR-2605262900 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Bootstrap-Council ratify | 3 core cells (transaction_ledger / tithe_accounting / public_fund_accounting) + monthly summary reports begin |
| **R2** | post-R1 + 30-day public objection | +3 cells + first annual transparency report (CY 2026) |
| **R3** | post-R2 + Council Lv7+ unanimity | All 6 cells + external auditor engagement framework battle-tested |

## Cross-actor Relationships

| Actor | Direction | Purpose |
|---|---|---|
| `chigiri.tax_receipt` | ↔ | Donor-side tax receipt boundary; cross-link via TitheRouter tx CID |
| `chigiri.ip_licensing` | ← (read) | External counsel contract events (disbursement records) |
| TitheRouter | → (read) | On-chain income source |
| Public Fund Safe | → (read) | On-chain grant disbursement source |
| Council Safe | → (read) | On-chain operational expense source |
| Land Registry | → (read) | Land acquisition records |
| chigiri.stewardLaborAttestation | → (read) | L0..L6 classification for subsistence flow categorization |
| `danjo` (ADR-2605301600) | ↔ | **Boundary**: toritate audits the religious-corp's OWN on-chain books; danjo audits the STATE's published open-data books (国会会議録 / 予算書 / 政府調達). Cross-reference where a vendor appears in both (toritate's tithe-recipient set ∩ danjo's procurement/budget recipients). |

## R0 Status

**Scaffold only.** No cells exist yet (W1). Lexicon schemas are
skeleton only — required-field validation lands at R1 Council
attestation review.

## Basic High Income valuation (ADR-2605301020)

toritate is the accounting SSoT for the **Basic High Income** doctrine — the
non-cash income/asset figure (imputed income FLOW + commons-asset access STOCK)
published in `com.etzhayyim.liberation.metricReport.basicHighIncome`. Two
R0-path-reserved cells compute it (`toritate_imputed_income_compute` +
`toritate_commons_asset_value`) against open, method-versioned reference tables in
`20-actors/toritate/valuation/` (`v1-retail-equiv` draft 雛形). No cash is ever
transferred (`cashStipendUsd ≡ 0`, N1); figures are aggregate-only (no
per-adherent leaderboard). See `valuation/README.md`.

## Related Files

- `/20-actors/toritate/manifest.jsonld`
- `/20-actors/toritate/CLAUDE.md`
- `/20-actors/toritate/valuation/` (Basic High Income reference price tables — ADR-2605301020)
- `/00-contracts/lexicons/com/etzhayyim/toritate/` (5 Lexicons + README)
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md`
- `/CHARTER-RIDER.md` §2 — 8 prohibited categories (esp. §2(e) anti-gatekeeping + §2(c) covert-ops vendor)
- `/CLAUDE.md` — Religious-corp status table
