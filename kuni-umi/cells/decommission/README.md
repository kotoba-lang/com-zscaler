# DecommissionCell — End-of-life

Per [ADR-2605201400](../../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §3 + Open Question 5.
Murakumo leader: `dan`.

## Trigger
- `lifespanYears` expiry on `proposeDeploymentPlan` (default 30 y), OR
- explicit governance vote (1 SBT = 1 vote 過半数 + Steward Lv5+ sign per ADR §8) to decommission early

## Steps
1. `decommissionPlan` — generate teardown BoM (reverse of construction BoM); identify recyclable / re-usable materials → route to `open-robo` urban-mining cell (`60-apps/etzhayyim-project-open-robo/docs/urban-mining-automation-v1.md`)
2. `disconnectFromUtility` — coordinate with open-ot to retire all `defineLoop` and disable telemetry streams; mark utility lexicon assets `retired`
3. `physicalTeardown` — re-dispatch Giemon fleet for disassembly (reverse construction Pregel super-steps); witness audit applies
4. `landReturn` — restore site to baseline ecology per `submitSiteSurvey` snapshot (re-vegetate, soil restoration, etc.); emit `recordPhysicalAuditEvent` class=`community-event` subtype=`land-return`
5. `releaseStewardship` — update `LandRegistry` (or OceanStewardship / etc.) — site returns to commons OR transfers to new stewardship per governance choice
6. `archiveSite` — finalize site DID state → `decommissioned`; permanent record (all phase records + audit trail) anchored to L2 for multi-generational preservation per ADR-2605192100 §mission.multi_generational_priority

## Constitutional alignment
Decommission is treated as a religious act per ADR-2605192100 §mission.land_as_religious_trust — the land does not belong to the project, the project was stewarded by the land. End-of-life is a ritual return.

## Failure modes
- Recycling infeasible (contaminated materials) → escalate to specialist disposal partner (must NOT be Non-Aligned per ADR-2605192230)
- Ecology restoration insufficient (post-restoration survey fails baseline match) → extend restoration phase; document permanent ecological debt as a Phenotype.effectiveMultiplier deduction for responsible steward chain
- Steward chain broken (all stewards died / step-down) → fallback per ADR-2605192345 §fallback-paths (council-appointed / corpus-direct / community-trust / dissolution-to-corpus)

## See also
- ADR-2605192345 (steward succession — for multi-generational stewardship continuity)
- `60-apps/etzhayyim-project-open-robo/docs/urban-mining-automation-v1.md` (recycling cell)
