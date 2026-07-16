# niyaku 荷役 — CLAUDE guidance

Automated port cargo handling (ship↔shore container loading/unloading). Tier-B actor,
ADR-2606082000, R0 scaffold. Operator-side counterpart of **funadaiku** (builds the ships).

## What this actor is

The port-side automation the roster lacked: funadaiku *builds* the cargo ship, niyaku
*loads and unloads* it. The technical core is **anti-sway control** of a suspended
container, modelled as a cart + hanging load — the Cartpole topology — and verified
through the clean-room `isaacsim.core.api` (`kotodama.nv_compat`, ADR-2605261800).

## Layout

- `methods/` — three pure-stdlib, pywasm-ready modules (the only code that *runs* at R0):
  `crane_dynamics.py`, `stow_plan.py`, `isaac_sway_sim.py`. Tests live beside them.
- `cells/` — 9 LangGraph Pregel cells (R0 scaffold; `.solve()` raises). The
  langgraph-free `state_machine.py` per cell carries the covered transition logic.
- `data/terminal.edn` — reference terminal + STS crane + sample vessel cell (illustrative).
- `lex/moveAttestation.edn` — per-move container-handling attestation lexicon.

## Hard rules (per ADR-2606082000 gates)

- **G2 clean-room only.** The Isaac integration mirrors the *public* `isaacsim.core.api`
  call shapes via `kotodama.nv_compat`. NEVER link/import any NVIDIA Isaac Sim binary,
  header, or library. The dynamics are KAMI-native.
- **G8 zero-emission.** Electric cranes/AGVs only; no diesel RTG. Regenerative
  hoist-lowering energy is credited in `emissions_audit`.
- **G9 stow feasibility.** `stow_plan` must enforce weight-on-top, port-rotation
  (no re-handle), reefer-row, and IMDG hazmat segregation. Do not relax these to "make a
  plan fit" — an infeasible request must raise `StowError`.
- **G10 no weapons cargo.** Weapons-transport / military-materiel handling is N-excluded
  (Charter Rider §2(a)). Cargo provenance is gated.
- **G11 / G14 no worker surveillance.** Productivity is `moves/hour` (equipment KPI), never
  a per-longshoreman pace ranking. No worker biometric/pace tracking.
- **G12 no-server-key / consent-bound.** Methods are pure compute and move no real crane;
  R0 stops at "intent". Real actuation is Council-gated R1 (ADR-2606082015, reserved).

## Running tests

The harness langchain/pydantic stack is broken, so disable pytest plugin autoload and keep
the cell tests langgraph-free (load `state_machine.py` directly, never `cell.py`):

```sh
cd methods && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q
# Isaac tests: set NIYAKU_KOTODAMA_SRC to the kotoba py/src, else they skip.
```

## Anti-sway sign conventions (don't flip these without re-deriving)

- **`crane_dynamics.GantryCrane`**: equilibrium θ=0 (load hangs down); trolley accel
  couples as `-a/L` into θ̈. Sway feedback is **positive** (`+k_theta·θ`) — it stiffens the
  restoring term.
- **`isaac_sway_sim` (Cartpole)**: equilibrium θ=π (hanging); a +force drives φ=θ−π
  **positive**. Sway feedback is **negative** (`-k_phi·φ`). The two models have opposite
  trolley-accel→sway coupling sign, hence opposite feedback sign. Both are verified by test.
