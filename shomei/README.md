# shomei (証明) — Believer Identity Binding + Proof-of-Personhood

**DID**: `did:web:etzhayyim.com:actor:shomei`
**Namespace**: `com.etzhayyim.shomei.*`
**ADR**: ADR-2606072100 (R0) · parent ADR-2605260000 · R1 gate ADR-2606072300
**Status**: R0 design scaffold (2026-06-07)

証明 (shomei) is the believer's **passport — DID-centric, not state-centric**. A member self-binds
their existing identities to **one DID** and shomei aggregates the verified bindings into an Identity
Assurance Level + a W3C Verifiable Credential + a proof-of-personhood signal. It is the charter-clean
**inversion of centralized identity verification**: self-sovereign, no admin approval, no central KYC,
no state-database query, PII never plaintext.

## What you can bind

| factor | class | proof | gate |
|---|---|---|---|
| crypto wallet (EVM) | key | EIP-191 / ERC-1271 (`kotoba-auth eth`/`cacao`) | self-sovereign |
| crypto wallet (BTC) | key | BIP-322 legacy signmessage (`kotoba-auth btc`) | self-sovereign |
| SNS account (X / GitHub / Google / Apple) | social | OAuth `sub` / signed gist / DNS-TXT | self-sovereign |
| WebAuthn passkey | device | P-256 assertion (ADR-2605260000) | self-sovereign |
| Base L2 membership | covenant | `Joined` event (ADR-2605172600) | self-sovereign |
| Adherent SBT | covenant | ERC-5192 ownerOf (ADR-2605172700) | self-sovereign |
| AT oath | covenant | Ed25519 record sig | self-sovereign |
| **government ID** (MyNumber / passport / license) | government | local NFC IC + X.509 (ADR-2605260000) | **Council-gated (IAL 4)** |

## Identity Assurance Level

`0 did-only · 1 self-attested · 2 multi-factor (≥2 classes) · 3 covenant-bound · 4 government-verified`.
proof-of-personhood = IAL ≥ 2 with ≥ 2 distinct classes (sybil-**resistance**, not sybil-proof).
Two wallets of the same chain-class do not raise diversity — independence across classes does.

## Lexicons (`00-contracts/lexicons/com/etzhayyim/shomei/`)

- `verificationChallenge` — single-use, subject+factor-bound nonce (anti-replay)
- `identityClaim` — one self-sovereign, subject-signed external-identity binding
- `personhoodCredential` — the aggregate W3C-VC-shaped credential (no PII)
- `bindingRevocation` — owner-signed, append-only retraction (history retained)

## Cells (R0 scaffolds — `.solve()` raises, G11)

`shomei_challenge` · `shomei_verify_claim` · `shomei_aggregate` · `shomei_revoke` ·
`shomei_gov_attest` (hands IAL-4 elevation to the Council-gated gov_auth cells, ADR-2605260000).

## Run

```
./run_tests.sh                    # 75 tests, 9 suites
cd methods && python3 analyze.py  # dry-run → methods/out/identity-report.md + personhood-credentials.json
```

## Boundaries

- **Self-sovereign, never central KYC** (G1) — the subject signs; shomei never approves.
- **Own-identity-only** (G2) — you bind your own IDs; no field asserts a third party's identity.
- **Identity assurance ≠ social credit** (G8) — proves identity strength, never personal worth.
- **No state-database** (G6, §0.4) — government ID is read locally; never queried from the state.
- **PII never plaintext** (G3) — government identifiers are salted-hash + XChaCha20 CID only.

See `CLAUDE.md` for the full 11-gate invariant set and `ADR-2606072100` for the design rationale.
