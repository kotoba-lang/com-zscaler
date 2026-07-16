# DMN: proportionality-check

Phase 2 gate at `proposeDeploymentPlan`.
Per [ADR-2605201400](../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §5.3.

## Inputs
- `siteAreaKm2` — terrestrial / aquatic footprint
- `estimatedCostUsdc` — total BoM + labor + land-use + Tithe
- `lifespanYears` — planned operational lifespan
- `ecologyImpactScore` — derived from `submitSiteSurvey` baseline delta estimate (0–100)
- `populationImpacted` — community DID count + estimated non-DID surrounding population
- `reversibilityScore` — 0 (irreversible) … 100 (fully restorable per `DecommissionCell` plan)
- `domain` — terrestrial / ocean / river / atmosphere / orbit

## Decision table

| # | Condition | Action |
|---|---|---|
| 1 | `domain ∈ {ocean, atmosphere, orbit}` | **REQUIRES Extended Sovereignty vote** (50% quorum + 2/3 supermajority) |
| 2 | `siteAreaKm2 > 0.1` OR `populationImpacted > 100` | **REQUIRES governance vote** (1 SBT = 1 vote 過半数) |
| 3 | `ecologyImpactScore > 30` | **REQUIRES governance vote** + ecologist Council Lv6+ third-party review CID |
| 4 | `reversibilityScore < 50` | **REQUIRES Council Lv6+ supermajority** (3-of-N council multisig) — irreversible commitments need higher hurdle |
| 5 | `estimatedCostUsdc > 1_000_000` | **REQUIRES Public Fund grant evaluation** (ADR-2605192145) if drawing on treasury |
| 6 | site involves `intendedUse` matching `transparent-force-rd` (detection / defensive R&D installation) | **REQUIRES Force Authorization vote** (ADR-2605192315) — 50% quorum + 67% supermajority |
| 7 | none of the above | **ACCEPT** with local Steward Lv5+ sign-off |

## Rationale
ADR-2605192100 §mission.anti_individualism + multi_generational_priority + wellbecoming — large or irreversible impact decisions cannot be made by a single Steward. The thresholds escalate with risk magnitude and time-horizon. Domain-specific (ocean / atmosphere / orbit) thresholds align with the Extended Sovereignty governance pattern.

## Implementation
Same as siblings — Rego policy + LangGraph node.
