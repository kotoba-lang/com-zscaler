# hoshimori 星守 — off-Earth / orbital (軌道) stewardship mirror

**ADR**: 2606073600 · **depends**: 2606073000 (inochi) + 2606073200 (asobi) + 2606073400
(hokorobi — sibling pattern) · 2605192330 (orbital land-sovereignty claim) · 2606041827
(watari — live ship/aircraft KG) · 2606012600 (watatsuna — cable chokepoints) · 2605312345
(Datom = canonical state) · 2605215000 (Murakumo-only). **Status**: 🟡 R0 design-only.

hoshimori ("星守" = guardian of the things in the sky) is the **orbital sibling** of the
live/infrastructure-resilience lineage (watari for ships/aircraft, watatsuna for submarine
cables). It mirrors **public orbital catalogs** — orbital regimes, operators/constellations,
hazards, and the public services that depend on orbit — into the kotoba Datom log, and
surfaces **orbital-congestion concentration** (which regimes bear the most crowding /
collision / debris risk) vs **stewardship** (remediation / deconfliction / disposal), routed
to **STEWARDSHIP** (orbital sustainability). It sits under the orbital land-sovereignty claim
(ADR-2605192330).

It closes coverage-gap **B** of ADR-2606073000.

## Hard gates (constitutional — read before any change)

- **G1 — STEWARDSHIP map, NEVER a targeting / interception aid.** This is the defining
  inversion, and it is load-bearing because orbital position data is **dual-use**. hoshimori
  mirrors **only already-public catalogs**; it emits **no precise predictive ephemeris** (no
  interception-grade state vector); all positional facts are **orbital-shell / regime-aggregate
  band labels**. ASAT / kinetic-intercept / collision-causing uses are **unrepresentable**
  (Charter §1.12 Transparent-Force: open + on-chain + 1 SBT = 1 vote). A dedicated test
  (`test_g1_no_precise_ephemeris`) asserts no per-object lat/lon/alt/velocity/TLE attribute.
- **G2 — edge-primary (N1).** Congestion lives ONLY on edges (`:en/orbit-load`). A regime's
  congestion-concentration = the **integral of its incident inbound hazard/occupancy 縁**
  (severity × disclosed regime weight), computed **on read** — never a stored per-object
  score. There is no `:hoshimori/threat-of-object`.
- **G3 — non-adjudicating (N3).** Orbital-regime definitions and named **public** debris
  EVENTS (e.g. FY-1C 2007, Cosmos-1408 2021) are DISCLOSED facts, never hoshimori verdicts.
- **G4 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. Never a private/covert
  orbital registry.
- **G5 — sourcing honesty.** Every record `:authoritative | :representative`; orbit-load
  values are **representative severities, not measured conjunction probabilities**.
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated.** Live catalog ingest (space-track / CelesTrak-shaped public feeds)
  requires Council + operator DID. R0 = analyzer + schema + seed only.
- **G8 — observation-only.** hoshimori operates no spacecraft and conducts no maneuver; it
  observes the public orbital commons and routes to stewardship.

## Layout

```
20-actors/hoshimori/
├── CLAUDE.md                          # this file
├── manifest.jsonld                    # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-orbit-graph.kotoba.edn    # real PUBLIC regimes/operators/hazards/services + 縁
├── methods/                           # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                     # edge-primary congestion vs stewardship analyzer
│   ├── datom_emit.py                  # kotoba Datom-log (EAVT) emitter — canonical state
│   └── coverage_report.py             # honest coverage + gap map (G5)
├── tests/                             # 9 tests, pure stdlib (incl. G1 no-ephemeris)
│   ├── test_analyze.py
│   └── test_coverage.py
├── wasm/
│   └── README.md                      # kotoba pywasm actor (componentize-py) design
└── out/                               # GENERATED — do not hand-edit
    ├── congestion-report.md
    ├── orbit-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
cd 20-actors/hoshimori
python3 methods/analyze.py          # → out/congestion-report.md
python3 methods/datom_emit.py       # → out/orbit-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py  # → out/coverage-report.md
python3 tests/test_analyze.py && python3 tests/test_coverage.py   # 9 green
```

## Cross-links

hoshimori is the orbital member of the resilience-map family: **watari** (live ship/aircraft
positions → safety), **watatsuna** (submarine-cable chokepoints → redundancy), and now
hoshimori (orbital congestion → stewardship). All three are chokepoint/concentration mirrors
routed to resilience, never target-lists. The seed surfaces **LEO-low** as the top congestion
concentrator (megaconstellation + debris band) and **PNT-on-MEO** as a top service-dependency
fragility — both routed to deconfliction and active-debris-removal, never to harm.
