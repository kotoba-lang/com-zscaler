# credits — Maturity

**Stage: R0 first real slice** (2026-07-10). Credit ledger + public-fund routing
substrate for yoro.etzhayyim.com: Earn (HC tasks / Murakumo compute) -> Purchase
(fixed 30% platform fee) -> Spend (fixed 10% public-fund allocation), with
anti-fraud rate limiting + HC reputation gate. Prior to this slice: CLAUDE.md +
MIGRATION-TODO.md only, zero methods/cells/tests (0% scaffold).

| Dimension | State |
|---|---|
| Lexicons | ⬜ none yet (out of scope this slice — see README "Left out of scope") |
| Cells | ⬜ none yet (out of scope this slice) |
| Manifest | ✅ `manifest.edn` — 9 constitutional gates (G1-G9), derived 1:1 from CLAUDE.md + MIGRATION-TODO.md, no invented policy |
| Tests | ✅ `methods/test_charter_gates.cljc` + per-method tests — **37 tests / 76 assertions, green** (`bb -e '...'`, auto-discovered by `bb test:actors`) |
| Methods | ✅ `methods/purchase.cljc` (30% fee), `methods/spend_allocation.cljc` (10% split + destination enum), `methods/anti_fraud.cljc` (rate limits / high-value reject / reputation gate / duplicate reward), `methods/ledger_rails.cljc` (non-fiat asset + banned-vendor rail predicate) — all pure functions, no I/O, no live ledger/db/payment-gateway wiring |

## Charter gates pinned by the test

- **G1** purchase platform fee is a fixed 30% (`purchase/platform-fee-bps` = 3000) — not user/admin-adjustable per-transaction.
- **G2** spend public-fund allocation is a fixed 10% (`spend/public-fund-bps` = 1000) of every spend.
- **G3** allocation destination enum is **exactly** {public-fund:common, public-fund:education-family, public-fund:health-access, public-fund:climate-resilience}; unset preference resolves to `public-fund:common`; unknown destination_id raises.
- **G4** rate limits: spend <=60/hour, earn <=30/hour.
- **G5** a single earn transaction >50 credits is rejected.
- **G6** HC-sourced reward requires `approval_rate >= 0.5`.
- **G7** duplicate reward for the same `task_id` or `session_id` is rejected.
- **G8** native asset (`"credit"`) is never one of the fiat currency codes {usd, jpy, eur, gbp, cny, fiat}.
- **G9** no banned commercial payment-processor / ads-analytics vendor (stripe, paypal, google-analytics, ga4, meta-pixel) is a valid settlement rail.

## Left out of scope this slice (see README.md for the full list)

Live ledger/db wiring, Lexicon schemas, Pregel cells, GCC Ethereum token layer,
real USDC/ERC-4337/TitheRouter integration, DID-bound auth wiring, and any
credit-scoring/history functionality are all explicitly NOT built this slice —
this is a 0% -> first-real-slice increment (methods + charter-gate tests only),
matching the shomei/kanjo/toritate/hikari/mizuho pattern at their own R0 stage.

## R0 -> R1 gate

Wire one method (`purchase` or `spend_allocation`) into a real Pregel cell
reading/writing `kotoba-datomic` state; add the first Lexicon
(`com.etzhayyim.credits.creditTransaction` or `.creditWallet`); replace the
legacy `actor-manifest.jsonld` (k8s-langserver/Cypher shape) per
MIGRATION-TODO.md's substrate-boundary codemod.
