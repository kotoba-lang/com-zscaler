# 系図 (keizu) — public-source verification standards

ADR-2606066000. This governs `sources.seed.json` — the catalog of public sources keizu ingests
at **R1**. Every source is `unverified-seed` until a maintainer/Council verifies it; **no live
ingestion runs against an unverified source** (G8: Council Lv6+ + operator).

## Standards (each source must satisfy these before promotion)

1. **Primary public source only (G3).** The source must be the issuing authority's own published
   record (官報 / 調達ポータル / 政治資金収支報告書 / Federal Register / USAspending / FEC / TED /
   Commission registers / OECD). A secondary aggregator is acceptable only as a *second*
   corroborating citation — never the sole source.
2. **≥2 citations per derived datom (G3).** A relation or money flow keizu emits must cite ≥2
   public records; a committee composition / statement must cite ≥1. The bridge + validators
   enforce this; the registry exists so those citations resolve to a known, verified source.
3. **No commercial gov-intelligence terminals (Charter Rider §2(e), N5).** The following are
   PROHIBITED as sources (anti-gatekeeping — read the public record, never the paywalled
   compilation): **GovWin IQ, Bloomberg Government, Politico Pro, E&E News Pro, FiscalNote,
   CQ Roll Call Pro, 会社四季報, Bloomberg Terminal, Capital IQ, Refinitiv, FactSet, PitchBook,
   Crunchbase, LexisNexis, Westlaw.** `methods/test_sources.py` enforces this deny-list.
4. **Public-power scope only (G1, no-doxxing).** A source's rosters yield **public seats/organs**,
   never private individuals; any incidentally-sensitive personal datum is dropped at ingest
   (`validate_node` PII guard) and never stored on-graph (ADR-2605181100).
5. **Non-adjudicating (G2).** A source is read for facts and ties; keizu never imports a
   characterization of wrongdoing. A source field that reads like a verdict is mapped to a
   factual keizu kind or refused at the import boundary (`bridge.py`).
6. **Sourcing-honesty (G11).** Until verified, derived datoms are `:representative`; only a
   verified source promotes its datoms to `:authoritative`.
7. **Freshness (G8).** `freshnessWindowDays = 180`. A source past its window is re-verified
   before the next ingest; stale coverage is reported, never silently presented as current.

## Status

R1 design artifact. All 12 seed sources are `unverified-seed`. Live ingestion, verification
promotion, and `:authoritative` datoms are Council Lv6+ + operator gated.
