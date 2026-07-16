# tanemaki 種蒔き

**Public Fund grant-steward (fund-manager inversion).** Fuses disclosed public evidence from
the observatory lineage into a **public due-diligence scorecard** over candidate grantee
organizations — hard charter screens, then a weighted rubric with public weights — and drafts
an **unsent, structurally-advisory** grant proposal. The sower (種蒔き) scatters seed freely
and expects no return: the Public Fund **gives**, it never invests.

- **ADR**: 2606122001 · **Status**: 🟡 R0
- **Schema**: `00-contracts/schemas/fund-stewardship-ontology.kotoba.edn`
- **Lexicons**: `com.etzhayyim.tanemaki.{ddScorecard,grantProposal}`

## What it does

- **Screens** candidate orgs against hard charter eligibility gates (S1..S6 — 非営利整合,
  Rider §2 非抵触, open-by-default, 受領適法性, 私的捕獲なし, 透明性床), each anchored to a
  disclosed charter/Rider citation.
- **Evaluates** eligible orgs on a **public rubric** (C1..C8, weights disclosed, Σ = 1.0) with
  evidence drawn from public sources only: kanjō 勘定 (financials), kabuto 兜 (supply chain),
  tsumugi 紡ぎ (取-concentration), kosatsu 高札 (designations), ooyake 公 (registries),
  shiori 栞 (relief-gap).
- **Drafts** an UNSENT advisory grant proposal (grant / milestone-escrow / in-kind only) and
  **refuses + explains** anything screen-conflicting or under-evidenced.

## The steward boundary — what makes it charter-clean (G1/G2)

tanemaki is a **steward, never a sovereign**. A commercial fund manager decides allocations
and owes its LPs a return; tanemaki evaluates in public and owes the members the truth —
**every grant is decided by 1 SBT = 1 vote** (GrantGovernor, ADR-2605192145) behind a
timelock. The proposal record is structurally advisory (`advisory: true`, `bindsFund: false`,
`decidedBy: "1-sbt-1-vote"`), and the **investment vocabulary does not exist**: equity, debt,
convertible, revenue-share, carry and exit are unrepresentable (the fuchi 扶持 pattern).
These boundaries are enforced in code and proven by tests, not just documented.

All R0 seed orgs are **fictional** (G6) — evaluating a real organization is a G7-gated live
leg fed from primary disclosure only.

## Run

```bash
cd 20-actors/tanemaki
python3 methods/analyze.py && python3 methods/coverage_report.py
python3 methods/propose.py --org org.osslib --amount-usdc 25000 --instrument milestone-escrow
python3 tests/test_analyze.py && python3 tests/test_propose.py && python3 tests/test_coverage.py && python3 tests/test_wasm.py
```

See `CLAUDE.md` for the full gate set and the disclosed rubric.
