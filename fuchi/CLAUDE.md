# 扶持 (fuchi) — CLAUDE guidance

Mission-aligned **maintainer sustenance allocator** — the charter-clean **inverse of a business
investment fund** (ADR-2606052300). Read the repo-root `CLAUDE.md` first; this file only adds
fuchi-specific rules.

## What this actor is (and is NOT)

- **IS**: a covenant-gated, tenure-weighted, **in-kind** sustenance allocator for the real-world
  maintainers (信者) of etzhayyim's actors. A horizontal control-plane over Public Fund +
  Displacement Dividend + Basic-High-Income-in-kind.
- **IS NOT**: an investment fund / lender / employer. No equity, no ROI, no debt, no exit, no wage,
  no cash. These are not policy choices — they are **structurally unrepresentable**.

## Hard rules (do not weaken — each is a 3-place invariant)

- **G1 no investment vehicle** — `:alloc/instrument` may only be `in-kind-grant | sustenance |
  tooling-access | compute-access`. Never add `equity`/`debt`/`revenue-share`/`carry`/`dividend`/
  `exit` to the schema enum, the lexicon enum, or `allocate.ALLOWED_INSTRUMENTS`.
- **G2 cash≡0** — `:envelope/cash-usd-micros` and `:alloc/cash-usd-micros` are `:db/allowed [0]`.
  Never introduce a cash field that can be nonzero. `Allocation.__post_init__` must keep raising.
- **G3 in-kind rails only** — `:rail/kind` has no `:cash-disbursement`. The only path that touches
  fiat is `:liquidity-warifu` with `member_principal = true` (the member pays; 扶持 never does).
- **G5 payoff帰属 = etzhayyim** — `:maintainer/owns-payoff` is `:db/allowed [false]`. A maintainer
  never holds a stake.
- **G7 non-adjudicating** — `gov_route` is a pure function; never add a `:gov/decision` attribute
  or let a cell/operator decide accept/reject. The vote / Council decides.
- **G9 no-server-key** — `:alloc/server-held-key` is `:db/allowed [false]` (ADR-2605231525).
- **G8 Murakumo-only** inference; **G10 outward-gated** — every live leg goes through
  `methods/live_gate.py` and **REFUSES by default**. A live `provision`/`vote`/`book`/`couple`
  fires only when the operator flag `FUCHI_ALLOW_LIVE_<LEG>=1` + an operator attestation +
  Council Lv6+ (Lv7+ for `couple`) + a member signature are ALL present. Never let the gate relax
  cash≡0 / no-server-key / in-kind-only / the vote timelock / the G2 funded-cohort gate — it is an
  *authorization* membrane, not an invariant override. Never sign server-side to satisfy it.

## When extending

- Reuse the Displacement-Dividend curve (`50-infra/etzhayyim-public-fund/displacement/allocate.py`)
  — do not invent a second tenure formula.
- New in-kind needs → add an `:envelope/line` + a rail in `LINE_TO_RAIL`, mapped to a **producing
  actor** (mitsuho/hikari/okaimono/iyashi/commons-land/warifu). Never add a rail that pays cash.
- Keep tests green: `./run_tests.sh` (174 tests). The `test_charter_invariants.py` suite parses the
  ontology + lexicons + code and will fail if an invariant drifts out of any of the three places —
  including the R1-live locks (every leg refused by default; `couple` is Lv7).

## Honest framing to preserve in all docs

扶持 cannot make a maintainer's external fiat obligations vanish. Say so. It maximizes in-kind
coverage and routes the residual to member-principal 0% liquidity. Full fiat income is a Charter
Lv7+ matter and is out of scope (N4).
