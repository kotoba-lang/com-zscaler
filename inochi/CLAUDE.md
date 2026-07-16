# inochi 命 — living-world (生命圏) Knowledge Graph mirror

**ADR**: 2606073000 · **depends**: 2606011000 (§D7 産霊の網 / engi-organism) + 2606011500
(spirit-ontology) · 2605081300 (edge-primary karma) · 2605301600 (danjo) · 2605302300
(kanae) · 2606066000 (keizu) · 2605312345 (Datom = canonical state) · 2605215000
(Murakumo-only). **Status**: 🟡 R0 design-only.

inochi ("命" = life) is the **living-world sibling** of the power-mirror lineage
(danjo / tsumugi / ooyake / keizu / kanae). It applies the same KG-mirror architecture to
the **biosphere**: it weaves **species / ecosystems / biomes** and the **pressures** borne
by the living world into the kotoba Datom log, and runs an **edge-primary ecological
取-concentration** pass (custody-debt = extinction / degradation pressure accumulated over
the living world) **routed to RESTORATION** (release).

It closes the single largest coverage gap in the actor roster: the biosphere — animals /
wildlife, forests, oceans / fisheries, biodiversity / ecosystems, and climate-as-it-affects-life
— previously had **no** actor at all, despite the Charter's Tree of Life / Wellbecoming
ontology (ADR-2606073000 §Context).

## Hard gates (constitutional — read before any change)

- **G1 — RESTORATION map, NEVER a target-list.** This is the defining inversion. inochi
  carries **no precise occurrence coordinates** of at-risk taxa (poaching / overfishing
  hazard); spatial readouts are **biome / realm-aggregate only**. The 取-holder is the
  **pressure**; the bearer is the **living world**; the routing is **restoration**. It is
  never a hunting / harvest / exploitation map.
- **G2 — edge-primary (N1).** 取 lives ONLY on edges (`:en/grasping-load`). A node's
  restoration-priority = the **integral of its incident inbound `:pressures` edges**
  (severity × disclosed IUCN weight), computed **on read** — never a stored per-organism
  score. There is no `:biosphere/score-of-species`.
- **G3 — non-adjudicating (N3).** IUCN Red List categories are **DISCLOSED facts**, never
  inochi verdicts. inochi datafies the pressure-and-dependency structure; it does not rank
  the worth of life.
- **G4 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. Never a private registry.
- **G5 — sourcing honesty.** Every record carries `:organism/sourcing :authoritative |
  :representative`. The committed seed is a **bounded** mix (IUCN categories + documented
  trophic / dependency relations); coverage of all life is ~0 by design (`coverage_report.py`
  makes this measurable and names the gaps).
- **G6 — Murakumo-only narration.** Any LLM narration routes through Murakumo (ADR-2605215000).
- **G7 — outward-gated.** Live planet-scale ingest (IUCN / GBIF / IPCC APIs) requires
  Council + operator DID. R0 = analyzer + schema + seed only.
- **G8 — no git-lfs.** Large assets via DataLad → IPFS (`80-data/biosphere`).

## Layout

```
20-actors/inochi/
├── CLAUDE.md                              # this file
├── manifest.jsonld                        # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-biosphere-graph.kotoba.edn    # real PUBLIC biosphere graph (IUCN + trophic 縁)
├── methods/                               # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                         # edge-primary ecological 取-concentration analyzer
│   ├── datom_emit.py                      # kotoba Datom-log (EAVT) emitter — canonical state
│   └── coverage_report.py                 # honest coverage + gap map (G5)
├── tests/                                 # 8 tests, pure stdlib
│   ├── test_analyze.py
│   └── test_coverage.py
├── wasm/
│   └── README.md                          # kotoba pywasm actor (componentize-py) design
└── out/                                   # GENERATED — do not hand-edit
    ├── restoration-report.md
    ├── biosphere-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
cd 20-actors/inochi
python3 methods/analyze.py          # → out/restoration-report.md
python3 methods/datom_emit.py       # → out/biosphere-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py  # → out/coverage-report.md
python3 tests/test_analyze.py && python3 tests/test_coverage.py   # 8 green
```

## Ontology (biosphere-ontology, `00-contracts/schemas/`)

- **nodes** `:organism/kind` ∈ `{:species, :ecosystem, :biome, :pressure}` with taxon /
  eco / pressure facets; `:taxon/iucn` ∈ `{:LC :NT :VU :EN :CR :EW :EX :DD}` (disclosed).
- **edges** `:en/kind` ∈ `{:pressures, :habitat-of, :depends-on, :keystone-of, :predates,
  :pollinates, :migrates-through}` carrying `:en/grasping-load` ∈ [0,1] (where 取 lives).
- **derived** `:bond/restoration-priority` · `:bond/dependency-fragility` ·
  `:bond/pressure-imposed` — transient, computed on read, never persisted (N1/G2).

## Cross-links

`:pressure/links` can name a node in the **tsumugi** power-graph where a power-entity drives
a pressure — the accountability bridge (aggregate-first) from ecological debt to the power
that imposes it (danjo / keizu lineage). inochi observes; it does not adjudicate or target.
