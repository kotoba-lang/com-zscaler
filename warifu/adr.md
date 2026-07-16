# ADR-2605302000: warifu 割符 — Open Zero-Fee Card (credit + debit), API-compatible

> **Status**: Proposed (R0)
> **Date**: 2026-05-30
> **Layer**: 20-actors (+ 10-protocol, 50-infra, gateway services)
> **Authority**: Council Lv6+ (Tier-B genesis); Phase 2 external acceptance requires Lv7+ unanimity
> **Canonical**: [`90-docs/adr/2605302000-warifu-open-zero-fee-card.md`](../../90-docs/adr/2605302000-warifu-open-zero-fee-card.md)

This file mirrors the canonical religious-corp ADR. See the canonical copy for the full
Context / Decision / Consequences / Alternatives / References. Summary:

`warifu` is an open-source, **zero-merchant-fee** card payment service (credit + debit) that is
**API/wire-compatible** across three surfaces — Stripe-shaped REST, EMV + ISO 8583 terminals, and
mobile NFC (HCE + self TSP) — settling on-chain (USDC on Base L2 + ERC-4337). Zero fee is
structural (no interchange/assessment/markup; gas sponsored by Paymaster/Public Fund; loss
mutualised by `wakai`). Credit is interest-free (qard ḥasan). **Phase 1 is closed-loop** (SBT↔SBT
carve-out, charter-clean); **Phase 2 external open-loop is gated on a Council Lv7+ amendment** of
the payment-purpose invariant (ADR-2605192115) plus a commercial-vendor-arm bridge
(ADR-2605301036).
