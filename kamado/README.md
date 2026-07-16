# kamado 竈

**Closed-loop carbon refining + fossil-refinery decommissioning/transition + refinery observation.**
Tier-B actor · R0 · ADR-2606051500 · DID `did:web:etzhayyim.com:actor:kamado`.

竈 = the sacred hearth/furnace. The transformation apparatus (distillation/cracking/reforming/
hydrotreating) is morally neutral; the multi-generational harm of petroleum lives in the carbon's
**origin** (out of geological storage) and **fate** (combusted back to atmosphere), not in the unit
operations. kamado reclaims the furnace for closed-loop carbon and refuses the fossil feedstock by
construction.

## Why a robotics-controlled fossil refinery is still a multi-generational harm

`methods/carbon_balance.py` is the whole argument as arithmetic (tCO₂e per tonne of product):

| pathway | origin | process | fate | **NET** | D3? |
|---|---:|---:|---:|---:|:---:|
| fossil diesel, fossil-powered (BASELINE — not buildable) | +0.00 | +0.40 | +3.10 | **+3.50** | ❌ |
| fossil diesel **+ full robotic APC** (still not buildable) | +0.00 | +0.28 | +3.10 | **+3.38** | ❌ |
| biogenic alkane diesel, hikari-powered, combusted | −3.10 | +0.03 | +3.10 | **+0.03** | ✅ |
| captured-CO₂ e-fuel, combusted | −3.10 | +0.03 | +3.10 | **+0.03** | ✅ |
| biogenic naphtha → durable polymer (carbon locked) | −3.10 | +0.03 | +0.00 | **−3.07** | ✅ |

Robotics/control only touches the **process** column (~11% of the harm), so full automation moves a
fossil refinery from +3.50 to +3.38 — still strongly net-positive. The carbon-negative and net-zero
rows are reached only by changing the **feedstock**. That is the entire design: G1 makes
`:fossil-virgin-crude` unrepresentable, and robotics is applied where it is genuinely harm-free —
(B) tearing down/converting existing fossil plants, and (C) operating the closed-loop synthesis ones.

## Three faces

- **A. observation** (`asset_observation` + `methods/analyze.py`) — kotoba-native successor to the
  legacy `oil-refining` Cypher actor. Refinery/unit/outage registry + transition-readiness. A
  resilience + transition map, **never** a target-list (G4).
- **B. decommission/transition** (`decommission_plan`) — §2(d) robotics to wind down / remediate /
  convert existing fossil refineries → hikari solar / synthesis plant / hodoki+kanayama recovery.
- **C. closed-loop synthesis** (`feedstock_guard` + `synthesis_control`) — refining on biogenic /
  captured-CO₂ / recycled carbon only; every design scored against D3 (net Δ ≤ 0).

## Run

```
cd methods
python3 carbon_balance.py          # the harm ledger
python3 analyze.py                 # observation + transition + D3 report → out/intel-report.md
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_kamado.py          # 11
cd ../cells && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_state_machines.py  # 8
```

## Honest R0

Design + data-model + carbon-sim + dry-run only. `:representative` seed (refinery names are public;
throughput/status illustrative). No live bulletin ingest, no real teardown, no live process
actuation — all Council Lv6+ + operator gated (G8), and live synthesis actuation additionally needs
a certified-safety review (G11). Carbon model is a transparent per-tonne ledger, not a full LCA.
