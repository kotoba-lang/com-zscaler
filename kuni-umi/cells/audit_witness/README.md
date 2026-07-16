# AuditWitnessCell — Continuous

Per [ADR-2605201400](../../../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) §9.
Murakumo leader: `levi` (membership + audit domain).

## Role (CRITICAL — constitutional)
Religious-corp の自前 inspection layer. 国家 inspector に依存せず、N ≥ 2 independent Giemon robot 署名で物理世界の活動を attestation する。ADR-2605192315 §3 transparency triple (on-chain log + open-source + 1 SBT = 1 vote) を物理 deployment にも適用。

## Trigger
- continuous sensor stream during Phase 3 (construction) and post-commission
- every super-step boundary triggers a witness check
- on `recordPhysicalAuditEvent` class events: `anomaly` / `intrusion` / `injury` / `compliance-check` / `community-event`

## Steps
1. `pollWitnesses` — fetch latest sensor blob CIDs from ≥ 2 independent robots stationed at the site
2. `verifySignatures` — each robot signs the blob hash with its Ed25519 key (`did:web:etzhayyim.com:kuniumi:robot:<serial>`)
3. `compareBlobs` — robots observe the same physical reality; blob hashes for shared sensor channels MUST match within tolerance (sensor noise floor)
4. `attestRecord` — assemble `witnessAttestations[]` and attach to the current event record
5. `escalateMismatch` — if any pair disagrees beyond tolerance → automatic halt + Council Lv6+ deliberation + `recordPhysicalAuditEvent` class=anomaly subtype=`witness-mismatch`
6. `feedbackPhenotype` — for `injury` / `community-event` classes, emit Phenotype delta per ADR-2605192230 (`injury` ↓ for responsible steward; `community-event` ↑)

## Witness invariant (NON-NEGOTIABLE)
N ≥ 2. Always. Even for solo-survey (S1) phase requires 2 witnesses (use a stationary base-station robot + the active scout, or two scouts in tandem). No site is ever commissioned with N=1 witness coverage.

## Encryption
- `anomaly` / `injury` subtypes — XChaCha20-Poly1305 per ADR-2605181100 (Council Lv6+ + steward + injured-party DID for `injury`)
- `community-event` / `compliance-check` — public (transparency)

## See also
- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/kuniUmi/recordPhysicalAuditEvent.json`
- ADR-2605191657 (Ed25519 DID auth — robot signature scheme)
- ADR-2605192315 (transparent force R&D — drone-detection sensors are reused here for `intrusion` class)
