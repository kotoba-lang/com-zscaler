# umisachi 海幸 — seafood / marine-bounty (海産物) KG mirror

**ADR**: 2606074200 · **depends**: 2606073000 (inochi — the KG-mirror pattern) · 2606073200
(asobi — the clj-native mirror trio this is modelled on) · 2605261015 (mitsuho 瑞穂 — the
land-food sibling whose **N9** reserves the ocean for *this* actor) · 2606013400 (funadaiku —
zero-emission cargo ships) · 2606041827 (watari — live vessel position, aggregate lane) ·
2605081300 (edge-primary karma) · 2605301600 (danjo) · 2605312345 (Datom = canonical state) ·
2605215000 (Murakumo-only). **Status**: 🟡 R0 design-only.

umisachi ("海幸" = *umi-sachi*, the bounty of the sea — the mythic counterpart of *yama-sachi*,
and the marine echo of mitsuho 瑞穂, "abundant rice") is the **marine sibling** of the
KG-mirror lineage (inochi / asobi / tsumugi / danjo / kanae / keizu). It is the actor
**mitsuho N9 explicitly hands the ocean to**: mitsuho keeps freshwater aquaculture only and
forbids ocean factory-fishing, reserving the sea for "a separate Funamori-class actor" — this
is that actor, named 海幸 for the kami-naming convention and to avoid colliding with the
silicon-supply "Funamori marine cargo" lane.

It weaves **fish stocks / fisheries + aquaculture / cold-chain logistics / markets** and the
**pressures** that gate sustainable provisioning into the kotoba Datom log, and surfaces, on
read, **provisioning-nourishment** (the equitable-bounty surface to widen) vs **取-depletion**
(overfishing / IUU / enclosure / climate harm), routed to **RESTORATION + equitable
NOURISHMENT**. It closes the seafood/fisheries coverage gap (no dedicated marine actor
existed; mitsuho covers only land + freshwater).

## Hard gates (constitutional — read before any change)

- **G1 — NOURISHMENT / RESTORATION map, NEVER a catch-target list.** The defining dual-use
  refusal. umisachi has **no fishing-ground coordinates, no stock GPS, no "where the fish
  are"** — coordinate-shaped keys (`lat`/`lon`/`gps`/`geohash`/…) are **refused at load**
  (`assert-charter-clean`). Regions are coarse **FAO major-area names** only. The 取-holder is
  the **pressure**; the bearer is the **stock / habitat / market**; the routing is
  **restoration**. It is never a fishing-effort / catch-maximization map.
- **G2 — edge-primary (N1).** 取 lives ONLY on edges (`:en/load`). A stock's depletion = the
  **integral of its incident depletion 縁**, computed **on read** — never a stored score.
  There is no `:umisachi/score-of-stock`.
- **G3 — non-adjudicating (N3).** `:stock/status` (`:sustainable` … `:depleted`) and
  `:market/access` are **DISCLOSED facts** (FAO / RAM Legacy / ICES / IUCN / CCAMLR / ICCAT /
  MSC), never umisachi verdicts. umisachi datafies the structure; it does not rank a stock's
  worth.
- **G4 — factory-fishing is UNREPRESENTABLE as a method.** Destructive methods
  (`:factory-fishing` / `:bottom-trawl` / `:driftnet` / `:iuu` / `:dynamite` / `:cyanide` /
  `:ghost-gear`) **cannot be authored as a `:fishery` method** — refused at load. They appear
  ONLY as a `:pressure` node (the disclosed harm borne), routed to restoration. This inherits
  mitsuho **N1** (no animal slaughter R0–R3 ethics gate) and **N9** (no ocean factory-fishing).
- **G5 — sourcing honesty.** Every record `:authoritative | :representative`; coverage of all
  seafood is ~0 by design (`coverage_report.clj` makes it measurable and names the gaps).
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated.** Live ingest (FAO/RFMO catch statistics, MSC/ASC certificates)
  requires Council + operator DID. R0 = analyzer + schema + seed only.
- **G8 — no person/vessel tracking.** Aggregate stock/fleet scale only; live vessel position
  is watari's aggregate lane. Never "where is vessel X".

## Layout

```
20-actors/umisachi/
├── CLAUDE.md                              # this file
├── manifest.jsonld                        # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-umisachi-graph.kotoba.edn     # PUBLIC marine-provisioning graph (status + access + 縁)
├── methods/                               # CLOJURE (babashka), pure stdlib + clojure.edn
│   ├── analyze.clj                        # edge-primary nourishment/depletion analyzer (+ G1/G4 guard)
│   ├── datom_emit.clj                     # kotoba Datom-log (EAVT) emitter — canonical state
│   └── coverage_report.clj                # honest coverage + gap map (G5)
├── tests/                                 # 16 tests, babashka clojure.test
│   ├── test_analyze.clj                   # 11 tests — load, G1/G4 guards, edge-primary integrals
│   └── test_coverage.clj                  # 5 tests — coverage + Datom-emit round-trip
├── wasm/
│   └── README.md                          # kotoba pywasm/wasm actor (componentize-py) design
└── out/                                   # GENERATED — do not hand-edit
    ├── nourishment-report.md
    ├── umisachi-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
# from repo root (babashka classpath = 20-actors)
bb --classpath 20-actors 20-actors/umisachi/methods/analyze.clj          # → out/nourishment-report.md
bb --classpath 20-actors 20-actors/umisachi/methods/datom_emit.clj       # → out/umisachi-datoms.kotoba.edn (EAVT)
bb --classpath 20-actors 20-actors/umisachi/methods/coverage_report.clj  # → out/coverage-report.md
bb --classpath 20-actors 20-actors/umisachi/tests/test_analyze.clj \
&& bb --classpath 20-actors 20-actors/umisachi/tests/test_coverage.clj   # 16 green
```

## Why Clojure (not Python)

umisachi is authored **clj-native** on babashka: `clojure.edn/read-string` reads the seed
graph directly (no custom EDN tokenizer), `pr-str` serializes datoms losslessly, and the same
namespaces target the kotoba WASM Component Model runtime. It is the first food/logistics
mirror actor written as a working Clojure port rather than a Python-first scaffold (the
sibling `funadaiku`/`ainori` clj ports landed in the same wave).

## Cross-links

`:pressure/links` can name a node in the **tsumugi** / **kabuto** power-graph where a
power-entity operates a pressure (e.g. a distant-water fleet or a distribution monopoly) — the
accountability bridge (aggregate-first) from depleted bounty to the power that depletes it.
umisachi sits beside **mitsuho** (land food), **inochi** (living world / the biosphere it
draws from), **watari** (live vessel position), **watatsuna** (submarine cable), **funadaiku**
(the zero-emission ships that carry the catch), and **okaimono** (provisioning commons that
consumes it).
