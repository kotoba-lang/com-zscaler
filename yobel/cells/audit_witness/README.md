# AuditWitnessCell — Continuous

Per [ADR-2605201800](../../../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md) §Decision + [ADR-2605192415](../../../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md) §B continuous-witness pattern.
Murakumo leader: `reuben` (firstborn / witness — Gen 49:3, "my might, the firstfruits of my strength"; אֵל רָאָה בְּעָנְיִי / "God has seen my affliction" — Gen 29:32).

## Trigger

- LangGraph super-step boundary (every node transition in `rite_declaration` / `creditor_enrollment` / `debtor_enrollment` / `release_settlement`)
- `release` MST record finalization (from `release_settlement` cell `emitAuditEvent`)
- Sensor-style polling: every 60s, scan recent MST records for tampering signals

## Steps

1. `collectStateDiff` — read LangGraph checkpoint deltas via `kotodama.checkpointer.MstCheckpointSaver` (ADR-2605191559); compute `(stateRootBefore, stateRootAfter, txDigest)` triple
2. `signAndAppend` — sign triple with cell's rotating witness key; append to `auditLog` MST collection (`com.etzhayyim.apps.etzhayyim.yobel.auditEvent`)
3. `tamperDetect` — verify previous super-step's signed triple is in the chain. Missing prior signature or hash chain break → tampering signal
4. `anchorAuditBatch` — batched anchor via `AnchorBridge` (ADR-2605171800) every 100 audit events or 10 minutes (whichever first). MST → IPFS → Base L2 anchor tx in `AuditAnchorRegistry` contract
5. `onTamperingDetected` (handler):
   - Mark rite `status = superseded_for_audit`
   - Emit `tamperingIncident` MST record (public per Charter §1.3 transparency)
   - Auto-emit Public Fund grant request (ADR-2605192145) for independent audit cell instance
   - Cross-actor notify Council Lv9 chair + Five-Bootstrap-Council (ADR-2605192300)

## LLM use

This is the only yobel cell that uses an LLM. Used **read-only** for:
- Anomaly narration: when DMN warns `severity=high` on a release, the LLM composes a 1-paragraph human-readable rationale appended to the audit event (NOT used for decision-making — decisions are DMN/contract-driven)
- Quarterly summary: rolls up all rites + releases in the quarter into a public-facing Apache 2.0 + CR v2 compliant report under `90-docs/quarterly-reports/yobel-<YYYY>-Q<N>.md`

Prompts: [`prompts/anomaly-narration.txt`](prompts/anomaly-narration.txt), [`prompts/quarterly-summary.txt`](prompts/quarterly-summary.txt) — both NOT YET COMMITTED (S0 stage; deferred to S1).

## Encryption

Audit log entries are **public per Charter §1.3** (transparent religious-corp acts). However the *content* of upstream events being witnessed may be encrypted (e.g. `creditor_enrollment` payloads). The audit log stores **only** the encrypted ciphertext's hash + signed witness triple, never the plaintext — so audit transparency does not break upstream confidentiality.

## Failure modes

- Witness key rotation in progress + super-step occurs → use overlap window key (cell maintains current + next key for 60s overlap)
- Anchor batch fails (Base L2 outage) → queue locally up to 24h with disk persistence; emit `anchorBacklog` event if queue ≥ 1000
- Tampering false positive (e.g. clock skew on Murakumo replica) → require 2-node consensus before raising `tamperingIncident`. Single-node detection = `tamperingSuspicion` (lesser severity, manual review)

## Output

No XRPC method (witness is internal). Side effects:
- Continuous writes to `com.etzhayyim.apps.etzhayyim.yobel.auditEvent` MST collection (signed)
- Batched anchors to `AuditAnchorRegistry` Solidity contract on Base L2
- On tampering: rite status mutation + Public Fund grant request + Council notification

## See also

- [`prompts/`](prompts/) — anomaly narration + quarterly summary LLM prompts (deferred to S1)
- ADR-2605191559 MST Checkpointer Stage-2 Activation
- ADR-2605171800 AnchorBridge (MST → IPFS → Base L2 pipeline)
- ADR-2605192145 Public Fund grant request semantics
- ADR-2605192300 Council Five-Bootstrap consultation path
