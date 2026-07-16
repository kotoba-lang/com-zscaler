# DeploymentPlanningCell — Phase 2

Per [ADR-2605201400](../../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §3 Phase 2.
Murakumo leader: `zebulun` (treasury-adjacent for BoM cost calculations).

## Trigger
`submitSiteSurvey` (accepted=true) MST record.

## Steps
1. `deriveTargetTopology` — translate `utilityClass` + survey blob into target CIM records (open-denki `defineGenerationNode` / open-gas `definePipeSegment` / open-water `defineMain` / open-network `defineLink` / etc.)
2. `bomGeneration` — for each commodity in target topology, invoke UNSPSC LangGraph agent fleet ([ADR-2605171300](../../../../90-docs/adr/2605171300-open-unispsc-generative-agent-fleet.md)) to specify part numbers + quantities + estimated USDC cost
3. `counterpartyClassification` — DMN gate (see `../../dmn/counterparty-classification.md`) — every supplier DID must NOT be Non-Aligned per `ChartersComplianceRegistry` (ADR-2605192230)
4. `proportionalityCheck` — DMN gate (see `../../dmn/proportionality-check.md`) — if scale / impact / reversibility threshold exceeded → flag `requiresCouncilVote=true`
5. `paymentPlan` — schedule USDC disbursements via `Etzhayyim.pay()` → `TitheRouter.route()` 90/10 split (ADR-2605172100 + 2605192130)
6. `fleetAllocation` — request Giemon fleet from `open_robo.fleet` (Otete arms + crawlers + Hitogata humanoids if S3+); estimated robot-hours
7. `proposePlan` — write encrypted `proposeDeploymentPlan` MST record (XChaCha20-Poly1305 envelope for BoM cost confidentiality per ADR-2605181100). If `requiresCouncilVote` → also emit governance proposal.

## Encryption
BoM cost and supplier identities are XChaCha20-Poly1305-enveloped per ADR-2605181100. Per-recipient key wrap: Council Lv6+ DIDs (3+) + plan steward DID + assigned construction-cell leader DID.

## Failure modes
- UNSPSC fleet timeout for any commodity → retry with Murakumo gemma4 fallback (ADR-2605171300 §Local fallback)
- Counterparty Non-Aligned → reject plan; emit `proposeDeploymentPlan` with `accepted=false` and rationale, suggest alternate supplier set
- Proportionality breach + Council vote fails → plan transitions to `rejected`
- Payment plan insufficient liquidity → emit `requiresFunding` event for Public Fund (ADR-2605192145) grant cell

## See also
- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/kuniUmi/proposeDeploymentPlan.json`
- UNSPSC agent fleet (`kotodama.unispsc.dispatch`)
