# DMN — Council Ratification Threshold

Per [ADR-2605201800](../../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md) + [ADR-2605192230](../../../90-docs/adr/2605192230-etzhayyim-three-tier-enforcement-implementation.md).
Gate for `rite_declaration` cell — determines required Council Lv composition to ratify a `declareRite` request.

**Hit policy**: COLLECT (sum requirements across all matching rules; multiple rules can compound).

## Inputs

| Name | Type | Source |
|---|---|---|
| `riteType` | `enum` | `declareRite.riteType` |
| `estimatedDebtBusd` | `integer` | Σ (enrollCreditor.debts[].principalMicroUsdc + accruedMicroUsdc) ÷ 10^9 (estimated pre-enrollment, refined post-enrollment) |
| `scopeBreadth` | `enum(local, regional, national, multinational, global)` | derived from `declareRite.scope` |
| `riteJurisdictionScope` | `string[]` | `declareRite.scope` parsed |
| `issuerDid` | `string` | `declareRite.issuerDid` |
| `priorRiteSupersession` | `boolean` | true iff this rite supersedes an active rite |

## Outputs

| Name | Type |
|---|---|
| `requiredLv6PlusCount` | `integer` |
| `requiredLv9ChairCount` | `integer` |
| `requiredQuorumPct` | `integer` (0-100) |
| `additionalGates` | `string[]` (e.g. `"transparent-force-rd-disclosure"`, `"land-sovereignty-coordination"`) |

## Rules

| # | riteType | estimatedDebtBusd | scopeBreadth | priorRiteSupersession | → `requiredLv6PlusCount` | `requiredLv9ChairCount` | `requiredQuorumPct` | `additionalGates` |
|---|---|---|---|---|---|---|---|---|
| **B1 (baseline)** | `*` | `*` | `*` | `*` | `3` | `1` | `50` | `[]` |
| **R1** | `shmita_7yr` | `< 1` | `local ∨ regional` | `false` | `0` | `0` | `0` | `[]` (B1 baseline absorbs) |
| **R2** | `yobel_50yr` | `*` | `*` | `*` | `+2` | `0` | `+10` | `["land-sovereignty-coordination"]` (Lev 25 land tenure) |
| **R3** | `tokusei_rei` | `≥ 0.001` | `national ∨ multinational` | `*` | `+1` | `+1` | `+10` | `["jurisdiction-claim-coordination"]` (overlaps sovereign authority) |
| **R4** | `religious_jubilee` | `*` | `*` | `*` | `+0` | `+0` | `+0` | `["partner-religious-corp-notification"]` (cross-denomination coordination) |
| **R5** | `political_amnesty` | `*` | `multinational ∨ global` | `*` | `+3` | `+1` | `+20` | `["transparent-force-rd-disclosure", "council-five-bootstrap-consultation"]` |
| **R6** | `*` | `≥ 1` | `*` | `*` | `+1` | `+0` | `+5` | `[]` |
| **R7** | `*` | `≥ 10` | `*` | `*` | `+2` | `+0` | `+10` | `["public-fund-impact-review"]` |
| **R8** | `*` | `≥ 100` | `*` | `*` | `+3` | `+1` | `+15` | `["mission-charter-review", "public-fund-impact-review"]` |
| **R9** | `*` | `*` | `*` | `true` | `+1` | `+0` | `+5` | `["superseded-rite-archival-check"]` |
| **R10** | `*` | `*` | `global` | `*` | `+2` | `+1` | `+15` | `["global-land-sovereignty-coordination"]` |

## Aggregation example

A `political_amnesty` rite, ~$50 BUSD scope, multinational, supersedes prior:

- B1 baseline: `3 / 1 / 50%`
- R5 political_amnesty + multinational: `+3 / +1 / +20%`
- R6 `≥ 1 BUSD`: `+1 / 0 / +5%`
- R7 `≥ 10 BUSD`: `+2 / 0 / +10%`
- R9 supersession: `+1 / 0 / +5%`

**Total**: `10 Lv6+ × 3 Lv9 / 90% quorum + ["transparent-force-rd-disclosure", "council-five-bootstrap-consultation", "public-fund-impact-review", "superseded-rite-archival-check"]`.

## Implementation note

COLLECT hit policy で実装。aggregation は加算 (numeric) + 集合和 (additionalGates)。Total `requiredQuorumPct` は 100% cap。LangGraph node 実装は `cells/rite_declaration/nodes.py`。
