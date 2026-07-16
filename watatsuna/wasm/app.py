"""watatsuna 綿津綱 actor — chokepoint criticality, as a WASI component.

Per ADR-2606014600. This is the *executable* face of the watatsuna actor, built
with componentize-py into a WASM Component-Model component. It mirrors the core
of `methods/analyze.py`: per submarine-cable **chokepoint**, the aggregate Tbps
of the distinct cables that traverse it (a resilience map, NEVER a target-list —
G2; watatsumi N8). Real deployments read the full graph from the kotoba Datom
log; this self-contained PoC embeds a bounded `:representative` seed so the
component is verifiable offline. Reproduces the documented finding that the
Malacca Strait carries the top chokepoint load.

componentize-py wires this module's top-level `compute()` to the WIT world export.
"""

import json
from collections import defaultdict

# (cable_id, design_capacity_tbps)
CABLES = {
    "jupiter": 60.0, "faster": 60.0, "plcn": 144.0, "marea": 200.0,
    "dunant": 250.0, "grace_hopper": 220.0, "2africa": 180.0, "smw3": 4.6,
    "smw4": 65.0, "adc": 160.0, "sjc": 28.0, "apcn2": 2.56, "bifrost": 15.0,
    "equiano": 144.0,
}

# chokepoint → distinct cables that traverse it (the trans-oceanic backbone
# routes through Malacca dominate, per the watatsuna report).
CHOKEPOINTS = {
    "malacca": ["adc", "bifrost", "sjc", "apcn2", "smw3", "smw4",
                "faster", "jupiter", "dunant", "grace_hopper"],
    "luzon-strait": ["faster", "plcn", "jupiter", "adc", "sjc", "apcn2"],
    "suez-red-sea": ["smw3", "smw4", "2africa"],
    "gibraltar": ["2africa", "equiano"],
    "south-china-sea": ["adc", "apcn2", "plcn"],
}
# flattened (cable, [chokepoint]) segments — the on-disk shape of analyze.py
SEGMENTS = [
    (cable, [cp]) for cp, cables in CHOKEPOINTS.items() for cable in cables
]


def _analyze():
    # per chokepoint: distinct cables that traverse it (edge-primary, aggregate)
    choke_cables = defaultdict(set)
    for cable, points in SEGMENTS:
        for cp in points:
            choke_cables[cp].add(cable)
    choke_load = {
        cp: round(sum(CABLES.get(c, 0.0) for c in cs), 2)
        for cp, cs in choke_cables.items()
    }
    ranked = sorted(choke_load, key=lambda k: -choke_load[k])
    top = [
        {
            "rank": i + 1,
            "chokepoint": cp,
            "cables": len(choke_cables[cp]),
            "load_tbps": choke_load[cp],
        }
        for i, cp in enumerate(ranked[:5])
    ]
    return {
        "actor": "watatsuna",
        "metric": "chokepoint-dependent-capacity-tbps",
        "sourcing": "representative",
        "cables": len(CABLES),
        "chokepoints": len(choke_cables),
        "top": top,
    }


def compute() -> str:
    """Plain entry point (used by the offline python sanity check)."""
    return json.dumps(_analyze(), ensure_ascii=False)


# componentize-py binds the WIT world's exports to a class named `WitWorld`
# (the default world → `wit_world` package → `WitWorld` protocol).
class WitWorld:
    def compute(self) -> str:
        return json.dumps(_analyze(), ensure_ascii=False)
