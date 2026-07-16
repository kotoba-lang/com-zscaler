# SiteSurveyCell — Phase 1

Per [ADR-2605201400](../../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §3 Phase 1.
Murakumo leader: `naphtali`.

## Trigger
`com.etzhayyim.kuniUmi.defineDeploymentSite` MST record (post-gate).

## Inputs (state)
- `siteDid`
- `geo` (GeoJSON polygon)
- `utilityClass` (`electric` / `gas` / `water` / `network` / `power` / `rail` / `airplane` / `port` / `multi`)
- `jurisdictionDid` (LandRegistry / OceanStewardship / RiverStewardship / AtmosphereStewardship / OrbitalSlot)
- `stewardDid` (Lv5+)

## Steps (LangGraph nodes)
1. `allocateScoutFleet` — request N ≥ 2 Giemon scout robots from `open_robo.fleet`
2. `collectSensorBlob` — RGB-D + LIDAR + chem-sensor + ecology baseline → IPFS pin (via `@etzhayyim/sdk`)
3. `jurisdictionEligibility` — DMN gate (see `../../dmn/jurisdiction-eligibility.md`)
4. `witnessAttest` — N robot Ed25519 signatures over the survey-blob CID set
5. `emitSurvey` — write `submitSiteSurvey` to MST

## Outputs
- `submitSiteSurvey` MST record (public)
- `surveyBlobCids[]` IPFS pinned
- Witness signatures attached

## Failure modes
- N < 2 active scout robots → wait + retry; if > 24 h stalled → Council notify
- Ecology baseline detects protected species / cultural heritage → halt + Council Lv6+ vote required to proceed
- Jurisdiction eligibility fails → MST `submitSiteSurvey` written with `accepted=false`, site DID transitions to `rejected`

## See also
- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/kuniUmi/submitSiteSurvey.json`
