# toritate valuation tables — Basic High Income reference prices

**ADR**: ADR-2605301020 (Basic High Income — Imputed-Income + Commons-Asset Doctrine)
**Owner cells**: `toritate_imputed_income_compute` + `toritate_commons_asset_value` (R0 path-reserved; activate at R1)
**Consumed by**: `com.etzhayyim.liberation.metricReport` → `basicHighIncome` block (ADR-2605261000 §4)
**Status**: R0 scaffold — `v1-retail-equiv` is a DRAFT 雛形 pending Council Lv6+ ≥3 attestation

## Purpose

etzhayyim provides adherents a **high standard of living without cash** — "Basic
High Income" (ADR-2605301020). Because no money changes hands, value must be
**imputed**: priced at the market-equivalent the adherent would otherwise pay in
the commercial market etzhayyim routes around. These tables are the open,
method-versioned, Council-attested reference prices that turn in-kind provision
into an auditable USD-equivalent figure.

**The figure is for transparency and accounting only. No USDC is ever transferred
to an adherent on either axis** (ADR-2605261000 §5 N1; funding rail = USDC into
actors / Public Fund, adherent receives services + access).

## Two axes

| Axis | File key | Meaning | Medium |
|---|---|---|---|
| **Imputed income** | `flow` | Market-equivalent annual value of in-kind services *consumed* (food, shelter-service, care, energy, education, mobility, health) | provisioning, not cash |
| **Commons-asset access** | `stock` | Annualized imputed value of SBT-bound **non-alienable access rights** (Land Trust tenure security, actor-mesh productive-surplus access, data substrate, energy infra) | access right, never title |

**Double-counting guard**: FLOW is consumption-in-period; STOCK is secured access
to capital/commons that is *not* a consumption service. A dwelling contributes
`shelter_service` (FLOW, the dwelling-use consumed) and `land_trust_tenure_access`
(STOCK, the inalienable security-of-tenure premium) as **distinct facets**, never
the same dollar twice.

## Non-alienability invariant (CRITICAL)

Every `stock` entry carries `"alienable": false`. Commons-asset access is bound to
the Adherent SBT (soulbound) and can **never** be sold, transferred,
collateralized, or inherited as title (ADR-2605301020 §2). On exit, access
suspends — there is no liquidation event. This generalizes the Land Trust
waqf-inalienability (ADR-2605192245, no `transfer()`/`burn()`/`setOwner()`) to all
commons assets. This is what makes "high income" compatible with the
anti-individualist ontology: **wealth without property, abundance without
accumulation.**

## Method versioning

- Each table is identified by `methodId` (e.g. `v1-retail-equiv`). The
  `metricReport.basicHighIncome.imputedIncomeValuationMethod` field stores this id,
  so every published figure is traceable to the exact reference table + version.
- Tables are **append-only by version**. A new method ships as a new file
  (`v2-*.json`); old versions are retained so historical reports remain reproducible.
- A table is authoritative only when `status == "attested"` and `councilAttestation`
  lists ≥3 Council Lv6+ DIDs. Until then it is `draft-pending-council-attestation`.

## Files

| File | Method | Status |
|---|---|---|
| `v1-retail-equiv.json` | `v1-retail-equiv` — retail-equivalent annualized reference prices | draft 雛形 |

## Constitutional constraints

- **`cashStipendUsd ≡ 0`** — mirrored from the `metricReport` structural invariant.
  No table row is ever a cash payment.
- **Aggregate-only** — reference prices feed median/percentile computation across
  adherents; no per-adherent imputed figure is ever published (ADR-2605301020 §7 +
  ADR-2605261000 N6 — no leaderboard, no class formation).
- **Open + citable sources** — every category must carry a `sourceRef` to an open,
  citable price source before attestation (no proprietary price feeds; Charter Rider
  §2(e) anti-gatekeeping).
- **Murakumo-only inference** (toritate G7) for any model-assisted valuation.

## Related

- `/90-docs/adr/2605301020-basic-high-income-imputed-and-commons-asset-doctrine.md`
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — ladder L0..L6 + N1
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — toritate master
- `/00-contracts/lexicons/com/etzhayyim/liberation/metricReport.json` — `basicHighIncome` consumer
- `/90-docs/adr/2605192245-etzhayyim-global-land-sovereignty.md` — waqf inalienability (generalized here)
