# subaru 昴 — R3 live-legs operator runbook (Council-attestation-gated)

**ADR**: 2606162355 · **Status**: 🔴 R3 — gated, **no live action performed by this document**.

This runbook is the **Council-attestation request** for subaru's two gated legs. Per the
Bootstrap premise (root `CLAUDE.md`: *Council attestation = Pull Request review*), **merging the
PR that carries this runbook = Council attestation of the PROCEDURE below** — it does NOT
authorize operating a live constellation. Constellation operation is its own on-chain-attested,
spectrum-coordinated programme (G4/G6).

No step here is executed by an agent or a server. Every operational leg is a **no-server-key
operator-DID** action (G8); the actor component routes no traffic and operates no spacecraft.

## Leg A — componentize-py WASM build + CID advertisement (operator/deploy step)

Pre-req: PR #1857 (R0–R2) merged. Then, on an operator workstation:

```bash
cd 20-actors/subaru
componentize-py -w subaru-actor componentize actor -o dist/subaru.wasm
ipfs add --cid-version=1 --raw-leaves dist/subaru.wasm > dist/subaru.cid
node ../tsumugi/wasm/loader/verify.mjs dist/subaru.wasm   # headless CID re-verify
```

Advertise the CID in the actor's `did.json` (`:actor/wasm-cid`, ADR-2606013800). The component
is read-only, carries no user traffic, and has no DPI/user-location attribute (G1/G2) — **safe
to do on attestation of this runbook**.

## Leg B — live constellation operation (operator-DID + Council + spectrum, physical)

**This leg is NOT unblocked by merging this PR.** It additionally requires:

1. **G1 connectivity-commons** — no deep-packet inspection, no user-geolocation-as-product, no
   ISR/targeting relay, no military-exclusive C2; the `check_g1`/`check-g1` gate must pass
   (these are unrepresentable — verified in CI).
2. **G2 no person-tracking / reciprocal-symmetric** — subscriber content E2E-encrypted
   (`com.etzhayyim.encrypted`, ADR-2605181100); the constellation routes ciphertext it cannot
   read; no traffic-metadata retention-as-product.
3. **G3 non-profit / cash≡0** — connectivity delivered as §1.16 in-kind (covenantal-universal,
   to the unconnected + disaster zones); no subscription / ads / data-as-payment.
4. **G4 Transparent constellation** — open-source bus + protocol + on-chain ops log + ratified
   1 SBT = 1 vote.
5. **G5 orbital stewardship** — low-deorbit-debt orbit + disposal plan + darksat night-sky
   mitigation; `stewardship` G5 pass; footprint reduces, not adds to, hoshimori's congestion
   integral. Couples torifune G5 (the launch that places the bus) and hoshimori (the observer).
6. **G6 spectrum/coordination honesty** — ITU + national coordination DISCLOSED + respected; no
   harmful interference; no unlicensed/covert band use.
7. **G8 no-server-key** — operations driven by the operator's own ground segment/key, never an
   etzhayyim-operated Worker/pod/CI/bot.

## What this PR attests / does NOT attest

- **Attests** (on merge, = Council PR review): the *procedure* above is charter-conformant and
  Leg A may proceed.
- **Does NOT attest**: operating a live constellation, spectrum filings, or any operator-DID
  action — those are a separate, on-chain-attested programme outside this repo's authorship.
