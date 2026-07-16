# hokorobi 綻び — world systemic finance-risk observatory

**ADR**: 2606073400 · **depends**: 2606073000 (inochi) + 2606073200 (asobi — sibling
pattern) · 2606032000 (kanjō — financial-disclosure KG) · 2605302300 (kanae) · 2606022000
(kabuto — concentration metrics) · 2605301600 (danjo) · 2605312345 (Datom = canonical state)
· 2605215000 (Murakumo-only). **Status**: 🟡 R0 design-only.

hokorobi ("綻び" = a seam coming undone / the first fraying of a fabric) is the
**risk-observation sibling** of the disclosure/accountability lineage (kanjō / kanae /
kabuto / danjo). It applies the KG-mirror architecture to **systemic financial risk** —
insurance, banking-lending, and pensions — and surfaces where systemic fragility
concentrates (the resilience surface) vs where resilience buffers absorb it, routed to
**RESILIENCE** (繕い, the mending of the 綻び).

It closes coverage-gap **D** of ADR-2606073000. Crucially, finance **production**
(insurance/banking/lending) is **Charter-excluded** (non-profit only) — so hokorobi is the
**observation** counterpart: it never operates a financial product, it observes the world's
finance-risk structure as a public-interest resilience map.

## Hard gates (constitutional — read before any change)

- **G1 — RESILIENCE map, NEVER a panic / trading signal.** This is the defining inversion of
  market-data terminals. hokorobi is **never a bank-run trigger, never a trading / short /
  market-moving signal, never a per-institution solvency verdict, and it NEVER trades**
  (the `mitooshi` never-trades discipline). Aggregate-first. The 取-holder is the
  **risk-source**; the bearer is the **public** (depositors / pensioners / insured /
  taxpayers); the routing is **resilience**.
- **G2 — edge-primary (N1).** Risk lives ONLY on edges (`:en/risk-load`). A node's
  systemic-risk-concentration = the **integral of its incident inbound risk 縁** (severity ×
  disclosed systemic-importance weight), computed **on read** — never a stored score. There
  is no `:hokorobi/solvency-of-bank`.
- **G3 — non-adjudicating (N3).** Systemic-importance designations (G-SIB / D-SIB / IAIS) are
  **DISCLOSED facts**, never hokorobi verdicts. **No investment advice / forecast** (the
  `kanjō` discipline — disclosure only, no advice, no paid terminal).
- **G4 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. Never a private registry or
  paid terminal.
- **G5 — sourcing honesty.** Every record `:authoritative | :representative`; risk-load values
  are **representative severities, not measured solvency**; coverage of all institutions is ~0
  by design (`coverage_report.py` makes it measurable).
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated.** Live ingest (FSB / IAIS / regulator disclosures) requires Council +
  operator DID. R0 = analyzer + schema + seed only.
- **G8 — observation-only.** Finance **production** is Charter-excluded; hokorobi only observes.

## Layout

```
20-actors/hokorobi/
├── CLAUDE.md                           # this file
├── manifest.jsonld                     # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-finrisk-graph.kotoba.edn   # real PUBLIC institutions (G-SIB/IAIS/pension) + risk 縁
├── methods/                            # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                      # edge-primary systemic-risk vs resilience analyzer
│   ├── datom_emit.py                   # kotoba Datom-log (EAVT) emitter — canonical state
│   └── coverage_report.py              # honest coverage + gap map (G5)
├── tests/                              # 8 tests, pure stdlib
│   ├── test_analyze.py
│   └── test_coverage.py
├── wasm/
│   └── README.md                       # kotoba pywasm actor (componentize-py) design
└── out/                                # GENERATED — do not hand-edit
    ├── systemic-risk-report.md
    ├── finrisk-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
cd 20-actors/hokorobi
python3 methods/analyze.py          # → out/systemic-risk-report.md
python3 methods/datom_emit.py       # → out/finrisk-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py  # → out/coverage-report.md
python3 tests/test_analyze.py && python3 tests/test_coverage.py   # 8 green
```

## Cross-links

hokorobi sits beside **kanjō** (disclosed corporate financials), **kabuto** (supply-chain
concentration / HHI), **kanae** (government fiscal flow), and **mitooshi** (probabilistic
forecasting — never-trades). Together they mirror the financial world from disclosure
(kanjō) through concentration (kabuto) to **systemic fragility (hokorobi)** — observation
only, the production side staying Charter-excluded. The contagion (`:interconnects`) 縁 make
linchpin market infrastructure (clearing CCPs) visible as systemic concentrators routed to
resilience.
