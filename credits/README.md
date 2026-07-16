# credits — Credit Ledger & Public Fund Routing

**DID**: `did:web:credits.etzhayyim.com`
**Status**: R0 first real slice (2026-07-10); DID-bind identity gate added (2026-07-10, iteration #5) — methods + charter-gate tests only
**See also**: `CLAUDE.md` (full command/policy reference), `MIGRATION-TODO.md`
(substrate-boundary remediation checklist, still pending on the legacy
`actor-manifest.jsonld`)

## Overview

credits is the yoro.etzhayyim.com human-participation credit ledger: Earn
(HC tasks / Murakumo compute) -> Purchase (fixed 30% platform fee) -> Spend
(fixed 10% automatic public-fund allocation), with anti-fraud rate limiting
and an HC reputation gate. Before this slice the actor had only
`CLAUDE.md` + `MIGRATION-TODO.md` + a legacy `actor-manifest.jsonld` (0%
scaffold — no methods, no cells, no tests).

## What this slice adds

| File | Purpose |
|---|---|
| `manifest.edn` | 9 constitutional gates (G1-G9), derived 1:1 from CLAUDE.md's Purchase/Allocation Policy + Anti-Fraud sections and MIGRATION-TODO.md's substrate-boundary list — no invented policy |
| `methods/purchase.cljc` | `preview-purchase` — fixed 30% platform-fee deduction (G1) |
| `methods/spend_allocation.cljc` | `compute-spend-allocation` / `resolve-destination` — fixed 10% public-fund split + the 4-destination enum + default (G2/G3) |
| `methods/anti_fraud.cljc` | `check-spend-allowed` / `check-earn-allowed` — rate limits, high-value-earn reject, HC reputation gate, duplicate-reward reject (G4-G7) |
| `methods/ledger_rails.cljc` | non-fiat native-asset constant + banned-payment-vendor predicate (G8/G9) |
| `methods/identity_gate.cljc` *(iter #5)* | `identity-check` / `require-identity!` / `gated-preview-purchase` / `gated-compute-spend-allocation` — DID-bind gate: requires a shomei-verified DID (Identity Assurance Level >=1) before purchase/spend proceed (G10). Thin adapter over `shomei.methods.aggregate/aggregate` — the IAL ladder is never reimplemented |
| `methods/test_*.cljc` | per-method unit tests |
| `methods/test_charter_gates.cljc` | the umbrella charter-gate suite (also cross-checks `manifest.edn` declares exactly G1-G10) |

All methods are **pure functions**: no I/O, no live ledger/db write, no
payment-gateway call, no fabricated user/financial data. Test run: **50
tests / 112 assertions, green** (was 37/76 before iteration #5's identity
gate). Auto-discovered by `bb test:actors` (ADR-2606131500 discovery
convention) — no `bb.edn` edit was needed or made.

## Constitutional gates (G1-G10)

- **G1** Purchase platform fee is a fixed 30% of `gross_amount`.
- **G2** Spend public-fund allocation is a fixed 10% of every spend.
- **G3** Allocation destination must be one of the 4 declared destinations
  (`public-fund:common` / `-education-family` / `-health-access` /
  `-climate-resilience`); unset preference resolves to `public-fund:common`.
- **G4** Anti-fraud rate limits: spend <=60/hour, earn <=30/hour.
- **G5** A single earn transaction >50 credits is rejected.
- **G6** HC-sourced reward requires `approval_rate >= 50%`.
- **G7** Duplicate reward for the same `task_id`/`session_id` is rejected.
- **G8** The ledger's native asset (`"credit"`) is never a fiat currency.
- **G9** No commercial payment-processor / ads-analytics vendor (Stripe,
  PayPal, GA4, Meta Pixel) is a valid settlement rail.
- **G10** *(iter #5)* A `subject_did` must be shomei-verified at Identity
  Assurance Level >=1 (self-attested) before `PurchaseCredits`/`SpendCredits`
  proceed — the MIGRATION-TODO.md "DID-bind authentication" item, closed via
  a thin adapter over shomei's own `aggregate` function (never reimplemented).

## Left out of scope this slice (deliberately)

This is a **0% -> first-real-slice** increment, not full charter
completion. Explicitly NOT built:

- Any live ledger / `kotoba-datomic` wiring — methods are pure, in-memory only.
- Lexicon schemas (`00-contracts/lexicons/com/etzhayyim/credits/*`) — none exist yet.
- Pregel cells — none created; no `40-engine/.../cells/credits_*` path-reservation yet.
- The GCC Ethereum token layer (wallet / minter / treasury / Chainlink price feed).
- Real USDC / ERC-4337 / `etzhayyim-tithe-router` integration (MIGRATION-TODO.md's
  Stripe/PayPal -> USDC codemod is still pending — untouched this slice).
- **Live DID-bind wiring** — iteration #5 closed the *pure-function* half
  (`methods/identity_gate.cljc` calling shomei's real `aggregate` fn with a
  caller-supplied verified-factor set), but there is still no call to a
  running `shomei_verify_claim`/`shomei_aggregate` cell or a shared
  `kotoba-datomic` read of a subject's actual personhoodCredential — that
  live cross-actor I/O wiring is real R1 work (see `identity_gate.cljc`'s
  docstring TODO), gated by shomei's own G11 (Council-gated proofs raise at R0).
- Replacing/retiring the legacy `actor-manifest.jsonld` (k8s-langserver /
  Cypher-query shape) — left as-is; `manifest.edn` is additive, not a replacement.
- Any credit-scoring / credit-history bureau functionality — out of scope
  for this actor's stated charter entirely (it is a ledger + fee/allocation
  router, not a scoring system) and doubly out of scope for a 30-minute slice.
- No real people's financial data anywhere — all test fixtures are synthetic
  literal numbers (`100`, `10`, `1000`, ...), no fabricated user records.

## Roadmap

| Phase | Scope |
|---|---|
| **R0 first slice** (2026-07-10) | `manifest.edn` gates + pure methods + charter-gate tests |
| **R0 + identity gate** (iter #5, 2026-07-10) | `methods/identity_gate.cljc` (G10) — pure DID-bind gate composed in front of purchase/spend, adapting shomei's real `aggregate` fn |
| **R1** | Wire one method into a real Pregel cell reading/writing `kotoba-datomic`; author the first Lexicon (`creditWallet` or `creditTransaction`); wire `identity_gate.cljc` to a LIVE shomei call/substrate read instead of a caller-supplied factor set |
| **R2** | Begin MIGRATION-TODO.md substrate-boundary codemod (Stripe/PayPal -> USDC) |
| **R3** | Legacy `actor-manifest.jsonld` retirement once R2 codemod lands |

## Related Files

- `/20-actors/credits/CLAUDE.md` — full command/policy reference (Commands, Credit Rates, Data Model, GCC Token)
- `/20-actors/credits/MIGRATION-TODO.md` — substrate-boundary remediation checklist
- `/20-actors/credits/actor-manifest.jsonld` — legacy k8s-langserver manifest (pre-substrate-boundary; untouched this slice)
