# Migration TODO

**Status**: 🔄 TRANSFORM — seed copied 2026-05-21, codemod pending.

**Codemod required**: commerce — Stripe→USDC+TitheRouter, SBT carve-out

## Substrate-boundary checks

This actor SDK was copied verbatim from `etzhayyim-root/20-actors/credits`.
Following must be remediated:

- [ ] Replace direct `@atproto/api` / `viem` / IPFS / Signal client imports with `@etzhayyim/sdk`.
- [ ] Strip RisingWave / Postgres / Kysely → AT MST + IPFS + Base L2 anchor.
- [ ] Strip Stripe / PayPal / fiat → USDC + ERC-4337 + `etzhayyim-tithe-router`.
- [ ] Remove 3rd-party ad / GA4 / Meta Pixel.
- [~] DID-bind authentication (did:web:etzhayyim.com + did:plc + WebAuthn + Adherent SBT).
      Pure-function gate landed 2026-07-10 (iter #5):
      `methods/identity_gate.cljc` requires a shomei-verified DID (Identity
      Assurance Level >=1) before purchase/spend proceed, adapting shomei's
      real `shomei.methods.aggregate/aggregate` fn (G10, never reimplemented).
      STILL OPEN: live wiring to an actual shomei cell/substrate read (today
      the verified-factor set is caller-supplied) — that is R1 work.
- [ ] Verify against Charter Rider v2.0 §2(a)-(h).

## Reference

- ADR-2605192100 / 2605192115 / 2605192200
- `/CLAUDE.md` § Substrate boundary
