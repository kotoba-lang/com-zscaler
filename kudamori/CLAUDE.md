# kudamori 管守 — sewer / confined-space in-pipe cleaning robotics

**ADR**: 2606142030 · **depends**: 2606073001 (robotics remote-work GAP survey — §4 names
toxic-gas/confined-space death as a high-remote-value GAP) · 2606032100 (labor-liberation
robotics wave) · 2606032130 (Displacement Dividend) · 2606042100 (tazuna — teleop substrate)
· 2605215000 (Murakumo-only) · 2605312345 (Datom = canonical state). **related**:
2606142000 (kuramori — Clojure-first sibling), 2605263100 (mizuho — water/sanitation
treatment; kudamori is the in-pipe cleaning counterpart, effluent handoff). **Status**:
🟡 R0 design + sim only.

kudamori ("管守" = pipe-keeper) **removes the human from the confined space**. The robotics
GAP survey (ADR-2606073001 §4) flagged toxic-gas / confined-space death as a high-remote-value
target; mizuho 水穂 covers wastewater *treatment* but not the in-pipe *cleaning* that sends a
worker down a manhole into an O2-deficient, H2S-laden sewer. kudamori is that body: an
electric in-pipe crawler with a confined-space atmosphere entry gate, in-pipe navigation, and
pressure-safe hydro-jetting.

**Clojure-first.** A sibling of the reference Clojure-first actor kuramori (ADR-2606142000):
methods are pure Clojure (no deps) → run under both `bb` and the kotoba pywasm runtime.

## Hard gates (constitutional — read before any change)

- **G1 — design + sim only.** R0 is pure planning compute; it moves no real robot. Real
  actuation is Council Lv6+/operator-gated R1 (no-server-key). The methods never touch a
  network or a device.
- **G2 — water reuse / eco.** The jetting water-reuse fraction is tracked; the residual
  effluent is **never discharged untreated** — it is handed off to mizuho 水穂
  (ADR-2605263100) for treatment (`jetting/water-balance` `:handoff :mizuho`). Electric
  crawler only.
- **G3 — no worker surveillance.** KPI is metres-cleaned/hour (an *equipment* metric), never
  a per-worker pace ranking or biometric (mirrors kuramori G3 / niyaku G11).
- **G4 — dividend-coupled.** 管路清掃 labour displacement is coupled to a funded
  Displacement-Dividend cohort (ADR-2606032130 G2). No live displacement without it.
- **G5 — ★ confined-space atmosphere gate.** `atmosphere/entry-permitted?` is false and
  `assert-entry!` **RAISES** on ANY unsafe gas reading (O2 19.5–23.5 %, H2S <10 ppm, CH4
  <10 %LEL, CO <35 ppm). Entry without a passing/purged atmosphere is **unrepresentable** —
  `purge-to-entry` models forced ventilation, and only an atmosphere that actually passes the
  gate admits entry (it never lies about safety). This is the gate that removes the human.
- **G6 — Murakumo-only** narration/inference (ADR-2605215000).
- **G7 — ★ no pipe over-pressure.** `jetting/jet-pressure-safe?` is false and
  `assert-jet-pressure!` **RAISES** when the nozzle pressure exceeds the pipe material's
  working rating (VCP/PVC/concrete/ductile-iron). Over-pressure that would damage the pipe is
  unrepresentable — never clamp-and-proceed.
- **G8 — tazuna-operated.** Remote operation/teleop is via tazuna 手綱 (ADR-2606042100);
  weaponizable use is unrepresentable.

## Layout

```
20-actors/kudamori/
├── CLAUDE.md                       # this file
├── manifest.edn                    # actor manifest (5 cells, 8 gates, Clojure methods)
├── data/
│   └── network.edn                 # reference foul-sewer reach seed (:representative)
├── methods/                        # pure Clojure → bb-runnable AND kotoba-pywasm-portable
│   ├── atmosphere.clj              # ★ G5 confined-space entry gate + purge-to-entry
│   ├── pipe_nav.clj                # diameter-fit + BFS route + route-around blockage
│   ├── jetting.clj                 # ★ G7 pressure-safe hydro-jet + debris + water balance
│   ├── analyze.clj                 # end-to-end R0 orchestrator (entry → nav → jetting)
│   ├── datom_emit.clj              # kotoba EAVT Datom-log emitter (canonical state)
│   └── test_kudamori.clj           # 17 tests / 55 assertions (clojure.test)
└── lex/
    └── cleanAttestation.edn        # per-segment cleaning attestation lexicon
```

## Run

```bash
# from repo root (classpath = 20-actors, ns = kudamori.methods.*)
bb --classpath 20-actors 20-actors/kudamori/methods/test_kudamori.clj   # 17 green
bb --classpath 20-actors -m kudamori.methods.analyze                    # → report
bb --classpath 20-actors -m kudamori.methods.datom-emit                 # → EAVT Datom log
```

## The two starred safety gates (verified in code + tests)

The whole point of kudamori is to make two unsafe acts **unrepresentable**, not merely
discouraged: descending into an unsafe atmosphere (G5) and jetting a pipe past its rating
(G7). Both are enforced as *raising* asserts in the methods and proven by dedicated tests
(`entry-refused-on-unsafe-air`, `purge-honest-when-budget-exhausted`,
`jet-over-pressure-raises`, `gated-when-atmosphere-unrecoverable`). When changing the
thresholds or the purge model, re-run the suite — a green suite is the safety invariant.
