# shionome (潮目) — cross-asset capital-flow observatory

**DID**: `did:web:etzhayyim.com:actor:shionome` · **Tier**: B · **Status**: R0 · **ADR**: 2606072200

**Read the root `/CLAUDE.md` Charter + substrate rules first.** shionome-specific invariants below
OVERRIDE nothing in the Charter; they make it concrete for this actor.

## The one-sentence identity

潮目 (shionome = the tide-rip where two currents meet — where capital rotation becomes readable)
ingests PUBLIC market data across asset classes (equities / govt-bonds / credit / commodities /
FX / crypto / real-estate / cash) and weaves OBSERVED capital movement — **どこからどこへ資金が
流れているか** — into ONE kotoba Datom flow-graph, then narrates aggregate findings as **dry-run
social posts**. An observational **MIRROR** of where money MOVED — **it never trades**
(トレードはしない): no buy/sell signal, no price target, no portfolio call, ever.

## Where shionome sits among its siblings (no overlap)

| actor | object | shionome's relation |
|---|---|---|
| **mitooshi** 見通し | probabilistic FORECASTS (distribution-only, never trades) | shionome observes **realized** flows, never forecasts; shared never-trade discipline; shionome flows can feed mitooshi |
| **kanae** 鼎 | renders fund flows (Sankey/treemap) | shionome emits the capital-movement `:flow` datoms kanae visualizes |
| **kanjo** 勘定 | company financial DISCLOSURE (BS/PL/CF) | shionome is cross-asset price/flow MOVEMENT, not company fundamentals |
| **watari** 渡り | live physical positions (ship/aircraft) | shionome is capital positions; both append-only as-of, no person-tracking |
| **kabuto** 兜 | supply-chain concentration | shared HHI / edge-primary discipline, disjoint domain |

## The pipeline

```
ingest ─▶ flow_graph ─▶ rotation_weave ─▶ regime_observer ─▶ social_post (dry-run)
(public    (net flow    (rotation pairs,    (risk-on/off,       (member-signed, mirror
 market     per bucket)   inflow HHI,         descriptive only)   disclaimer, no-trade
 data)                    by-class/region)                        body scan, ≥2 sources)
```

`methods/weave.py` is the heart: it validates every bucket/flow/snapshot against the closed vocab,
builds the graph, and computes aggregate **edge-primary** metrics — net flow per bucket (where
money is going/leaving), rotation pairs (どこからどこへ), per-bucket inflow HHI, by-asset-class /
by-region slices, a FACTUAL cross-asset regime descriptor, and the **stock pyramid** (`stock_pyramid`
— the money-and-markets "how big is everything" sizing view from `:outstanding-usd` snapshots).
Nothing is a per-asset rating or signal.

The STOCK layer sits *alongside* the flow graph: a bucket may carry an `:outstanding-usd` snapshot
(the total SIZE of an asset class in USD trillions). `stock_pyramid` aggregates the latest such
snapshot per asset class into the Visual-Capitalist money pyramid (physical currency < broad money
< equities < debt < real estate < derivatives notional, with gold/crypto sized against them). A
SIZE is a factual observed quantity (like `:return-pct`) carrying `no_trade_notice=true` — **NOT** a
rating/signal/target (G2/G4 untouched). Stock (usd-tn) is **never** summed with flow magnitudes
(usd-bn): two distinct on-read views over the same append-only graph.

`methods/grounding.py` is an OPTIONAL **entity-grounding bridge** answering *who is inside each
layer?* — it decomposes a pyramid layer into the named real entities sibling actors already mirror
(equities ← kabuto listed-company ledger, with disclosure DEPTH ← kanjō; a systemic-institutions
overlay ← hokorobi) and reports the coverage gap honestly (value coverage as a stated lower bound, a
`:representative` count denominator, and a per-layer **roadmap** that names — for every layer — the
candidate source actor and, where `ungroundable-at-r0`, the explicit reason; e.g. the debt layer is
a bond-market aggregate and must NOT be conflated with kanjō corporate-BS liabilities). Rules: it is
**fail-open** (a missing
sibling ledger → that layer is reported ungrounded, never a crash); the **core path
(`weave`/`concentration`) must NOT import it** (no sibling-file coupling in the hermetic core);
its figures are sizes/counts/fractions only — never a per-entity rating/signal/target (G2/G4).

`methods/autorun.py` is the **autonomous heartbeat**: each cycle it runs the whole pipeline by
itself and persists a content-addressed transaction to the append-only kotoba Datom log
(`methods/kotoba.py`). That is "kotoba で自律的に稼働" in the charter-permitted form — live external
posting/ingest stays G8-gated (one human gate-flip away).

## The 11 gates — do NOT weaken

Structural invariants live in **three places each** (ontology `:db/allowed`/closed-vocab vectors
+ lexicon `:const`/`:enum` + Python `ValueError`/refusal). Touch one, touch all three or you've
made a charter violation representable. `methods/test_charter_invariants.py` guards this.

- **G1 public-bucket-only / no-doxxing** — `:bucket/scope ∈ {:asset-class :sector :region :theme}`.
  `:individual`/`:account`/`:portfolio`/`:trader`/`:person` are **unrepresentable**. shionome maps
  public capital categories, never individual investors. **This is the no-doxxing invariant.**
- **G2 no-trade / non-advisory (トレードはしない)** — `:flow/kind` + `:snap/metric` are **factual
  observation** closed vocabs; trade/advisory tokens (`buy`/`sell`/`long`/`short`/`recommend`/
  `overweight`/`target-price`/`推奨`/`買い`/`売り`/`目標株価`/…) are **not enum members**, are
  refused on flows/buckets, AND are scanned out of every post body. `noTradeNotice` is `const true`.
  **THE DEFINING INVARIANT.** shionome records where capital MOVED, never what to do.
- **G3 source-provenance mandatory** — every `:flow` carries **≥2** public-source citations; a
  commercial market-data terminal (Bloomberg/Refinitiv/FactSet/CapIQ/四季報…) is a **prohibited
  citation** (Rider §2(e)/N5). An under-sourced or terminal-cited flow **raises**.
- **G4 edge-primary, no rating-of-an-asset** — concentration is computed **on read** from flows.
  `:bucket/rating`/`:bucket/signal`/`:bucket/target`/`:bucket/score` **do not exist** (the schema
  has no such attr; `validate_bucket` raises if one appears) — those would be trade instructions.
- **G5 mirror-not-signal** — every post is `isMirror const true`, opens with the observation
  disclaimer, and never speaks AS a market maker. An observational map, **never a trade signal**.
- **G6 Murakumo-only** — any LLM narration runs on the Murakumo fleet (ADR-2605215000).
- **G7 no-server-key** — posts are member-signed; `serverHeldKey const false`; the server never
  signs (ADR-2605231525).
- **G8 outward-gated** — live market-data ingest + live posting = Council Lv6+ + operator + member
  signature; R0 = offline analyzer + **dry-run** posts + an autonomous loop that persists to the
  **local** kotoba log only (no external I/O); `:post/status` is `:dry-run` only (`:published`
  unrepresentable); every cell `.solve()` raises.
- **G9 PII-encrypted** — public-bucket only, but any incidentally-sensitive datum →
  XChaCha20-Poly1305 envelope (ADR-2605181100).
- **G10 non-eschatological as-of** — flows/snapshots are append-only observation history on the
  content-addressed Datom DAG; a re-observation is a NEW datom, never an overwrite.
- **G11 sourcing-honesty** — every datom declares `:representative` | `:authoritative`; the
  committed seed is `:representative`; registry verification status drives `:authoritative`.

## When editing

- The closed vocab in `00-contracts/schemas/capital-flow-ontology.kotoba.edn`
  (`:ontology/bucket-scopes`, `:ontology/flow-kinds`, `:ontology/snapshot-metrics`,
  `:ontology/post-statuses`) is the single source the invariant test parses. Adding a
  trade-bearing flow kind, a private bucket scope, a `:bucket/rating`, or a `:published` post
  status fails `test_charter_invariants.py`.
- `weave.TRADE_TOKENS` is the Python mirror of the no-trade rule; keep it in sync with the cell
  state machines (`cells/*/state_machine.py`) and `social._guard_no_trade`.
- `.solve()` raises `RuntimeError` on every cell at R0 — live execution is G8-gated. Do not wire a
  cell to a live market-data fetch or a live firehose post.
- Tests are standalone-runnable (`python3 test_*.py`); run everything with `./run_tests.sh`
  (182 tests across 15 suites, hermetic). See MATURITY.md for the per-suite breakdown.

## Honest R0

Design + data-model + offline analyzer + dry-run posts + an autonomous heartbeat loop that
persists to the **local** append-only kotoba Datom log. The seed is bounded `:representative`
(public asset categories, rounded figures) — **not** a live authoritative capture; buckets are
public asset/sector/region/theme categories, never named persons/accounts. Live full-universe
ingest (FRED / US Treasury / ICI / SEC EDGAR / BOJ 資金循環 / JPX / ECB / IMF-IIF / EIA / LBMA /
on-chain / Census) and live social posting are Council Lv6+ + operator gated (Lv7+ for live
publication under 1 SBT = 1 vote).

## Build / test / run autonomously

```
./run_tests.sh                          # all 15 suites (182 tests)
cd methods && python3 weave.py          # concentration + stock pyramid over the :representative seed
cd methods && python3 grounding.py      # decompose pyramid layers into named entities (kabuto/hokorobi) + coverage
cd methods && python3 analyze.py        # end-to-end dry-run → methods/out/intel-report.md
cd methods && python3 social.py         # dry-run social posts
cd methods && python3 ingest.py         # offline normalize (──live refuses without the G8 gate)
cd methods && python3 autorun.py --cycles 3 --fresh   # AUTONOMOUS loop → kotoba Datom log
```

## Do not

- Do not emit a buy/sell signal, price target, over/under-weight call, or portfolio instruction —
  G2 (トレードはしない). Trade/advisory tokens are unrepresentable on flows, buckets, and posts.
- Do not add a `:bucket/rating`/`:signal`/`:target`/`:score`, or a private `:bucket/scope`
  (`:individual`/`:account`/`:portfolio`) — G1/G4 (`validate_bucket` raises).
- Do not add an individual-investor/account field to a bucket (`:account`/`:holder`/`:wallet`/…) —
  G9/G1 no-doxxing (PII lives encrypted off-graph).
- Do not accept an under-sourced (<2) flow, or one citing a commercial terminal — G3 / Rider §2(e).
- Do not let a post be `:published` or `serverHeldKey:true` — G7/G8.
- Do not wire `autorun.py` or any cell `.solve()` to live external I/O — R0 persists to the local
  kotoba log only; live ingest/posting is G8-gated.
- Do not route narration through a commercial GPU — G6 (Murakumo-only).
- Do not use RisingWave/SQL/Datomic — kotoba Datom log only (N7).
