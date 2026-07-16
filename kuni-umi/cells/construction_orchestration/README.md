# ConstructionOrchestrationCell — Phase 3

Per [ADR-2605201400](../../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §3 Phase 3.
Murakumo leader: `joseph`.

## Trigger
`proposeDeploymentPlan` accepted (either auto for < threshold, or post-Council vote).

## Steps
1. `dispatchFleet` — `open_robo.fleet.dispatch(fleetDid, taskCid)` where `taskCid` is the IPFS-pinned construction plan (per-robot task list, BSP super-step DAG)
2. `superStepLoop` — drive Pregel super-step 1 cell at a time (1 cell = 1 Giemon construction unit = 1 Pregel node per ADR-2605151200 §SPEC §6)
3. `streamProgress` — emit `recordConstructionProgress` at 1–10 Hz checkpointer cadence (per ADR-2605191559) — NOT hard-RT, that stays in firmware
4. `witnessVerify` — at each super-step boundary, collect N ≥ 2 robot signatures from `AuditWitnessCell`; mismatch → halt + Council escalation
5. `handlerAnomaly` — sensor / actuator anomaly → emit `recordPhysicalAuditEvent` with `class=anomaly` (encrypted per ADR-2605181100) → resume or halt per DMN
6. `completeHandoff` — when all cells done, prepare handover blob (control verbs + calibration data + as-built CIM record updates) → emit terminal `recordConstructionProgress` with `phase=handoff-ready`

## Cadence rules (CRITICAL)
- LangGraph orchestrator observes / coordinates at 1–10 Hz (checkpointer cadence)
- Per-cycle critical path (≥ 100 Hz PID math, motion / servo loops) stays in Giemon firmware (open-robo + open-ot field tier, WAMR AOT on Zephyr)
- A LangGraph node MUST NOT execute hard-RT control — this is a constitutional invariant inherited from open-ot ADR-2605151200

## Failure modes
- Robot failure mid-cell → re-allocate from fleet pool; if pool insufficient → halt + open-robo bulk dispatch request
- Witness DID signature mismatch (N robots disagree on sensor blob hash) → automatic halt + Council Lv6+ deliberation
- BoM material delivery delay → halt + recompute schedule; if delay > planned `slackDays` → Council escalation
- Construction injury (`recordPhysicalAuditEvent` class=injury) → immediate halt; safety review; Phenotype.effectiveMultiplier feedback per ADR-2605192230

## See also
- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/kuniUmi/recordConstructionProgress.json`
- Giemon firmware (`60-apps/etzhayyim-project-open-robo/firmware/`)
- open-ot Pregel orchestrator pattern (`60-apps/etzhayyim-project-open-ot/orchestrator/`)
