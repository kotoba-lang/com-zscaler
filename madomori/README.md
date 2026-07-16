# madomori 窓守

**High-rise / façade window-cleaning robotics — coverage path + wind/sway envelope + adhesion.**
Tier-B actor · ADR-2606142020 · 🟡 R0 (design + sim) · Clojure-first.

madomori **removes a human from the rope/cradle** on a high-rise façade — the GAP the
robotics remote-work survey (ADR-2606073001 §4) named the **highest unmet remote value**
("高所・façade window cleaning — fall fatality; current GAP"). kiyome 清め is indoor/ground
only; madomori covers fall-risk façade work.

It mirrors the **kuramori 倉守 reference Clojure-first idiom** (ADR-2606142000): pure methods,
no deps → runnable under both `bb` and the kotoba pywasm runtime.

## Run

```bash
bb --classpath 20-actors 20-actors/madomori/methods/test_madomori.clj   # 14 tests / 54 assertions
bb --classpath 20-actors -m madomori.methods.analyze                    # → façade R0 report
bb --classpath 20-actors -m madomori.methods.datom-emit                 # → kotoba EAVT Datom log
```

## What it does

| Method | Role |
|---|---|
| `facade_path.clj`   | boustrophedon (S-shape) coverage path over a pane grid · path length · per-pane water + agent budget (G2) · coverage completeness (every pane once) |
| `wind_envelope.clj` | rope/BMU pendulum sway model · **wind work-stop** + **≥2 fall-arrest anchors** (★ G5 — both RAISE on violation) |
| `adhesion.clj`      | suction adhesion force vs payload × surface factor-of-safety · **adhesion-safe? RAISES below the required margin** (★ G7) |
| `analyze.clj`       | end-to-end: load seed → coverage + budget → wind/sway envelope → adhesion FoS → **GO?** (both safety gates pass) |
| `datom_emit.clj`    | kotoba EAVT projection (`:mado.*` GROUND + `:bond/*` DERIVED transient; G3 structural — only the on-device imagery flag is emittable) |

## Gates

R0 design+sim only (G1, no-server-key) · water + chemical minimization (G2) · ★
privacy-by-construction — on-device imagery only, no person/interior recognition (G3) ·
Displacement-Dividend-coupled (G4) · ★ wind work-stop + fall-arrest redundancy raise (G5) ·
Murakumo-only (G6) · ★ adhesion factor-of-safety raises (G7) · tazuna-teleoperable (G8).
See `CLAUDE.md` for the full text.

Apache 2.0 + etzhayyim Charter Compliance Rider v3.1.
