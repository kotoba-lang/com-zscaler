# silicon 珪 — methods/ (runnable fab-flow, cljc + kotoba Datom)

Brings the silicon fab to the same **runnable `.cljc` + kotoba-Datom** level as the
other manufacturing actors (niyaku / giemon / sarutahiko). This is the only code in
the actor that *runs* at R0; the `cells/*` are langgraph Pregel scaffolds whose
`.solve()` stays Council-gated. Per ADR-2605242500 + 2605242545; ADR-2606160842 port wave.

## Modules

| file | what it does |
|---|---|
| `fab_flow.cljc` | 8-工程 wafer-lot **process-physics simulation** — `litho → deposition → etch → implant → cmp → metrology → test → packaging`. Each step is a first-order model (Bossung CD, conformal film, anisotropic etch, LSS implant range, CMP planarization, SPC, Poisson yield `Y=exp(-D·A)`, assembly). `run-lot` threads a lot through the route; defect density accumulates and the test step converts it to die yield. |
| `wafer_handler.cljc` | **wafer-handling robotics** — single-arm cluster-tool transfer kinematics (trapezoidal move profile), loadlock pump/vent, route cycle-time, and steady-state throughput (wafers/hr) bounded by the bottleneck station, plus **SCARA 2-link forward/inverse kinematics + workspace reachability** (kami-genesis PlanarChain-iso, pure cljc). Counterpart to niyaku's `agv_transfer`. |
| `lot_ledger.cljc` | **lot traceability** (G8) — lowers a completed lot record to append-only `:silicon.lot/*` + `:silicon.step/*` EAVT datoms, content-addressed into a sha256 commit-DAG (byte-identical canonicalization to `meisai.methods.kotoba`). `verify-chain` is tamper-evident. |
| `fab_cell.cljc` | **orchestration cell** (datalog/kotoba) — `run-fab-lot` ties it together: station reachability → `fab_flow` sim → `lot_ledger` commit, with optional throughput. `run-reference` runs the iwakura ternary-PE tile lot on a realistic 8-station layout. |

## Hard gates (silicon manifest)

- **G1 dual-use force-review** — `litho` and `implant` drive export-controlled equipment.
  `run-lot` refuses a route containing them unless given `:silen-force-attest` (Charter
  Rider §2(a)(c)). `force-review-required?` is the predicate.
- **G11 outward-gated** — **DRY-RUN / design only**. `dispatch-equipment!` (real fab
  actuation) is structurally unrepresentable at R0 and always raises a `:council-gate`
  ex-info. No method here moves real equipment; live fab is Phase 4 + Council-gated.
- **G6 kotoba-EAVT-native** — lot/step state lowers to Datoms, not SQL.

## Run

```bash
bb -e "(require 'clojure.test 'silicon.methods.test-fab-flow \
  'silicon.methods.test-wafer-handler 'silicon.methods.test-lot-ledger) \
  (clojure.test/run-tests 'silicon.methods.test-fab-flow \
  'silicon.methods.test-wafer-handler 'silicon.methods.test-lot-ledger)"
# or as part of the actor cljc suite:
bb test:pywasm
```

32 tests / 81 assertions green (within the `test:pywasm` suite).

## Demo

```clojure
(require '[silicon.methods.fab-flow :as f] '[silicon.methods.lot-ledger :as l])
(def rec (f/run-lot f/reference-lot f/default-route f/reference-recipe
                    :silen-force-attest "ok: ternary-PE tile, civilian inference ASIC"))
(:yield rec)            ;; => ~0.96 for the healthy baseline recipe
(:packaged-units rec)   ;; => known-good packaged dies
(:tx/cid (l/commit-lot rec))  ;; => content-addressed lot-traceability CID
```

## Scope honesty

These are **deterministic first-order process models**, not TCAD/foundry-calibrated
physics. They make the fab flow *runnable and traceable* end-to-end (recipe → measured
outputs → yield → content-addressed lot ledger) at the same R0 maturity as the rest of the
roster. Real equipment RTL/CAD (the 8 categories under `50-infra/silicon/equipment/`) and
N5 tape-out remain Phase 2–4 and Council-gated.
