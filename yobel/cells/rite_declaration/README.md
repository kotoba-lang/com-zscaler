# RiteDeclarationCell — Phase 0 (gate)

Per [ADR-2605201800](../../../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md) §Decision.
Murakumo leader: `judah` (declaration / proclamation tribe — Gen 49:8-10, kingly proclamation).

## Trigger

`declareRite` XRPC request from an etzhayyim-aligned religious-corp DID (or partner religious-corp DID).
Input lexicon: `com.etzhayyim.apps.etzhayyim.yobel.declareRite`.

## Steps

1. `validateInput` — assert `riteType` ∈ enum, `doctrinalBasis` non-empty, `effectiveDate` ≥ now, `scope` parseable
2. `verifyIssuerStanding` — `CouncilSBT.balanceOf(issuerDid) ≥ Lv1` ∨ partner-religious-corp registry membership (ADR-2605192230 ChartersComplianceRegistry)
3. `charterRiderGate` — verify the proposed rite does not violate Charter Rider §2(a-h):
   - §2(b) speculative finance — already enforced at lexicon schema level (no loan / interest / margin methods)
   - §2(a) military — `scope` text scan + DMN; rite scoped to military debt forgiveness needs additional disclosure under transparent-force-rd (ADR-2605192315)
4. `councilRatificationGate` — DMN [`council-ratification-threshold.md`](../../dmn/council-ratification-threshold.md) → derive `requiredLv6PlusCount` / `requiredLv9ChairCount` / `requiredQuorumPct` / `additionalGates`. Emit governance proposal MST record. Wait for Council deliberation (Tier C escalation per [ADR-2605192415](../../../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md))
5. `landSovereigntyCoordination` (only if `riteType = yobel_50yr`) — coordinate with [`LandRegistry`](../../../../50-infra/) per ADR-2605192245 to identify which land tenure records would auto-revert under Lev 25:23 ("the land shall not be sold in perpetuity")
6. `anchorRite` — on Council ratification success, write `rite` MST record with `status=active`. Anchor via `kotodama.anchor.AnchorBridge` (MST → IPFS → Base L2 batched anchor, ADR-2605171800)

## Encryption

`declareRite` MST record is **public by design** (Charter §1.3 transparent religious-corp acts). Sensitive metadata (e.g. council member names) is XChaCha20-Poly1305-enveloped per ADR-2605181100 with per-recipient wrap to Council Lv6+ DIDs + plan steward DID.

## Failure modes

- Issuer not religious-corp aligned → reject with `error: "issuer not in ChartersComplianceRegistry"`, no MST write
- Charter Rider gate fail (e.g. scope claims military debt forgiveness without transparent-force-rd disclosure) → reject + emit ADR-amendment-required event
- Council ratification fails (insufficient Lv6+ count or quorum) → rite status = `cancelled`, archive MST record with rejection rationale
- Council deliberation timeout (default 30 days per Three-Tier Enforcement) → status = `cancelled`
- Land sovereignty coordination conflict (yobel_50yr scope overlaps active LandRegistry stewardship not aligned with rite) → defer to Lv9 chair tiebreak

## Output

Lexicon `com.etzhayyim.apps.etzhayyim.yobel.declareRite` response:

```json
{
  "ok": true,
  "riteId": "yobel-2074-tree-of-life-50yr",
  "vertexId": "at://did:web:yobel.etzhayyim.com/com.etzhayyim.apps.etzhayyim.yobel.rite/<tid>"
}
```

On success, downstream cells (`creditor_enrollment`, `debtor_enrollment`) start listening for enrollments scoped to this `riteId`.

## See also

- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/yobel/declareRite.json`
- DMN [`dmn/council-ratification-threshold.md`](../../dmn/council-ratification-threshold.md)
- ADR-2605192230 Three-Tier Enforcement (Council Lv6+ ratification semantics)
- ADR-2605192100 §1.5 free release + §1.13 SBT identity (issuer standing basis)
