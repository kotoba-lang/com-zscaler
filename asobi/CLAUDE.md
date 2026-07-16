# asobi 遊び — freed-time / play & cultural-expression (遊び) KG mirror

**ADR**: 2606073200 · **depends**: 2606073000 (inochi — the sibling pattern) + 2606011000
(engi-organism) · 2605081300 (edge-primary karma) · 2606061900 (kawaraban — mirror MEDIUM
stance) · 2605301600 (danjo) · 2605312345 (Datom = canonical state) · 2605215000
(Murakumo-only). **Status**: 🟡 R0 design-only.

asobi ("遊び" = play / leisure / sacred free-play) is the **freed-time-telos sibling** of the
KG-mirror lineage (inochi / tsumugi / danjo / kanae / keizu). It applies the same
edge-primary, non-adjudicating, aggregate-first KG-mirror architecture to **play, body, and
expression** — sport, recreation, music, film, stage, museums, games, the meal-as-experience
— i.e. the **telos of labor liberation**: what the freed time is *for* (Charter mission).

It weaves **cultural works / events / venues / practices** and the **enclosures** that gate
them into the kotoba Datom log, and surfaces **participation-openness** (the access surface
to keep wide) vs **enclosure** (paywall / attention-platform / lock), routed to **OPENING**.
It closes coverage-gap **C** of ADR-2606073000.

## Hard gates (constitutional — read before any change)

- **G1 — PARTICIPATION / ACCESS map, NEVER an engagement ranking.** This is the defining
  inversion of the entertainment-industrial complex. asobi has **no retention metric, no
  recommend-for-time-on-platform, no per-work popularity score**. The 取-holder is the
  **enclosure**; the bearer is the **play**; the routing is **opening**. It is never a
  spectacle / attention / virality map.
- **G2 — edge-primary (N1).** 取 lives ONLY on edges (`:en/access-load`). A node's
  participation-openness = the **integral of its incident opening 縁** (affordance ×
  disclosed access weight), computed **on read** — never a stored score. There is no
  `:asobi/popularity-of-work`.
- **G3 — non-adjudicating (N3).** Access categories (`:public-domain` … `:proprietary`) are
  **DISCLOSED facts**, never asobi verdicts. asobi datafies the access structure; it does not
  rank the worth of a work.
- **G4 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. Never a private registry.
- **G5 — sourcing honesty.** Every record `:authoritative | :representative`; coverage of all
  culture is ~0 by design (`coverage_report.py` makes it measurable and names the gaps).
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated.** Live ingest (public event / license catalogs) requires Council +
  operator DID. R0 = analyzer + schema + seed only.
- **G8 — no addictive design (Wellbecoming §1.13).** Surfacing spectacle / retention design
  is unrepresentable; asobi serves dynamic flourishing, not engagement.

## Layout

```
20-actors/asobi/
├── CLAUDE.md                          # this file
├── manifest.jsonld                    # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-asobi-graph.kotoba.edn    # real PUBLIC play/expression graph (license + 縁)
├── methods/                           # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                     # edge-primary participation/enclosure analyzer
│   ├── datom_emit.py                  # kotoba Datom-log (EAVT) emitter — canonical state
│   └── coverage_report.py             # honest coverage + gap map (G5)
├── tests/                             # 8 tests, pure stdlib
│   ├── test_analyze.py
│   └── test_coverage.py
├── wasm/
│   └── README.md                      # kotoba pywasm actor (componentize-py) design
└── out/                               # GENERATED — do not hand-edit
    ├── participation-report.md
    ├── asobi-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
cd 20-actors/asobi
python3 methods/analyze.py          # → out/participation-report.md
python3 methods/datom_emit.py       # → out/asobi-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py  # → out/coverage-report.md
python3 tests/test_analyze.py && python3 tests/test_coverage.py   # 8 green
```

## Cross-links

`:enclosure/links` can name a node in the **tsumugi** power-graph where a power-entity
operates an enclosure — the accountability bridge (aggregate-first) from gated play to the
power that gates it. asobi sits beside **kawaraban** (news medium mirror), **kataribe**
(publishing/translation), **manabi** (education), and **inochi** (living world) — together
the mirror lineage covers power, the biosphere, and now the freed-time telos.
