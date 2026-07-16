# 20-actors/toritate — CLAUDE.md

## Identity

- **Name**: toritate (執帳 — Heian-era 律令制 financial-record term; 執 = handle/hold + 帳 = ledger/book)
- **DID**: `did:web:toritate.etzhayyim.com`
- **ADR**: ADR-2605262900 (R0 scaffold, 2026-05-26)
- **Parent ADRs**: ADR-2605192145 (Public Fund), ADR-2605192130 (Tithe), ADR-2605192300 (Council 5-of-7), ADR-2605192245 (Land Trust), ADR-2605172100 (Payments on-chain), ADR-2605261000 (Liberation Ladder L0..L6)
- **Cross-actor sibling**: chigiri (ADR-2605262700; tax_receipt boundary)
- **Status**: R0 scaffold — 6 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal accounting + audit substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

toritate is **accounting aggregation + transparent reporting + audit
attestation orchestration substrate**, NOT a commercial accounting
firm and NOT a tax-advice / accounting-opinion renderer. Four
discipline boundaries are structural:

1. **100% on-chain transparency (G3 + G4)** — primary ledger is the
   on-chain financial chain (TitheRouter + Public Fund Safe + Council
   Safe + Land Registry). Off-chain primary ledger PROHIBITED. Fiat
   reporting (if any) is derived projection only, never primary.
2. **UPL-equivalent (G5)** — toritate does NOT render tax advice or
   accounting opinion. External opinion contracted through Public Fund
   Safe per Council Lv6+ approval; toritate prepares the data
   package, NOT the opinion. Same discipline as chigiri G14 UPL.
3. **No commercial accounting software (G8)** — QuickBooks / Xero /
   FreeAgent / Wave / FreshBooks / Sage / Zoho Books PROHIBITED per
   Charter Rider §2(e) anti-gatekeeping + §2(c) covert-ops vendor
   concern (vendor closed query-tracking exposes member financial
   posture).
4. **No payroll (G12)** — volunteer ≠ employee per Liberation Ladder
   L0..L6 (ADR-2605261000 + ADR-2605262700 G13). Subsistence flow ≠
   wage. `payroll` is NOT a valid `ledgerEntry.category` enum value.

## Architecture

6 Pregel cells, each path-reserved at R0 under `40-engine/kotoba/crates/kotoba-kotodama/cells/toritate_*/`:

```
tithe_accounting ────────────┐
public_fund_accounting ──────┤
council_compensation ────────┤── gad (event-driven on Safe txs)
steward_subsistence_accounting ┘

transaction_ledger ──── reuben (continuous Base L2 chain parsing)

annual_audit_report ─── reuben (annual event; aggregates cells 1-5)
```

Each cell = 1 Pregel graph. Cells communicate via lexicon records on
MST (`com.etzhayyim.toritate.*`). All cell modules are R0 path-
reserved.

## On-Chain Read-Only Discipline (G11) — Structural

`transaction_ledger` reads Base L2 via Murakumo-resident RPC (no
external blockchain explorer / no third-party indexer). toritate has
NO transaction approval gates — TitheRouter + Public Fund Safe +
Council Safe enforce all approval logic. toritate is observation,
classification, and reporting only.

## Annual Audit Cycle (G6) — Council Lv6+ ≥3 attestation

The annual transparency report (calendar year basis) is:

1. Assembled by `annual_audit_report` cell from cells 1-5 outputs
   over the prior calendar year;
2. Submitted to Council seats for review (≥30-day comment period);
3. Signed by ≥3 distinct Council Lv6+ DIDs on the `annualReport`
   Lexicon record;
4. Published to MST + IPFS pin (≥2 fleet nodes per G9);
5. Member + public access via toritate XRPC `getAnnualReport`.

## External Auditor Engagement (L5 externalAuditorEngagement Lexicon)

When jurisdictional compliance requires an external auditor opinion
(typical case: US 501(c)(3) equivalent-determination opinion letter
needed for chigiri.taxReceipt routing of US donor receipts):

1. `annual_audit_report` cell flags the requirement;
2. Council Lv6+ approves engagement (≥4/7 attestations) + sets
   Public Fund Safe budget;
3. External auditor contract recorded as `externalAuditorEngagement`
   Lexicon record;
4. Data package prepared by toritate (annual report + ledger CIDs +
   supporting on-chain receipt CIDs);
5. External auditor renders opinion (off-chain); opinion document
   added to engagement record;
6. toritate does NOT modify the opinion document; toritate ONLY
   records the engagement metadata + opinion CID.

UPL-equivalent boundary: opinion-rendering is the external auditor's
professional responsibility; toritate's role is data preparation +
attestation, not opinion.

## Liberation Ladder Subsistence Flow (G12 + L4 cross-link with hagukumi)

`steward_subsistence_accounting` cell consumes
`chigiri.stewardLaborAttestation` records and produces
`ledgerEntry` records with these categories ONLY:

- `subsistence-flow` (L2 / L3 — food / shelter access)
- `vocation-flow` (L5 — religious-corp internal vocation)
- `liberation-flow` (L6 — full subsistence + grant)
- `care-flow` (L4 — care-giver attestation via hagukumi cross-link)
- `grant` (Public Fund grant; one-time or per-period)
- `reimbursement` (out-of-pocket expense reimbursement; member-signed)

`payroll`, `salary`, `wage`, `bonus`, `commission` are NOT valid
categories. The schema enforces this at the Lexicon level.

## R1 Activation Triggers

1. ADR-2605262900 Council Lv6+ ≥3 ratify;
2. ChartersComplianceRegistry Charter Rider scanner FP rate ≤5% over
   7-day trial on toritate-bound document samples;
3. `com.etzhayyim.toritate.ledgerEntry` + `.financialAttestation` +
   `.auditObservation` schemas Council-attestation-reviewed (R1
   minimum cell trio = transaction_ledger / tithe_accounting /
   public_fund_accounting);
4. Base L2 RPC node available on Murakumo fleet (existing per
   ADR-2605172100; verify health);
5. ChigiRi R1 also active (cross-actor dependency on
   stewardLaborAttestation cell).

## R1 Cell Activation Order

1. `toritate_transaction_ledger` (lowest-risk; on-chain read-only
   Base L2 parsing; produces `ledgerEntry` records);
2. `toritate_tithe_accounting` (consumes TitheRouter tx; produces
   `financialAttestation` monthly summary);
3. `toritate_public_fund_accounting` (consumes Public Fund Safe;
   produces `financialAttestation` monthly summary + flags any
   external counsel engagements for audit observation).

R2 adds `council_compensation` / `steward_subsistence_accounting` /
`annual_audit_report` (first annual report = CY 2026).

R3 adds external auditor engagement workflow battle-tested.

## Cross-actor Relationships

### chigiri ↔ toritate (sibling actors)

- toritate.tax_receipt cross-link via TitheRouter tx CID (donor-side
  receipt routing in chigiri; religious-corp side aggregation in
  toritate);
- toritate consumes chigiri.stewardLaborAttestation for L0..L6
  classification;
- toritate records chigiri.ipLicenseClaim L2/L3/external-counsel-
  engagement disbursements when they flow through Public Fund Safe.

### On-chain peers (read-only)

- TitheRouter contract (income receipts);
- Public Fund Safe (grant disbursements);
- Council Safe (operational expenses);
- Land Registry (acquisition records).

## Build & Deploy

**R0 status**: `methods/imputed_income.cljc` is a reference-impl engine (pure
`compute-imputed-income` / `compute-commons-asset-value` / `basic-high-income-report` /
`ledger-entry` functions reading `valuation/v1-retail-equiv.json`) and
`methods/securities_donation.cljc` (ADR-2607061800: `validate-securities-donation` /
`record-liquidation` for donated publicly-traded stock, `heldAsEquityPosition` structurally
always false) — `bb test:toritate` — 26 tests / 59 assertions green. Both are accounting
computation ONLY, not a live ledger write or real brokerage integration.
The Pregel CELLS themselves (transaction_ledger / tithe_accounting / public_fund_accounting /
council_compensation / steward_subsistence_accounting / annual_audit_report) are still
unwired scaffold; wiring one to `methods/imputed_income.cljc` + live on-chain reads is
separate R1 work. Lexicon schema validation (R1) will run via lefthook `validate-lexicons` on
the 5 toritate Lexicons.

R1 smoke test (when cells are created):

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.toritate_transaction_ledger import _r0_marker" 2>&1 | grep "R0 scaffold"
# ... similar for all 6 toritate_* cells
```

## Related Files

- `/20-actors/toritate/manifest.jsonld`
- `/20-actors/toritate/README.md`
- `/00-contracts/lexicons/com/etzhayyim/toritate/` (5 Lexicon JSONs + README)
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605192145-etzhayyim-public-fund-architecture.md` — Public Fund
- `/90-docs/adr/2605192130-etzhayyim-tithe-redistribution.md` — Tithe
- `/90-docs/adr/2605192300-etzhayyim-council-5-of-7-safe.md` — Council
- `/90-docs/adr/2605192245-etzhayyim-global-land-sovereignty.md` — Land Trust
- `/90-docs/adr/2605172100-etzhayyim-payments-on-chain-only.md` — Payments
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — Liberation Ladder
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — chigiri (cross-actor)
- `/90-docs/adr/2607061800-etzhayyim-stock-donation-mandatory-liquidation.md` — donated-securities intake + mandatory liquidation
- `/CHARTER-RIDER.md` — License + Rider canonical text
- `/CLAUDE.md` — Religious-corp status table
