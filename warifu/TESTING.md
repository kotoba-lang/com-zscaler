# warifu 割符 — Testing

ADR-2605302000. One command runs every layer:

```sh
bash 70-tools/scripts/warifu-test.sh
```

Exits non-zero if any suite fails; prints a per-suite PASS/FAIL table.

## Suites (163 checks across 4 toolchains)

| Suite | Tool | Path | Checks |
|---|---|---|---|
| contracts | `forge test` | `50-infra/warifu-contracts/test/*.t.sol` | 29 |
| cells | `python3` | `20-actors/warifu/cells/test_cells.py` | 26 |
| eavt-schema | `python3` | `20-actors/warifu/cells/test_eavt_schema.py` | 12 |
| guarded-substrate | `python3` | `20-actors/warifu/cells/test_guarded_substrate.py` | 11 |
| lexicons | `python3` | `20-actors/warifu/test_lexicons.py` | 48 |
| gateway | `node` (`npm test`) | `50-infra/warifu-gateway/src/**/*.test.mjs` | 37 |

`forge` 29 = WarifuCard 8 + SettlementRouter 6 + SettlementRouterEdge 4 + Reentrancy 2 + CreditLine 9.
`gateway` 37 = purpose 4 + idempotency/iso8583 8 + stripe-compat 13 + iso8583 e2e 5 + nfc e2e 4 + server 3.

## What the suite guarantees (charter invariants, machine-enforced)

- **手数料0**: `MERCHANT_FEE_BPS==0` (Solidity), `feeUsdc const:0` (lexicons), `warifu/fee_usdc==0`
  (EAVT schema), `fee:'0'` (gateway e2e) — across all surfaces and the on-chain settle.
- **無利息**: `INTEREST_BPS==0` / `LATE_FEE_BPS==0` + draw/repay/over-limit/over-pay edges.
- **Phase 1 閉域 / Phase 2 Lv7+ ゲート**: external `purchase`/`subscription` rejected pre-substrate
  on every surface (HTTP 451 / ISO 8583 DE39 57 / `PurposeGated()` on-chain) until `enablePhase2`.
- **no platform key**: gateway/cells hold no private key; passkey/smart-account only.
- **kotoba EAVT**: every emitted datom conforms to `cells/eavt_schema.py`; `GuardedSubstrate`
  enforces it at the write path before persist.
- **整合**: the purpose allow-list is identical across Solidity / Python / JS / lexicons
  (cross-checked by the gateway purpose test + the lexicon validator).
- **idempotency**: replay never double-charges (Stripe key / ISO 8583 STAN / NFC tap).
- **reentrancy**: a hostile token cannot re-enter `settleDebit`/`settleCredit`.

## Prereqs
`forge` (Foundry), `python3`, `node` (built-ins only — gateway has no npm deps to install).
