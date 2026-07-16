# DMN: jurisdiction-eligibility

Synchronous gate at `defineDeploymentSite` and re-checked at `submitSiteSurvey`.
Per [ADR-2605201400](../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §5.

## Inputs
- `geo` — GeoJSON polygon
- `utilityClass` — `electric` / `gas` / `water` / `network` / `power` / `rail` / `airplane` / `port` / `multi`
- `domain` — derived: `terrestrial` / `ocean` / `river` / `atmosphere` / `orbit`
- `jurisdictionDid` — DID of the relevant stewardship registry entry
- `stewardDid` — DID of the steward (Lv5+ requirement)
- `intendedBeneficiaryDids[]` — community DIDs
- `intendedUse` — free-form purpose statement

## Decision table

| # | Condition | Action |
|---|---|---|
| 1 | `jurisdictionDid` NOT registered in any of `LandRegistry` / `OceanStewardship` / `RiverStewardship` / `AtmosphereStewardship` / `OrbitalSlot` | **REJECT** — no land claim |
| 2 | `domain=terrestrial` and `LandRegistry.geometryHash(jurisdictionDid)` does not encompass `geo` | **REJECT** — out of bounds |
| 3 | Steward level < 5 | **REJECT** — insufficient stewardship |
| 4 | `ChartersComplianceRegistry.isNonAligned(stewardDid)` = true | **REJECT** — Non-Aligned steward |
| 5 | any `intendedBeneficiaryDids[i]` `isNonAligned` = true | **REJECT** — Non-Aligned beneficiary |
| 6 | `intendedUse` matches Charter Rider §2(a–h) prohibited categories (weapons / speculative finance / surveillance capitalism / fossil fuel extraction / specialist gatekeeping / multi-generational harm / strict individualist ontology / wellbecoming subordination) | **REJECT** — Charter Rider §2 violation |
| 7 | `utilityClass ∈ {electric, gas, airplane, network-spectrum, orbit}` and `localLawAttestationCid` missing | **ESCALATE** to Council Lv6+ — restricted utility requires local law attestation |
| 8 | `domain ∈ {ocean, atmosphere, orbit}` | **ESCALATE** to Extended Sovereignty vote (50% quorum + 2/3 supermajority per ADR-2605192330) |
| 9 | terrestrial site area > 0.1 km² OR multi-utility | **ESCALATE** to regular governance (1 SBT = 1 vote 過半数) |
| 10 | otherwise | **ACCEPT** — local Steward Lv5+ may approve |

## Rationale
- Rule 1 — no `intendedUse=undefined-land`; religious-corp does NOT claim operational sovereignty over land that is neither stewardship-registered nor recognized by state cadastre. Stewardship-only is sufficient per ADR-2605192245 §6 dual-recognition.
- Rule 4–5 — implements ADR-2605192230 three-tier enforcement L1 (license) gate at site definition.
- Rule 6 — Charter Rider §2(a-h) per ADR-2605192200.
- Rule 7 — restricted utilities have national-level regulation (Japan: 電気事業法 / ガス事業法 / 航空法 / 電波法). Local Steward cannot unilaterally clear.
- Rule 8 — ocean / atmosphere / orbital sovereignty requires international-law-aware vote per ADR-2605192330.
- Rule 9 — large or multi-utility scope is too consequential for solo Steward approval.

## Implementation
Encoded as a Rego policy under `00-contracts/rego/kuni-umi/jurisdiction-eligibility.rego` (to be authored in S1).
