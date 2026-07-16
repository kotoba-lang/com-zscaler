# shomei (証明) — believer self-sovereign identity binding + proof-of-personhood

**DID**: `did:web:etzhayyim.com:actor:shomei` · **Tier**: B · **Status**: R0 · **ADR**: 2606072100
**Parent**: ADR-2605260000 (L5 gov auth) · **R1 gate**: ADR-2606072300 (gov_auth R1)

**Read the root `/CLAUDE.md` Charter + substrate rules first.** shomei-specific invariants below
make the Charter concrete for this actor; they OVERRIDE nothing.

## The one-sentence identity

証明 (shomei = proof / attestation) is the believer's **passport — DID-centric, not state-centric**.
A member self-binds their existing identities (crypto wallet · SNS account · WebAuthn passkey ·
existing etzhayyim commitment · opt-in government ID) to **one DID** via cryptographically-verifiable,
**subject-signed** claims, which shomei verifies through the canonical **kotoba-auth** surface and
aggregates into an **Identity Assurance Level + W3C Verifiable Credential + proof-of-personhood**.
Identity **assurance**, never a social-credit score.

## Why it exists (the question it answers)

«Etzhayyim の信者が国家発行パスポート相当の本人証明を行い、既存の政府ID・SNS・ウォレットを DID に
複数紐づけて信頼性を担保する actor は設計されているか?» — Before shomei: **no**. The pieces existed
(did:web, Adherent SBT, the membership ritual, ADR-2605260000's MyNumber/WebAuthn scaffold,
kotoba-auth's eth/btc/cacao verify surface) but **nothing orchestrated multi-ID binding into one
presentable credential**. shomei is that orchestrator.

## The pipeline

```
shomei_challenge ─▶ (member signs proof off the nonce, client-side) ─▶ shomei_verify_claim
  (single-use nonce)                                                     (kotoba-auth verify)
                                                                              │
                          shomei_revoke (append-only) ◀── shomei_aggregate ◀──┘
                                                          (IAL + VC + PoP)
                                                              │
                                                  shomei_gov_attest (IAL-4, Council-gated)
                                                  → gov_auth (ADR-2605260000)
```

`methods/verify.py` is the heart: `PROOF_ROUTING` maps every `proofKind` to the exact kotoba-auth
call, and `verify_claim()` enforces the policy (challenge binding · single-use nonce · freshness ·
gov gate · subject Ed25519 signature). The crypto math itself lives in kotoba-auth (never reimplemented).

## Identity Assurance Level (IAL) ladder — `methods/factors.py`

| IAL | label | rule |
|---|---|---|
| 0 | did-only | no verified factor |
| 1 | self-attested | ≥1 verified factor |
| 2 | multi-factor | ≥2 factors across **≥2 distinct classes** |
| 3 | covenant-bound | IAL2 + a covenant etzhayyim factor (base-membership / adherent-sbt / at-oath) |
| 4 | government-verified | a gov factor paired with ≥1 other class (Council-attested, ADR-2605260000) |

Classes (`device · key · social · government · covenant`) are the sybil-resistance dimension: two
`key` wallets (EVM + BTC) raise the count but **not** class-diversity, so they stay IAL1. `proof-of-
personhood = IAL ≥ 2 AND ≥ 2 classes` — honest **sybil-RESISTANCE, not sybil-proof** (uniqueness is
only approached at IAL 4).

## The 11 gates — do NOT weaken

Each lives in **three places** (factors.py SSoT + lexicon `enum`/`required` + Python `ValueError`).
`methods/test_charter_invariants.py` + `methods/test_lexicons.py` guard the triple.

- **G1 self-sovereign / DID-primary** — the SUBJECT signs every claim; the server never signs/approves.
- **G2 own-identity-only** — you bind only your OWN IDs; there is no field to assert a third party's
  identity (anti-impersonation, the no-doxxing invariant).
- **G3 PII-never-plaintext** — `gov-*` factors carry NO `externalHandle`; only a salted blake2b hash +
  mandatory XChaCha20 `encryptedPayloadCid` (ADR-2605181100). `validate_claim` raises otherwise.
- **G4 cryptographic-proof-mandatory** — `verified=true` needs a verifier result; every proofKind is in
  `PROOF_ROUTING`; a wrong proof for a factor (`wallet-evm` + `bip322`) is unrepresentable.
- **G5 consent-bound + revocable** — opt-in; only the owner revokes (`bindingRevocation`, owner-signed).
- **G6 no-state-database** — `gov-*` is a LOCAL one-way NFC read; never a 住基ネット/マイナンバー query
  (§0.4 non-registration).
- **G7 no-server-key** — claims/revocations/credentials are subject-signed; `serverSig`/`operatorSig`
  fields are structurally rejected (ADR-2605231525).
- **G8 identity-assurance-NOT-social-credit** — the aggregate measures identity PROOF strength, never
  worth/behavior/reputation/rank. No behavioral input; no `score`/`rank` field exists.
- **G9 Murakumo-only** — any narration runs on the Murakumo fleet (ADR-2605215000).
- **G10 non-eschatological as-of + Tier-0 永久記憶** — append-only; a revocation never deletes a claim's
  history (no right to erasure).
- **G11 outward-gated** — live verify / Datom writes / gov L2 elevation are Council + operator +
  member-signature gated; R0 = offline membrane; every cell `.solve()` raises.

## Verify wiring (the kotoba-auth surface — never reimplement)

`PROOF_ROUTING` (methods/verify.py) is the SSoT map. Confirmed kotoba-auth symbols:
`eth::{personal_sign_hash, recover_eth_address, parse_address}` · `btc::verify_message` ·
`cacao::DelegationChain::{verify_signature, verify_signature_eip191_smart}`. shomei injects a
`SignatureVerifier`: `KotobaAuthVerifier` (production, documents the exact call) or `ReferenceVerifier`
(hermetic HMAC test double). **Do not** add a pure-Python secp256k1/Ed25519 implementation — Shannon
violation; delegate to kotoba-auth.

## Build / test

```
./run_tests.sh                       # all auto-discovered cljc test suites (currently 133 tests)
cd methods && python3 analyze.py     # end-to-end dry-run → methods/out/identity-report.md
```

## Honest R0

Design + data-model + offline membrane + dry-run only. The seed is REPRESENTATIVE (no real PII; an
HMAC ReferenceVerifier simulates proof-of-control). Live verification against real kotoba-auth, live
credential issuance, and gov-ID L2 elevation are all gated (G11 + ADR-2605260000 Council activation).
`KotobaAuthVerifier` raises with the canonical call to wire at R1 — it never fakes a success.

## Do not

- Do not let the server sign a claim/credential, or add a `serverSig`/`operatorSig` field — G1/G7.
- Do not store a plaintext government identifier or a `gov-*` `externalHandle` — G3 (PII off-graph, encrypted).
- Do not add a `score`/`rank`/`reputation`/`worth` field to personhoodCredential — G8.
- Do not query a state database for gov-ID — G6 (local NFC read only; §0.4).
- Do not reimplement secp256k1/Ed25519/keccak in Python/JS — delegate to kotoba-auth (Shannon).
- Do not call any cell `.solve()` — R0 scaffolds raise by design (G11).
- Do not add a field to assert a THIRD party's identity — G2 (own-identity-only).
- Do not delete a revoked claim — append-only, 永久記憶 (G10).
- Do not use RisingWave/SQL — kotoba Datom log only (N7).
