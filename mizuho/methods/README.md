# mizuho/methods — runnable water control loops (R0 `:representative`)

Deterministic, stdlib-only control loops behind the `water_supply` cell. They
compose the shared infra-robotics substrate in
`20-actors/kuni-umi/robotics/` (re-exported via `_substrate.py`); no network, no
hardware, no live actuation. `cell.py .solve()` stays Council-gated — these
modules are offline sim + dry-run only.

## `water_supply.py` — potable-supply level/pressure loop

- `ReservoirPlant` — a self-regulating service reservoir (a substrate `Plant`):
  stored volume integrates `inflow(pump command) − demand − gravity-leak(head)`;
  the process variable is level (m), and service pressure ∝ static head.
  `set_demand(lps)` applies the demand step the pump loop must reject.
- `commission_water_supply(demand_step_lps, use="supply", service_population, …)`
  — runs `assert_civilian` (N1 allowlist `supply/treat/sample/recycle/irrigate`)
  and the **G3 community-scale cap** (`SafetyError` if `service_population >
  2500`) *before* any run, then drives a secondary-PI pump via `simulate()` so
  the level returns to setpoint after a demand step. Returns a frozen
  `WaterSupplyResult` (level restored, final level/pressure, settling seconds,
  service population).
- `to_datoms(result, source_id)` — aggregate-only kotoba EAVT dict;
  `:water.supply/dry-run = True`, `:water.supply/server-held-key = False`.

## `chlorination.py` — residual-disinfection dosing loop

- `ResidualChlorinePlant` — first-order free-chlorine residual (`dC/dt = dose −
  k_decay·C`). PI dosing holds a target residual (default 0.5 mg/L).
- `ClampedDoser` — wraps a substrate `PID` with a **hard structural clamp** so
  the modeled residual can never cross the regulatory ceiling
  `MAX_RESIDUAL_MGL = 4.0` (WHO guideline / US-EPA MRDL) — clamp is independent
  of gains. The plant also caps at the ceiling (defence in depth).
- `commission_dosing(agent="disinfect", target_residual_mgl=0.5,
  per_member_consent=False, …)` — **G6 anti-paternalism**: `"disinfect"`
  (chlorine) runs community-wide with no consent; `"fluoridate"` **raises
  `SafetyError` unless `per_member_consent=True`** (no mandatory fluoridation).
  A target above the ceiling is refused.
- `to_datoms(result, source_id)` — aggregate-only; `:water.dosing/dry-run =
  True`, `:water.dosing/server-held-key = False`, ceiling reported.

## Run the tests

```bash
cd 20-actors/mizuho/methods
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q
```
