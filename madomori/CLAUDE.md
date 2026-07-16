# madomori 窓守 — high-rise / façade window-cleaning robotics

**ADR**: 2606142020 · **depends**: 2606073001 (robotics remote-work coverage/GAP survey —
names 高所・façade window cleaning as the **highest unmet remote-value GAP**, §4 #1) ·
2606032100 (labor-liberation robotics wave — sanae/hataori/kiyome sibling pattern) ·
2606032130 (Displacement Dividend) · 2606042100 (tazuna — teleop substrate) · 2605215000
(Murakumo-only) · 2605312345 (Datom = canonical state). **related**: 2606142000 (kuramori —
the reference Clojure-first actor idiom this mirrors), 2606034800 (manako — on-device vision).
**Status**: 🟡 R0 design + sim only.

madomori ("窓守" = window-keeper) **removes a human from the rope/cradle** on a high-rise
façade — the survey (ADR-2606073001 §4) named this the GAP with the *highest unmet remote
value* ("高所・façade window cleaning — fall fatality; current GAP"). kiyome 清め is
indoor/ground-level only; nothing covered fall-risk façade work. madomori closes it.

**Clojure-first.** Methods are pure Clojure (no deps) → run under both `bb` and the kotoba
pywasm runtime. madomori mirrors the kuramori 倉守 reference idiom (ADR-2606142000) exactly.

## Hard gates (constitutional — read before any change)

- **G1 — design + sim only.** R0 is pure planning compute; it cleans no real glass and moves
  no real robot. Real actuation is Council Lv6+/operator-gated R1 (no-server-key).
- **G2 — water + chemical minimization.** `facade-path/consumable-budget` tracks per-pane
  water + cleaning-agent so a planner can MINIMISE it. Agent is deionized-water + surfactant;
  no prohibited solvents.
- **G3 ★ — privacy-by-construction.** The robot's cameras point AT the glass; imagery is
  **on-device only and NEVER leaves the device**, and there is **no person/interior
  recognition** (no biometric). This is *structural*: the data model has no off-device /
  cloud / interior / biometric attribute — only `:imagery {:on-device-only true :recognition
  :pane-edge-only}` and the single `:mado.robot/imagery-on-device true` Datom are
  representable (mirrors kiyome on-device-no-cloud + manako no-biometric). Asserted in tests.
- **G4 — dividend-coupled.** 高所清掃員 displacement is coupled to a funded
  Displacement-Dividend cohort (ADR-2606032130 G2). No live displacement without it.
- **G5 ★ — wind work-stop + fall-arrest redundancy.** `wind-envelope/work-permitted?`
  **RAISES** at/above the wind work-stop threshold (≈10 m/s; the fall-equivalent gate, *not
  tunable up* by a planner — gusts checked too) OR with **<2 independent fall-arrest
  anchors** (a single-anchor descent is refused). It never returns false — a refusal surfaces.
- **G6 — Murakumo-only** narration/inference (ADR-2605215000).
- **G7 ★ — adhesion factor-of-safety.** `adhesion/adhesion-safe?` **RAISES** when the
  achieved suction/adhesion factor-of-safety is below the required margin (surface-dependent:
  glass seals best, porous stone worst). Adhesion failure on a façade is a fall, so an unsafe
  plan must surface, never be silently forced (mirrors kuramori `assign-slot!` G7 discipline).
- **G8 — tazuna-operated.** Remote operation/teleop is via tazuna 手綱 (ADR-2606042100);
  weaponizable use is unrepresentable.

## Layout

```
20-actors/madomori/
├── CLAUDE.md                       # this file
├── manifest.edn                    # actor manifest (5 cells, 8 gates, Clojure methods)
├── data/
│   └── facade.edn                  # reference high-rise tower face seed (:representative)
├── methods/                        # pure Clojure → bb-runnable AND kotoba-pywasm-portable
│   ├── facade_path.clj             # boustrophedon coverage path + budget (G2) + completeness
│   ├── wind_envelope.clj           # rope/BMU sway + wind work-stop + fall-arrest (★ G5)
│   ├── adhesion.clj                # suction adhesion factor-of-safety (★ G7)
│   ├── analyze.clj                 # end-to-end R0 orchestrator (→ GO? if both gates pass)
│   ├── datom_emit.clj              # kotoba EAVT Datom-log emitter (canonical state)
│   └── test_madomori.clj           # 14 tests / 54 assertions (clojure.test)
└── lex/
    └── cleanAttestation.edn        # per-pass façade-cleaning attestation lexicon
```

## Run

```bash
# from repo root (classpath = 20-actors, ns = madomori.methods.*)
bb --classpath 20-actors 20-actors/madomori/methods/test_madomori.clj   # 14 green
bb --classpath 20-actors -m madomori.methods.analyze                    # → report
bb --classpath 20-actors -m madomori.methods.datom-emit                 # → EAVT Datom log
```

## Why the safety envelope is the heart of the actor

The whole point of madomori is to take a human off the rope. So the safety gates are not
add-ons — they are the actor: the wind work-stop + fall-arrest redundancy gate (G5) and the
adhesion factor-of-safety gate (G7) both **RAISE** rather than return a soft "false", so an
unsafe descent can never be silently planned. The coverage path + budget (G2) and the
on-device-only imagery model (G3) round out the R0 sim. When kiyome's on-device-imagery or
manako's no-biometric stance changes, re-check the G3 invariants here.
