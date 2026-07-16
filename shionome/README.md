# 潮目 shionome — cross-asset capital-flow observatory

> どこからどこへ資金が流れているか を観測する。**トレードはしない。**

`did:web:etzhayyim.com:actor:shionome` · Tier-B · R0 · ADR-2606072200

潮目 (*shionome*, the tide-rip where two ocean currents meet — fishermen read it to find where
fish gather) ingests **public market data** across asset classes — equities, government bonds,
credit, commodities, FX, crypto, real estate, cash — and weaves the **observed movement of
capital** into one kotoba Datom flow-graph: which bucket money is entering, which it is leaving,
and the rotation pairs between them. It narrates the aggregate picture as **dry-run social posts**.

It is an **observational mirror of where money moved** — **never a trading system**. No buy/sell
signal, no price target, no over/under-weight call, no portfolio instruction. Ever.

## What it answers

- **Where is money going / leaving?** — net flow per bucket
- **From where, to where?** — ranked rotation pairs (どこからどこへ)
- **Is capital crowding?** — inflow concentration (HHI)
- **Which asset class / region?** — by-asset-class + by-region slices
- **How big is everything?** — the **stock pyramid** (`stock_pyramid`): the money-and-markets
  sizing view (à la Visual Capitalist) from `:outstanding-usd` snapshots — total SIZE per asset
  class in USD trillions (physical currency < broad money < equities < debt < real estate <
  derivatives notional, gold/crypto sized against them). A *size*, never a signal (トレードはしない).
- **Who is inside each layer?** — the **entity-grounding bridge** (`grounding.py`): decomposes a
  pyramid layer into the named real entities sibling actors already mirror (equities ← kabuto's
  1,719 listed companies, with disclosure depth ← kanjō; a systemic-institutions overlay ← hokorobi)
  and reports the coverage gap HONESTLY — value coverage as a stated lower bound, a `:representative`
  count denominator, and a per-layer **roadmap** naming why each ungrounded layer cannot yet be
  entity-decomposed.
- **What's the cross-asset mood?** — a *factual* risk-on / risk-off / mixed regime descriptor

## What it is NOT (by construction)

| Not | Why |
|---|---|
| a trading system / signal service / robo-advisor | **トレードはしない** (G2) — no broker, no order book, no execution, no signal/target |
| investment advice | non-advisory mirror only (G5) |
| a per-asset rating / score | edge-primary aggregates only; no per-bucket score (G4) |
| a surveillance system of investors | public market aggregates only; buckets are categories, never persons/accounts (G1/G9) |
| a commercial market-data terminal | Bloomberg / Refinitiv / FactSet / CapIQ / 四季報 prohibited as a source (G3 / Rider §2(e)) |
| a forecaster | mitooshi 見通し owns forecasts; shionome observes realized flows |

## Substrate

State is the **kotoba Datom log** (append-only, content-addressed EAVT — ADR-2605312345). The
autonomous heartbeat (`methods/autorun.py`) persists each observation cycle as a content-addressed
transaction linked into a verifiable commit-DAG. Inference/narration is Murakumo-only
(ADR-2605215000). No RisingWave / SQL.

## Run

```bash
./run_tests.sh                                       # 182 tests, 15 suites
cd methods && python3 weave.py                       # concentration + stock pyramid
cd methods && python3 grounding.py                   # who is inside each layer + coverage gap
cd methods && python3 analyze.py                     # dry-run intel report
cd methods && python3 autorun.py --cycles 3 --fresh  # autonomous loop over the kotoba Datom log
```

Live market-data ingest and live social posting are **outward-gated** (Council Lv6+ + operator +
member signature). R0 is design + offline analyzer + dry-run posts + autonomous-over-local-log.

Apache-2.0 + etzhayyim Charter Compliance Rider v3.0 (`/CHARTER-RIDER.md`).
