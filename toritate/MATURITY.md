# toritate 執帳 — Maturity

**Stage: R0** (scaffold) — ADR-2605262900. Accounting aggregation + transparent reporting +
audit-attestation substrate, **NOT a commercial accounting package**. 100% on-chain ledger,
no fiat / no commercial accounting software, no payroll, the 90/10 tithe split. Cross-linked
by 7+ sibling actors (wakai backstop / Public Fund / Tithe / Land Trust).

| Dimension | State |
|---|---|
| Lexicons | ✅ 5 under `com.etzhayyim.toritate.*` (ledgerEntry / financialAttestation / auditObservation / annualReport / externalAuditorEngagement) |
| Cells | 🟡 path-reserved in `40-engine/.../cells/toritate_*` (R0) |
| Manifest | ✅ present |
| Tests | ✅ `methods/test_charter_gates.cljc` + `methods/test_imputed_income.cljc` + `methods/test_securities_donation.cljc` — **26 tests / 59 assertions, green** (`bb test:toritate` / `./run_tests.sh`) — pins on-chain / no-fiat / no-payroll / tithe-split / donor-PII / Council gates, plus both engines' own invariants (incl. drift guards cross-checking hardcoded enums against the Lexicons) |
| Methods | ✅ `methods/imputed_income.cljc` — R0 reference implementation for ADR-2605301020 Basic High Income accounting: `compute-imputed-income` (FLOW) + `compute-commons-asset-value` (STOCK), both reading `valuation/v1-retail-equiv.json` rather than duplicating its figures; `basic-high-income-report` (the ADR-2605301020 §5 Liberation Metric `basicHighIncome` block, `cashStipendUsdMicros` structurally always 0); `ledger-entry` (G3/G4/G8/G12). **+ `methods/securities_donation.cljc`** (ADR-2607061800) — `validate-securities-donation` / `record-liquidation` for donated publicly-traded stock; `heldAsEquityPosition` structurally always false (no speculative holding); liquidation proceeds cross-link into the ordinary USDC/TitheRouter rail, accounted as a new `ledgerEntry` category (`securities-donation-liquidation-proceeds`). `solve()` raises in both — accounting computation only, NOT a live ledger write or real brokerage integration |

## Charter gates pinned by the test

- **G3/G4 100% on-chain** — `ledgerEntry` requires `chain` + `txCid` + `counterpartyDid` +
  `amountUsdMillicents`; `chain` enum is **exactly** {base-l2, geth-private, ipfs-record-only}
  (no off-chain rail representable).
- **G8 no fiat** — `ledgerEntry.nativeAsset` is **exactly** {usdc, eth, n-a}; no fiat token
  (usd/jpy/eur/gbp/cny/fiat) is representable.
- **G8 commercial-software / fiat-leak surfaced** — `auditObservation.observationCategory`
  can flag `commercial-accounting-software-integration-attempt` + `fiat-leak-attempt` +
  `tithe-split-mismatch`.
- **G12 no payroll** — no `salary`/`wage`/`payroll`/`bonus`/`compensation` ledger category;
  the volunteer-economy flows (`subsistence-flow` / `vocation-flow`) exist instead.
- **tithe 90/10** — `tithe-split-90pct-operational` + `tithe-split-10pct-public-fund`
  categories present.
- **donor-PII protection** — `financialAttestation` requires `publishedDonorPii`; enum is
  exactly {none, aggregated-only, opt-in-explicit}.
- **Council attestation** — `annualReport` + `externalAuditorEngagement` require `councilAttestations`.

## R0 → R1 gate

Council Lv6+ ≥3 baseline + the 5 ledger/report/audit cells + the annual audit cycle wired
(MST publish + IPFS pin ≥2 nodes). UPL-equivalent boundary (G5): toritate prepares the
data package; external-auditor opinion stays off-chain.
