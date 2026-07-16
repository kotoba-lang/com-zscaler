# DMN: counterparty-classification

Phase 2 gate at `proposeDeploymentPlan`. Applied to every supplier / labor / land-owner counterparty in the BoM.
Per [ADR-2605201400](../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §5.2.

## Inputs (per counterparty)
- `counterpartyDid`
- `role` — `supplier-material` / `supplier-service` / `labor` / `land-owner-co-grant` / `transport`
- `unspscCommodityCodes[]` — relevant codes from BoM

## Decision table

| # | Condition | Action |
|---|---|---|
| 1 | `ChartersComplianceRegistry.isNonAligned(counterpartyDid)` = true | **REJECT** |
| 2 | counterparty is a state-affiliated military supplier (per maintained `MilitarySupplierRegistry` snapshot CID) | **REJECT** — `mission.no_state_military_alliance` |
| 3 | counterparty IP / licensing terms are proprietary closed-design (no open-source equivalent BoM line) | **ESCALATE** — Council Lv6+ may approve for safety-critical items where no FOSS-RIDER-compatible alternative exists; otherwise prefer alternate supplier |
| 4 | UNSPSC agent reports any commodity has `dualUse=high` | **ESCALATE** — proportionality + dual-use analysis by Council Lv6+ |
| 5 | `role=labor` and labor counterparty's `Phenotype.effectiveMultiplier` < 0.5 | **WARN** — proceed but note in Phenotype feedback chain |
| 6 | otherwise | **ACCEPT** |

## Rationale
- Rule 1 — three-tier enforcement L2 (benefit refusal) per ADR-2605192230 — religious-corp does not transact economic benefit to Non-Aligned actors via deployment BoM.
- Rule 2 — `mission.no_state_military_alliance = true` per ADR-2605192100 §1.12.B + ADR-2605192315.
- Rule 3 — preference for open-source per ADR-2605192200 Charter Rider; Council escalation acknowledges legitimate exceptions (e.g., certified safety PLC chips per open-ot scope).
- Rule 4 — Charter Rider §2(a) weapons gate via UNSPSC dual-use flag.
- Rule 5 — Phenotype is feedback, not gate; rule documents the warning for transparency.

## Implementation
Same as `jurisdiction-eligibility.md` — Rego policy + LangGraph node call.
