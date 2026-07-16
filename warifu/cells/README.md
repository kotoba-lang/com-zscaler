# warifu cells (kotoba-EAVT-native)

Pregel/LangGraph-style cells over the kotoba EAVT ledger (ADR-2605262130). Each cell reads/writes
EAVT facts and emits ERC-4337 UserOperations via `@etzhayyim/sdk`. No platform-held signing key
(ADR-2605231525) — signing is WebAuthn-passkey / smart-account only.

| Cell | Purpose | EAVT entity | Status |
|---|---|---|---|
| `authorize` | debit hold / credit reserve (0%); purpose allow-list gate | `auth_hold` | R0 (logic + shape; substrate edges stubbed) |
| `capture` | full/partial capture of an approved hold | `capture` | R0 (logic + shape; substrate edges stubbed) |
| `settle` | SettlementRouter USDC transfer (T+0, fee 0) | `settlement` | R0 (logic + shape; substrate edges stubbed) |
| `refund` | reverse transfer (purpose `escrow-refund`) | `refund` | R0 (logic + shape; substrate edges stubbed) |
| `dispute` | chargeback record → chigiri procedure | `dispute` | R0 (logic + shape; substrate edges stubbed) |

**Invariants enforced in code**: `fee_usdc = 0` on every fact (決済手数料ゼロ);
`creditInterest = 0` (qard ḥasan); Phase-1 purpose allow-list (external `purchase`/`subscription`
return `GATED` until the Council Lv7+ amendment).

Per-cell I/O contracts live in `lex/` (`warifu.authorize.json`, `warifu.settle.json`).
Wire-level lexicons (`com.etzhayyim.card.*`) live in `10-protocol/warifu/`.

## Substrate seam + tests

- `substrate.py` — `SubstratePort` Protocol (the DI seam to kotoba/`@etzhayyim/sdk`),
  `UnwiredSubstrate` (fail-loud default, no platform key), `InMemorySubstrate` (deterministic
  test fake modelling balances / 0% credit lines / holds / settlements / EAVT facts log).
  R1 injects an `@etzhayyim/sdk`-backed adapter; cells never import a concrete client.
- `test_cells.py` — 26 happy + negative-path checks over `InMemorySubstrate`. Runnable with no
  deps: `python 20-actors/warifu/cells/test_cells.py`. Covers purpose-gate, balance/credit/card
  declines, zero-fee facts, exact money movement, partial/over-capture, partial/over-refund,
  credit-line draw+repay (0%), dispute reason validation + encrypted-CID evidence.
