# 潮目 (shionome) — source verification + commercial-terminal deny-list

**ADR**: 2606072200 · per Charter Rider §2(e) + N5.

`registry/sources.seed.json` is an **R1-readiness** catalog of already-public **primary/official**
market-data sources. Every entry is `verificationStatus: "unverified-seed"`. Per **G8**, NO live
ingestion runs against an unverified/stale source until a maintainer + Council (Lv6+ + operator)
flips it to `verified`; `registry.sourcing_for` then lets a record from that source be
`:authoritative` (else it stays `:representative`, G11).

## The no-commercial-terminal deny-list (Charter Rider §2(e) / N5)

shionome cites the **public record**, never a paywalled compilation. A derived datom (or a
dry-run post) that cites any of the following is **refused on every path** (seed / ingest /
analyze / social) by `weave.source_denied` + `registry.assert_source_allowed`:

```
bloomberg terminal · bloomberg.com/professional · refinitiv · eikon · factset ·
capital iq / capiq · morningstar direct · pitchbook · tradingview premium ·
koyfin pro · 四季報 · quick · sell-side desk
```

This is an **anti-gatekeeping** invariant: a capital-flow observatory built on a paywalled
terminal would re-introduce the information asymmetry the commons exists to dissolve.

## What is NOT here (by construction)

- **No brokerage / order-routing / execution venue** — shionome never trades (トレードはしない, G2);
  it has no path to a broker, an exchange order book, or a settlement rail.
- **No individual-investor / account / position source** — nodes are public capital BUCKETS, never
  persons or accounts (G1/G9 no-doxxing).
- **No alternative-data vendor that re-sells scraped personal/location data** — public official +
  exchange + central-bank + on-chain primary data only (G3).
