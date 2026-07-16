# ReleaseSettlementCell — Phase 2

Per [ADR-2605201800](../../../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md) §Decision.
Murakumo leader: `asher` (blessed / abundance — Gen 49:20, Deut 33:24-25).

## Trigger

Joined `(creditorEnrollment, debtorEnrollment)` pair with `eligible=true` and both anchored.
MST listener subscribes to both `creditorEnrollment` and `debtorEnrollment` collections, emits join event when both sides ready for the same `(riteId, debtId)`.

Input lexicon: `com.etzhayyim.apps.etzhayyim.yobel.recordRelease` (also callable directly by Council Lv6+ for manual override of automatic pairing).

## Steps

1. `loadPair` — decrypt creditor `debtItem` (per-pair wrapped key); load debtor enrollment; assert `riteId` + `debtId` match
2. `taxWarningDmn` — invoke [`dmn/tax-warning-by-jurisdiction.md`](../../dmn/tax-warning-by-jurisdiction.md). Persist `warnings[]` into rite + release records. **Tax warnings do NOT block release** (Charter §1.5: free release; tax consequences are creditor's / debtor's downstream responsibility, served by vendor:lawfirm.etzhayyim.com if needed)
3. `oneWayBoundaryCheck` — **schema-level Charter Rider §2(b) gate**: assert `releasedMicroUsdc ≤ debtItem.principalMicroUsdc + debtItem.accruedMicroUsdc`. Releases can only decrease creditor's stated claim (one-way utility increase). Negative or over-release → reject
4. `tithRouterPathSelection` — DMN: rite-induced release is **tithe-neutral** (no 10% tithe taken from the released amount, since money never changes hands in the canonical `voluntary_bookkeeping` mode). For `base_l2_transfer` mode, route via `TitheRouter.route()` with `releaseDirection=forward`, `tithRate=0` (rite override). Per ADR-2605172100 + ADR-2605192130 standard route is creditor → debtor 100% (no tithe on a forgiveness)
5. `executeRelease` — dispatch by `releaseMethod`:
   - `voluntary_bookkeeping` — no on-chain tx; creditor records forgiveness in own ledger
   - `base_l2_transfer` — invoke `EtzhayyimPaymaster` ERC-4337 user op: creditor USDC vault → debtor USDC vault, signature via creditor ERC725. Returns `baseL2TxHash`
   - `court_order` — emit cross-actor invoke to vendor:lawfirm.etzhayyim.com `runCourtOrderFiling` (read-only output capture; vendor handles secular procedure)
   - `sovereign_decree` — political_amnesty rite only; cross-actor invoke to vendor:lawfirm.etzhayyim.com `recordSovereignDecreeApplication`
   - `ecclesiastical_indulgence` — religious_jubilee rite only; no monetary tx, only doctrinal record
6. `anchorRelease` — write `release` MST record with `releaseMethod`, `baseL2TxHash` (if any), `warnings[]`, `releasedMicroUsdc`, `releasedAt`. Anchor via AnchorBridge
7. `emitAuditEvent` — message `audit_witness` cell (reuben) with release event for continuous append-only log

## Encryption

`baseL2TxHash` + `releasedMicroUsdc` are **public** (on-chain anyway; AT MST mirrors public chain state). `debtId → debtorDid` link encrypted (creditor-debtor relations sensitive per `creditor_enrollment` cell encryption choices).

## Failure modes

- DMN tax warning severity = `high` (release ≥ $1M USDC, R9) → release **continues** but cell emits `requiresLegalReview` cross-actor message to vendor:lawfirm.etzhayyim.com before anchor commit (advisory; not blocking). Operator can configure stricter policy via `releaseSettlementPolicy` MST record
- `releasedMicroUsdc > debt.principalMicroUsdc + debt.accruedMicroUsdc` → reject with `error: "one-way invariant violated — release ≤ enrolled debt amount"`
- Base L2 transfer revert (insufficient creditor vault balance) → release status = `pending_funding`, retry queue. Council Lv6+ can override to `voluntary_bookkeeping` if creditor cannot fund on-chain transfer but commits to bookkeeping forgiveness
- Cross-actor invoke to lawfirm fails / times out → release continues (lawfirm involvement is for downstream tax / court papering, not for blocking the rite act itself)

## Output

Lexicon `com.etzhayyim.apps.etzhayyim.yobel.recordRelease` response:

```json
{
  "ok": true,
  "releaseId": "yobel-2074-tree-of-life-50yr-rel-<tid>",
  "vertexId": "at://did:web:yobel.etzhayyim.com/com.etzhayyim.apps.etzhayyim.yobel.release/<tid>"
}
```

## See also

- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/yobel/recordRelease.json`
- DMN [`dmn/tax-warning-by-jurisdiction.md`](../../dmn/tax-warning-by-jurisdiction.md)
- ADR-2605172100 etzhayyim open telecom fabric (Base L2 settlement substrate)
- ADR-2605192130 TitheRouter (tithe-neutral pass-through for rite releases)
- vendor:lawfirm.etzhayyim.com — court_order / sovereign_decree downstream papering
