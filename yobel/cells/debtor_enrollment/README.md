# DebtorEnrollmentCell — Phase 1

Per [ADR-2605201800](../../../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md) §Decision.
Murakumo leader: `issachar` (discernment + scholar tribe — Gen 49:14-15, 1 Chr 12:32 "men who had understanding of the times").

## Trigger

`enrollDebtor` XRPC request, scoped to a `riteId` that is in `status=active`.
Input lexicon: `com.etzhayyim.apps.etzhayyim.yobel.enrollDebtor`.

## Steps

1. `validateInput` — `riteId` exists ∧ `status=active`; `debtorDid` resolves; `eligibilityProof` length within bound
2. `verifyDebtorSbt` — `CouncilSBT.balanceOf(debtorDid) ≥ Lv1` (Charter §1.13 invariant). No SBT → reject (R12 of `eligibility-by-rite-type.md`)
3. `runEligibilityDmn` — invoke [`dmn/eligibility-by-rite-type.md`](../../dmn/eligibility-by-rite-type.md) with `(riteType, debtorSbtLevel, debtorCommunityMember, debtOriginationDate, riteCycleStart, jurisdictionIso3, riteJurisdictionScope, debtInstrument, sovereignDecreeRef)`. FIRST hit policy. R12 (no SBT) + R13 (Charter Rider §2(b)) short-circuit before rite-type rules
4. `crossCheckCreditorEnrollment` — for each `(creditorDid, debtId)` pair in active `creditorEnrollment` MST records, decrypt debtor field (cell holds per-pair key wrap) and assert match. Mismatched debtor → enrollment accepted but `unpaired` flag; `release_settlement` will skip unpaired entries
5. `encryptEligibilityProof` — XChaCha20-Poly1305-envelope `eligibilityProof` per ADR-2605181100 (may contain PII e.g. residence cert, kyu/dan ref). Per-recipient wrap: debtor DID + Council Lv6+ × 3 + audit_witness cell leader DID (reuben)
6. `anchorEnrollment` — write `debtorEnrollment` MST record with encrypted proof + `eligible: boolean` + `reasons[]` (DMN output, public part); anchor via AnchorBridge

## Encryption

`eligibilityProof` is XChaCha20-Poly1305-enveloped (potentially PII per Charter §1.3 + GDPR-style stewardship). `eligible` + DMN `reasons[]` strings are public (transparent eligibility logic per Charter §1.3).

## Failure modes

- No CouncilSBT → reject with `error: "Charter §1.13 SBT identity required — obtain Council membership first"` + link to council enrollment flow
- DMN R13 fires (prohibited instrument in debts) → reject with `error: "debt portfolio contains Charter Rider §2(b) prohibited instrument"`. Note: this happens at debtor side too because we cross-check against creditor enrollments
- DMN rite-type rule fires `eligible=false` → enroll the debtor as `eligible: false`, persist DMN `reasons[]`, **do not** anchor on Base L2 (skip anchor for ineligible enrollments to avoid wasted gas)
- Cross-check finds no matching `creditorEnrollment` for debtor's claimed debt → enrollment accepted, status `unpaired`, debtor can re-submit when creditor enrolls

## Output

Lexicon `com.etzhayyim.apps.etzhayyim.yobel.enrollDebtor` response:

```json
{
  "ok": true,
  "enrollmentId": "yobel-2074-tree-of-life-50yr-debt-<tid>",
  "eligible": true,
  "vertexId": "at://<debtorDid>/com.etzhayyim.apps.etzhayyim.yobel.debtorEnrollment/<tid>"
}
```

`verifyEligibility` query (separate lexicon) wraps the same DMN evaluation without state mutation — useful for pre-flight UI checks.

## See also

- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/yobel/enrollDebtor.json` + `verifyEligibility.json`
- DMN [`dmn/eligibility-by-rite-type.md`](../../dmn/eligibility-by-rite-type.md)
- ADR-2605181100 envelope encryption (eligibilityProof PII protection)
