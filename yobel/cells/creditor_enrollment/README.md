# CreditorEnrollmentCell — Phase 1

Per [ADR-2605201800](../../../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md) §Decision.
Murakumo leader: `gad` (good fortune / treasury — Gen 49:19).

## Trigger

`enrollCreditor` XRPC request, scoped to a `riteId` that is in `status=active` (output of `rite_declaration` cell).
Input lexicon: `com.etzhayyim.apps.etzhayyim.yobel.enrollCreditor`.

Also: MST listener on `com.etzhayyim.apps.etzhayyim.yobel.rite` (`status: declared → active`) — when a rite activates, this cell begins accepting enrollments for that `riteId`.

## Steps

1. `validateInput` — assert `riteId` exists ∧ `status=active`; `debts[].length ≥ 1` ∧ `≤ 1000`; all `principalMicroUsdc` ≥ 0; all `originationDate` parseable
2. `verifyCreditorStanding` — `CouncilSBT.balanceOf(creditorDid) ≥ Lv1` ∨ partner religious-corp membership (Charter §1.13 SBT identity invariant). Non-aligned secular creditor: still allowed to opt-in (voluntary act), but receives an `info` warning that fallback to native `saisei` (自己破産・個人再生の自己申立て支援, ADR-2607061800) is the canonical mandatory-binding path for the debtor
3. `verifySignedConsent` — `kotodama.identity.erc725.verify_eip712_signed_consent`:
   - Recover signer address from `signedConsent` (EIP-712 typed data over canonical hash of `(riteId | creditorDid | debts[])`)
   - Assert signer address matches `creditorDid` ERC725 keystore (ADR-0074)
   - Fallback: DPoP JWT verification path (`kotodama.identity.dpop.verify`)
4. `historicalRecordGate` — **schema-level invariant (Charter Rider §2(b))**: all `debts[]` entries must have `originationDate < rite.effectiveDate`. New-loan origination via this cell is **prohibited** — assert this in `nodes.py` even though lexicon schema already enforces (defense in depth)
5. `instrumentSafety` — reject any `debts[].instrument` in {`liquidation`, `margin_call`, `seizure`} (Charter Rider §2(b) gate). Schema enum 既に exclude しているが二重 gate
6. `encryptSensitive` — XChaCha20-Poly1305-envelope `debts[].principalMicroUsdc` + `debts[].debtorDid` per ADR-2605181100. Per-recipient wrap: creditor DID + Council Lv6+ (×3) + assigned `release_settlement` cell leader DID (asher)
7. `anchorEnrollment` — write `creditorEnrollment` MST record with encrypted payload, anchor via AnchorBridge

## Encryption

XChaCha20-Poly1305-enveloped per ADR-2605181100 for `debts[].principalMicroUsdc` + `debts[].debtorDid` (creditor-debtor relations sensitive; aggregate metrics decryptable only by Council Lv6+ for audit / by debtor for self-lookup).

## Failure modes

- `riteId` not active → reject with `error: "rite not active or does not exist"`
- ERC725 signature recover mismatch → reject with `error: "signedConsent signature does not match creditorDid"`
- `originationDate ≥ rite.effectiveDate` for any debt → reject with `error: "historical record only — new debt origination not allowed"`
- Prohibited `instrument` value → reject with `error: "Charter Rider §2(b) violation — coercive instrument not enrollable"`
- Per-recipient key wrap failure (e.g. Council Lv6+ DID rotation in progress) → defer + retry with current Council snapshot

## Output

Lexicon `com.etzhayyim.apps.etzhayyim.yobel.enrollCreditor` response:

```json
{
  "ok": true,
  "enrollmentId": "yobel-2074-tree-of-life-50yr-cred-<tid>",
  "debtCount": 47,
  "vertexId": "at://<creditorDid>/com.etzhayyim.apps.etzhayyim.yobel.creditorEnrollment/<tid>"
}
```

## See also

- Lexicon `00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/yobel/enrollCreditor.json`
- ADR-2605181100 XChaCha20-Poly1305 envelope encryption
- ADR-0074 ERC725 + WebAuthn identity bridge (signedConsent verify path)
