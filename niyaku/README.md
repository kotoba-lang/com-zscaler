# niyaku 荷役 — automated port cargo handling (ship↔shore loading/unloading)

> Tier-B actor · `did:web:etzhayyim.com:niyaku` · ADR-2606082000 · **R0 scaffold**
> Operator-side counterpart of **funadaiku** (船大工, builds the ships) · consumer of
> **port** (terminal registry) + **watari** (live vessel position)

**Organism axis**: Axis 2 — Metabolism (代謝 / 産霊 musuhi): moves goods between hull
and shore so the commons can circulate them.

**荷役** (niyaku, *cargo handling*) is the port-side automation the roster was missing.
**funadaiku** builds the zero-emission cargo ship; **niyaku** loads and unloads it. It
closes the gap identified on 2026-06-08: the repo had a cargo-ship *builder* and a port
*registry*, but **no actor that automates container loading/unloading**.

## The defining problem — anti-sway

A ship-to-shore (STS) crane lifts a 20-40 t container on cables and traverses it 30-65 m
between hull and quay. The suspended load is a pendulum; an aggressive trolley move leaves
the box swinging, and it cannot be landed on a stack tier until the sway settles to a few
centimetres. **Anti-sway control is the whole game.**

Dynamically, an STS crane is a **cart + hanging load** — the *same topology* Isaac Sim
ships as **Cartpole** (prismatic trolley + revolute load). niyaku exploits this exactly:
the simulation drives the clean-room `isaacsim.core.api` Cartpole, with the load placed at
the **stable hanging equilibrium** (θ = π), and an anti-sway state feedback lands the box
quiet.

```
naive position push   →  load rings wildly (residual sway grows unbounded)
anti-sway feedback     →  trolley reaches the slot, residual sway < 0.01 rad
```

## Three runnable methods (`methods/`, pure-stdlib · pywasm-ready)

| Method | What it computes |
|---|---|
| `crane_dynamics.py` | gantry/STS anti-sway pendulum model (RK4), state-feedback anti-sway controller, ZV input-shaper, per-box cycle time → moves/hour |
| `stow_plan.py` | bay/row/tier slotting under **weight-on-top + port-rotation + reefer + IMDG hazmat segregation**; **no-rehandle** discharge sequencing |
| `isaac_sway_sim.py` | drives the clean-room `isaacsim.core.api` (kotodama.nv_compat) — **STS crane = Cartpole** (trolley + hanging load, anti-sway vs naive) **and boom/luffing-jib = DoublePendulum** (small-angle luff with passive-cable load damping; honest `reached=False` outside the underactuated envelope); emits kotoba EAVT datoms |
| `agv_transfer.py` | AGV horizontal transport: trapezoidal/triangular time-optimal travel profile, lane-segment time-window conflict detection, LPT makespan dispatch (backs `yard_transfer`) |
| `terminal_cycle.py` | **end-to-end discharge orchestration** — stow → no-rehandle sequence → per-box hoist+anti-sway crane leg (analytic, or routed through Isaac) → AGV yard legs dispatched in parallel → pipelined discharge time, moves/hour, worst residual sway, per-box ledger |

```sh
cd 20-actors/niyaku/methods
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q     # 24 tests
# Isaac tests need the kotoba submodule; point at it (or they skip gracefully):
NIYAKU_KOTODAMA_SRC=../../../40-engine/kotoba/crates/kotoba-kotodama/py/src \
  PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q
```

The Cartpole↔crane mapping is the *clean-room* Isaac surface (`kotodama.nv_compat`,
ADR-2605261800): no NVIDIA binary/header/library is linked. When the kotoba submodule is
absent the Isaac tests skip and the rest stay green.

## 9 Pregel cells (the handling pipeline)

`berth_allocation` (L0) → `stowage_planning` (L1) → `spreader_engagement` (L2) →
`sts_hoist_cycle` (L3) → **`trolley_traverse`** (L4, anti-sway) → `yard_transfer` (L5) →
`lashing_twistlock` (L6) → `manifest_attestation` (terminal), with `emissions_audit`
cross-cutting. R0 = scaffold; every cell is import-clean but `.solve()` raises
`RuntimeError` until Council Lv6+ ratifies R1 (ADR-2606082015, reserved). The
langgraph-free `state_machine.py` transitions are covered by `cells/test_state_machines.py`.

## Constitutional gates (R0)

G1 open-source control · G2 vendor-free clean-room sim (no NVIDIA Isaac binary) ·
G3 ≥2-robot witness per lift · G4 anti-sway safety envelope (settle before landing) ·
G5 Murakumo-only · G6 kotoba-EAVT-native · G7 tithe non-fiat · G8 zero-emission electric
cranes (regenerative lowering credited) · G9 IMDG segregation + weight-on-top + rotation ·
G10 no weapons/military-materiel cargo · G11 moves/hour KPI, never a worker-pace ranking ·
G12 no-server-key (methods move no real crane) · G13 consent-bound (compute-only) ·
G14 no worker biometric/pace surveillance.

## Status

R0 scaffold (2026-06-08): manifest + 9 cells + 5 runnable methods + lexicon + reference
terminal data. **55 tests green, 98 % branch coverage** of the methods (52 methods incl.
Cartpole STS + DoublePendulum boom-luff Isaac sims + end-to-end discharge orchestration,
3 cell-state-machine). R1 (live actuation) is Council-gated.
