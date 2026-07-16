# 扶持 (fuchi) — maintainer sustenance allocator

The **charter-clean inverse of a business investment fund**. Where a VC fund invests capital in
founders expecting equity + ROI + an exit, **扶持** (the feudal **扶持米** — the in-kind rice
stipend that sustained a retainer so they could serve) allocates **in-kind sustenance +
commons-asset access + tooling/compute** to the real-world **maintainers (信者)** who keep
etzhayyim's actors alive (business / robotics / remote-control).

It is a **redistribution / sustenance allocator, never an investor**: no equity, no ROI, no debt,
no profit claim, no exit. **cash≡0.** The whole fund vocabulary (NAV / carry / IRR / cap-table /
exit / dividend) is *unrepresentable* — `:alloc/instrument` is `:db/allowed` only the sustenance
set, exactly as nusa's `:psychoactive`, tazuna's `:weaponizable`, and kamado's
`:fossil-virgin-crude` are unrepresentable.

Per **ADR-2606052300**.

## Why it exists

Real-world maintainers must be able to live; robotics/remote-control work cannot maintain itself.
The charter forbids investing-in-members (non-profit / donation-only / cash≡0 / payoff帰属=etzhayyim),
so 扶持 meets the real need the charter-clean way: it **maximizes in-kind substitution** and
**routes the irreducible external fiat residual to member-principal 0% liquidity** — 扶持 never
holds, lends, or pays cash.

## System-of-systems

扶持 is a horizontal control-plane standing ON TOP of existing systems:

```
Public Fund + TitheRouter + Mission-funding revenue arm   (value source)
   └ Displacement-Dividend tenure curve                    (allocation math, reused)
       └ Basic-High-Income in-kind                          (cash≡0 delivery semantics)
           └ in-kind rails:
               housing → commons-land (LANDS.md)
               food    → mitsuho 瑞穂
               energy  → hikari 光
               compute → Murakumo mesh
               tooling → okaimono 御買物
               care    → iyashi / hagukumi / kokoro
               liquidity → warifu 0% qard-ḥasan (MEMBER-PRINCIPAL only)
           └ toritate (books) + kanae (viz)
           └ 1 SBT = 1 vote / Council Lv7 (governance)
           └ kotoba Datom `as-of` (Wellbecoming trajectory, 非終末論)
```

## Lifecycle

`covenant → need assessment → allocation compute → routing dispatch → governance gate → in-kind
provisioning → Wellbecoming append`

The governance gate is a **pure function** (G7): below the ceiling and in-kind → `auto`; above →
`sbt-vote`; invariant-adjacent (e.g. a new commons-land grant) → `council-lv7`; Charter-Rider §2
hit → `refused`. 扶持 computes + routes; the vote / Council decides.

## R1 a/b/c/d (landed offline)

- **(a) provisioning-intent wiring** (`methods/provision.py`) — the in-kind rails are mapped to
  the **real producing actor DIDs**: `mitsuho` (food), `hikari` (energy), `okaimono` (tooling),
  `iyashi` (care), `commons-land` (housing, LANDS.md), `murakumo` (compute), `warifu` (liquidity).
  Each is a **dry-run** intent: `published=false` (G10), `cash=0` (G2), `serverHeldKey=false` (G9).
  The liquidity intent is `member_principal` (the member borrows via warifu 0%; 扶持 never pays).
- **(b) real 1 SBT = 1 vote + 48h timelock** (`methods/vote.py`) — ballots dedupe by DID
  (1 SBT = 1 vote), `weight≡1` (no plutocracy), a `:server` voter is unrepresentable, ballots
  outside the window don't count, and `finalize()` **raises** if the 48h timelock has not elapsed.
- **(c) toritate booking + kanae flow viz** (`methods/book.py`) — each accepted in-kind rail is
  projected into a **toritate `ledgerEntry`** using toritate's own category enum
  (`subsistence-flow`/`vocation-flow`/`care-flow`), `cashStipendUsd≡0`, no payroll/wage; the
  member-principal liquidity rail is **not booked as income**. A **kanae-renderable** internal
  sustenance-flow graph (`:flow/*`: Public Fund → 扶持 → provider → maintainer) is emitted for
  the viz layer (NOT the government `fundFlowEdge`).
- **(d) Displacement-Dividend coupling** (`methods/couple.py` + `cohortEarmark` lexicon +
  `:event/:earmark/:couple`) — the structural join to the labor-liberation mission's other half: a
  **displacing actor's surplus** → donation → **TitheRouter 10% split** (`gross = tithe + earmark`,
  exact) → a **per-cohort Public-Fund earmark** that is the imputed-value budget ceiling 扶持's
  in-kind sustenance for that cohort draws on. The **G2 coupling gate** (ADR-2606032130): a
  displacement is admissible **only against a funded cohort earmark with headroom** — *no live
  displacement without a funded cohort* (an unfunded / over-committed cohort is REFUSED). Honest:
  the surplus→donation is real USDC into the Public Fund; what the maintainer receives stays in-kind
  (cash≡0).

## R1 (live-but-gated)

Each outward leg now has a **live path that refuses by default** (`methods/live_gate.py`), exactly
as yadori's live RDAP fetch refuses without `YADORI_ALLOW_LIVE_RDAP=1`:

- `provision.dispatch_live` · `vote.finalize_binding` · `book.write_live` · `couple.commit_live`
  each call `live_gate.require()`, which **raises `LiveGateRefused`** unless ALL of:
  1. the operator process flag `FUCHI_ALLOW_LIVE_<LEG>=1` is set (an operator action on the box);
  2. an **operator attestation** DID is present;
  3. Council **Lv6+** has ratified (**Lv7+** for `couple` — invariant-adjacent: it binds the
     robotics displacement wave);
  4. a **member signature** is present (no-server-key — the server can never sign, ADR-2605231525).
- The gate is an **authorization membrane, never an invariant override**: cash≡0, no-server-key,
  in-kind-only rails, the 48h vote timelock, and the G2 funded-cohort gate all still hold in live
  mode (the `couple` leg stacks `LiveGateRefused` *and* the G2 `ValueError`).
- `analyze.py` prints every leg **refused** in a dry run and emits `out/live-gate-status.kotoba.edn`.

Actual live execution (real dispatch / on-chain binding vote / live toritate write / binding
displacement commit) still needs the env flag flipped **and** Council ratification — it cannot
happen on this branch.

## Layout

```
fuchi/
├── manifest.jsonld
├── data/seed-sustenance-graph.kotoba.edn   # :representative: 5 maintainers + ballots + 2 cohorts
├── lex/                                     # 9 com.etzhayyim.fuchi.* lexicons
├── methods/
│   ├── allocate.py        # tenure-weighted in-kind shares + floors; cash≡0; G1 allowlist
│   ├── route.py           # envelope → in-kind rails + the pure-function gov_route
│   ├── provision.py       # R1(a) rails → real producing-actor provisioning intents
│   ├── vote.py            # R1(b) 1 SBT = 1 vote + 48h timelock
│   ├── book.py            # R1(c) toritate ledgerEntry + kanae :flow/* graph
│   ├── couple.py          # R1(d) Displacement-Dividend earmark + G2 coupling gate
│   ├── live_gate.py       # R1(live) operator+Council+member gate; every leg refuses by default
│   └── analyze.py         # end-to-end dry-run → out/*.kotoba.edn + allocation-dryrun.md
└── cells/                 # 5 coded state machines (.solve() raises at R0)
```

## Run

```bash
./run_tests.sh                 # 174 tests green
python3 methods/analyze.py     # end-to-end a→b→c→d dry-run scorecard + out/*.kotoba.edn
```

## Honest R0/R1

Design + offline allocation only. `:representative` seed. No live disbursement / provisioning /
land grant / binding vote (all G10 — Council Lv6+ + operator; invariant-adjacent Lv7+). The R1
a/b/c engines are built and tested **offline**; flipping them to live is a future ADR + Council
gate. 扶持 cannot eliminate a maintainer's external fiat obligations — it maximizes in-kind
coverage and routes the residual to member-principal 0% liquidity (N4). **Zero invariant amendments.**
