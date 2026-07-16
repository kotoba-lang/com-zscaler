# CommissioningCell — Phase 4

Per [ADR-2605201400](../../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §3 Phase 4.
Murakumo leader: `simeon`.

## Trigger
Terminal `recordConstructionProgress` with `phase=handoff-ready`.

## Steps
1. `registerWithUtility` — for each commissioned asset, call the relevant `open-*` utility lexicon:
   - electric → open-denki `defineGenerationNode` / `defineSubstation` / `defineFeeder` / `registerSmartMeter`
   - gas → open-gas `defineRegulator` / `definePipeSegment`
   - water → open-water `defineReservoir` / `defineMain`
   - network → open-network `defineSite` / `defineLink`
   - rail / airplane / ports / power → corresponding lexicons
2. `registerWithOpenOt` — for each control loop, call open-ot `defineDevice` / `defineCell` / `defineLoop` (ADR-2605151200) — open-ot WASM PLC takes over for steady-state operation
3. `enrolStream` — start telemetry stream (smart-meter / pressure-log / quality-sample / utilization-sample / etc.) into the utility lexicon
4. `commissionTest` — run a short acceptance test (e.g., for microgrid: black-start sequence + droop-P-f response — reuses open-ot reference BFB cells `ANTI_ISLANDING_ROCOF`, `DROOP_P_F`, `BLACK_START_SEQ` once available)
5. `commission` — emit `commissionDeployment` MST record; site state → `operational`
6. `transferStewardship` — formally transfer day-to-day operation to the steward DID + open-ot orchestrator; kuni-umi observes via cross-link (siteDid → open-ot loopDid)

## Boundary
After `commissionDeployment`, kuni-umi is observer-only for that site (except `AuditWitnessCell` continues throughout the site's lifespan, and `DecommissionCell` activates at end-of-life). Steady-state operation is open-ot's responsibility.

## Failure modes
- Commission test fails → emit `commissionDeployment` with `accepted=false`; site returns to Phase 3 with a punch list
- Utility lexicon rejects asset registration (e.g., MAOP violation on gas, monotonic constraint on electric meter) → emit anomaly; punch list
- open-ot `defineLoop` rejects topology → loop spec error in BoM; punch list

## See also
- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/kuniUmi/commissionDeployment.json`
- `60-apps/etzhayyim-project-open-ot/PROTOTYPE-MICROGRID.md` (acceptance test reference)
- All `60-apps/etzhayyim-project-open-{denki,gas,water,network,power,rail,airplane,ports}/CLAUDE.md`
