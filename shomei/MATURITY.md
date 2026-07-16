# 証明 (shomei) — Maturity

**ADR**: 2606072100 · **Status**: 🟡 R0 (design + offline membrane + dry-run) · **Updated**: 2026-06-07

## Stage ladder

| Stage | Scope | Gate | State |
|---|---|---|---|
| **R0** | ADR + 4 lexicons + factor taxonomy/IAL ladder + verify wiring (kotoba-auth routing + policy) + aggregate (IAL + W3C VC + proof-of-personhood) + revoke + offline analyzer + representative seed + 5 cell scaffolds (`.solve()` raise) + tests | ADR-2606072100 (PROPOSED) | ✅ landed |
| R1 | live self-sovereign verification (wallet/SNS/WebAuthn/etz factors) against kotoba-auth; live personhoodCredential issuance; gov L2 elevation via gov_auth R1 | Council Lv6+ ≥3 + ADR-2606072300 | ⏳ |
| R2 | credential presentation/verification UX (browser-local); warifu/musubi IAL-threshold consumers; expiry + re-verification cadence | Council Lv6+ ≥4 | ⏳ |
| R3 | cross-jurisdiction recognition; external-sso-bridge reason code (ADR-2605260000 R3) | Council Lv7+ + operator | ⏳ |

## R0 evidence

- **Tests**: `./run_tests.sh` green — **75 tests** across factors (9) / claims (11) / verify (10) /
  aggregate (10) / revoke (7) / lexicons (6) / charter-invariants (9) / analyze (7) / cells (6).
- **Verify wiring**: `methods/verify.py` `PROOF_ROUTING` maps all 11 proofKinds to the canonical
  kotoba-auth call; `test_verify` asserts the table is total and exercises the full policy (challenge
  binding, single-use nonce, freshness, gov gate, subject signature) with a hermetic `ReferenceVerifier`.
  `KotobaAuthVerifier` documents the exact call per path and **raises rather than fakes** a result.
- **IAL ladder**: `test_factors` + `test_aggregate` pin every level, including the sybil-resistance
  rule (two `key` wallets = one class = not proof-of-personhood) and the IAL-4 Council-issuer switch.
- **Charter invariants**: `test_charter_invariants` enforces all load-bearing gates structurally —
  G3 (gov requires encrypted CID, no plaintext handle), G7 (no server-sig field), G8 (no score field +
  deterministic assurance), G4 (routing total + wrong-proof unrepresentable), G11 (gov gate raises).
- **Lexicon ↔ code SSoT**: `test_lexicons` parses the 4 JSON lexicons and asserts every enum matches
  `factors.py` (factorKind / proofKind / factorClass / revocation reason / assuranceLevel) and that no
  `serverSig`/`score`/`rank` field exists in any lexicon.
- **End-to-end dry-run**: `analyze.py` over the representative seed produces, per member, IAL + W3C VC +
  proof-of-personhood, correctly reports the gov factor as Council-gated (not silently verified), and
  the written credentials contain no external identifiers (handles/addresses).

## Honest R0 gaps

- No live verification against real kotoba-auth (the Rust crate is not called from this pure-Python
  scaffold; `KotobaAuthVerifier` raises with the canonical call to wire at R1).
- gov-ID L2 elevation is doubly gated (shomei G11 + gov_auth Council activation, ADR-2605260000); the
  R1 activation design is ADR-2606072300.
- OAuth/DNS-TXT/signed-gist SNS verification is specified + routed but not yet wired to a live IdP/DNS.
- The seed is `:representative` (no real members, no real PII).
