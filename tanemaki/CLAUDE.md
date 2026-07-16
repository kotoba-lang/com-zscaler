# tanemaki 種蒔き — Public Fund grant-steward (fund-manager inversion)

**ADR**: 2606122001 · **depends**: 2605192145 (Public Fund architecture / GrantGovernor +
PublicFundGrantCell) · 2605192130 (10% tithe → Public Fund) · 2606052300 (fuchi 扶持 — the
give-only instrument-allowlist pattern) · 2605312345 (Datom = canonical state) · 2605231525
(no-server-key) · 2605215000 (Murakumo-only) · 2606062100 (3-Tier Charter) · 2606032000
(kanjō — the primary-disclosure live-leg pattern).
**Status**: 🟡 R0 — public DD + advisory proposal drafting (synthetic seed).

tanemaki ("種蒔き" = the sower) is the **Public Fund grant-steward**: the org-shaped role a
commercial world calls a *fund manager*, charter-clean-inverted. It fuses DISCLOSED public
evidence from the observatory lineage (**kanjō 勘定** financial disclosure / **kabuto 兜**
supply-chain / **tsumugi 紡ぎ** 取-concentration / **kosatsu 高札** designation landscape /
**ooyake 公** registry / **shiori 栞** relief-gap) into a **public due-diligence scorecard**
over candidate grantee organizations, and drafts an **unsent, structurally-advisory** grant
proposal. A fund manager decides allocations and owes its LPs a return; **tanemaki evaluates
in public and owes the members the truth — the vote decides** (1 SBT = 1 vote, GrantGovernor,
ADR-2605192145). It is the outflow-side sibling of **fuchi 扶持** (sustenance for internal
maintainers) pointed at **external organizations**, and the actor-ization of the
PublicFundGrantCell evaluation lane.

The sower scatters seed freely and expects no return: the Public Fund **gives** (grant /
milestone-escrow / in-kind) — it never invests.

## Hard gates (constitutional — read before any change)

- **G1 — steward not sovereign. THE defining boundary.** tanemaki EVALUATES + DRAFTS; it
  NEVER decides. Every grant is decided by **1 SBT = 1 vote** on the GrantGovernor behind a
  timelock; the scorecard is a 参考意見 displayed in the voting UI. Enforced in code: there is
  **no `:fund` route** in the route enum; `recommend_route()` raises if a screen-conflicting
  org would route to `:propose`; `build_proposal()` refuses ineligible orgs and emits
  `advisory: true` / `bindsFund: false` / `decidedBy: "1-sbt-1-vote"` / `drafted-unsent`.
  Test-covered (`test_g1_no_conflicted_org_is_proposable`, `test_g1_refuses_excluded_org`).
- **G2 — no investment instrument.** The Public Fund GIVES; it never INVESTS. Instruments are
  `{:grant :milestone-escrow :in-kind}` ONLY; `:equity :debt :convertible :revenue-share
  :profit-claim :carry :dividend :exit` are **unrepresentable** (`assert_instrument` raises —
  the fuchi ADR-2606052300 G1 pattern). Investment-return language (出資/持分/配当/ROI/exit…)
  in a justification is rejected (`assert_no_investment_language`).
- **G3 — non-adjudicating DD (N3).** A screen finding / evidence weight is a **DISCLOSED
  public fact with a named source**, never a verdict on an organization's worth. tanemaki
  never certifies an org good or bad and never promises an outcome.
- **G4 — public-by-default.** The criteria, their weights (Σ = 1.0 — a skewed rubric
  **raises**), the screens, every finding, every evidence source, and every scorecard live on
  the append-only kotoba Datom log. Scorecards are content-addressed (CIDv1+SHA-256) so voters
  can verify the bytes. No private ranking, no data room.
- **G5 — evidence honesty.** Coverage below the disclosed floor (`COVERAGE_FLOOR`), or any
  screen `:undetermined`/unevaluated, routes to `:insufficient-evidence` — tanemaki never
  proposes on thin DD. Every `:meets` edge names its public source.
- **G6 — synthetic seed.** R0 evaluates **FICTIONAL orgs only** (a real org in the committed
  seed = reputational adjudication; test-enforced). Evaluating a REAL organization is a
  **G7-gated live leg** fed from primary disclosure only (the kanjō pattern).
- **G7 — outward-gated.** Submitting a proposal on-chain, publishing a real-org scorecard,
  and live observatory ingestion require member/operator + Council steps. R0 = analyze +
  scorecard + **UNSENT** proposal only.
- **G8 — no-server-key** (ADR-2605231525). tanemaki holds no key and no vote weight: it
  cannot move funds, cannot vote, cannot submit. Disbursement = Public Fund 5-of-7 Safe +
  vote + timelock.
- **G9 — conflict-of-interest disclosure (相互監視).** An evaluator's 縁 to a candidate org
  must be declared on the log before their evidence is weighed.
- **G10 — Murakumo-only narration** (ADR-2605215000).

## The DD pipeline (公開プロセス — screens fire BEFORE weighting)

```
intake ────────── 誰でも候補 org を提案できる (ADR-2605192145: proposer は誰でも)
screen ────────── hard 適格性 S1..S6 (charter/Rider anchors disclosed)
                  ── 1 つでも :conflicts → :excluded (構造的に提案不能)
evidence ──────── observatory fuse: kanjō/kabuto/tsumugi/kosatsu/ooyake/shiori (public のみ)
                  ── 薄い (< floor) or :undetermined → :insufficient-evidence
scorecard ─────── weighted rubric C1..C8 (weights 公開, Σ=1.0) → 参考意見 (content-addressed)
proposal ──────── UNSENT advisory grantProposal (instrument ∈ {grant, milestone-escrow, in-kind})
vote ──────────── 1 SBT = 1 vote (GrantGovernor + timelock) — THE decision. tanemaki に票はない
milestone watch ─ attestation-gated tranches (Council Lv6+; under-delivery → 次トランシェ保留)
```

## The disclosed rubric (評価基準 — weights public, Σ = 1.0)

| code | criterion | weight | evidence source |
|---|---|---:|---|
| C1 | mission-axis fit (労働解放/Wellbecoming/多世代/commons) | 0.20 | public repo/declarations |
| C2 | openness (license/data/process) | 0.15 | public repo |
| C3 | financial stewardship (program 比率/準備金/資金源 HHI) | 0.15 | kanjō 勘定 |
| C5 | 取-release (集中を解放方向へ動かすか) | 0.15 | tsumugi 紡ぎ + kabuto 兜 |
| C4 | governance integrity (理事会/member governance/議事録) | 0.10 | ooyake 公 |
| C6 | wellbecoming evidence (as-of 軌跡 + relief-gap 適合) | 0.10 | shiori 栞 |
| C7 | additionality (この交付がなければ起きないか) | 0.10 | public record |
| C8 | capacity (track record/maintainer 健全性) | 0.05 | kabuto 兜 |

Hard screens (適格性, S1..S6 — pass/fail BEFORE weighting): S1 非営利整合 · S2 Rider §2
非抵触 · S3 open-by-default 成果公開 · S4 受領適法性 · S5 私的捕獲なし · S6 透明性床
(資金使途の公開報告合意).

## Layout

```
20-actors/tanemaki/
├── CLAUDE.md · README.md · manifest.jsonld
├── data/seed-stewardship-graph.kotoba.edn   # org↔screen↔criterion↔source graph (ALL FICTIONAL — G6)
├── methods/                                  # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py            # screens → rubric → route (G1: no :fund route, raises on violation)
│   ├── propose.py            # public scorecard (参考意見) + UNSENT advisory proposal (G2-guarded)
│   ├── datom_emit.py         # kotoba Datom-log (EAVT) emitter — canonical state
│   ├── coverage_report.py    # honest coverage + G1/G2/G4/G5/G6 integrity checks
│   └── cid.py                # kotoba IPFS CIDv1 (raw/sha2-256) — ipfs-parity, no daemon
├── tests/                    # 31 tests, pure stdlib (network-free)
│   └── test_analyze.py · test_propose.py · test_coverage.py · test_wasm.py
└── wasm/                     # kotoba pywasm component (componentize-py)
    ├── wit/world.wit · app.py · build.sh   # exports: analyze/datoms/coverage/scorecard/propose
```

## Run

```bash
cd 20-actors/tanemaki
python3 methods/analyze.py           # → out/dd-report.md (route per org)
python3 methods/datom_emit.py        # → out/stewardship-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py   # → out/coverage-report.md (incl. the G1..G6 integrity checks)

# render the PUBLIC scorecard + draft an UNSENT advisory proposal (G1/G2-guarded)
python3 methods/propose.py --org org.osslib --amount-usdc 25000 --instrument milestone-escrow

python3 tests/test_analyze.py && python3 tests/test_propose.py && python3 tests/test_coverage.py && python3 tests/test_wasm.py  # 31 green
```

## Ontology (fund-stewardship-ontology, `00-contracts/schemas/`)

- **nodes** `:fs/kind` ∈ `{:org, :screen, :criterion, :source, :instrument, :milestone}` with
  org (`:org/form :org/synthetic :org/mission-axis`), screen (`:screen/code :screen/basis`),
  criterion (`:criterion/code :criterion/weight :criterion/axis`), source (`:source/actor
  :source/nature`), instrument (`:instrument/kind`) and milestone (`:milestone/evidence`) facets.
- **edges** `:en/kind` ∈ `{:screened (w/ :en/finding), :meets (w/ :en/weight + :en/evidence),
  :sourced-from, :disburses-via, :watched-by}`.
- **derived** `:bond/screen-clear` · `:bond/dd-fit` · `:bond/evidence-coverage` · `:bond/route`
  — transient, computed on read (N1/G4); there is no stored org score.
- **instrument/allowlist** the load-bearing table: only `:grant :milestone-escrow :in-kind` are
  true; the investment vocabulary is false (G2 / fuchi G1).
- **decision/authority** `:tanemaki false` — the steward is not in the decision set (G1).

## Cross-links

`:fs/links` + `:source/actor` bridge to the observatory lineage — **kanjō 勘定** (disclosed
financials), **kabuto 兜** (supply-chain concentration), **tsumugi 紡ぎ** (取-release),
**kosatsu 高札** (designation landscape, disclosed not adjudicated), **ooyake 公** (registry/
governance records), **shiori 栞** (relief-gap fit) — plus **fuchi 扶持** (the give-only
instrument pattern), **toritate 執帳** (every disbursement booked), **kanae 鼎** (fund flows
rendered). tanemaki evaluates in public and drafts; it does not decide, invest, or disburse.
