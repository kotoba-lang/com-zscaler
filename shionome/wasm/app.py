"""shionome 潮目 actor — cross-asset capital-flow observation, as a WASI component.

Per ADR-2606072200 + ADR-2606014600. This is the *executable* face of the shionome actor,
built with componentize-py into a WASM Component-Model component that runs in the kotoba WASM
runtime (jco/node, donated mesh, or a full IPFS-node host). It mirrors the core of
`methods/weave.py`: per capital BUCKET the net observed flow (in − out), the top rotation pair
(どこからどこへ), and a FACTUAL cross-asset regime descriptor (risk-on/off/mixed).

THE DEFINING INVARIANT — トレードはしない (G2): the result is an OBSERVATION. It carries
`no_trade: true` and contains NO buy/sell signal, price target, over/under-weight call, or
portfolio instruction. Only capital-movement flow kinds (rotation / fund-inflow / fund-outflow /
fx-flow) contribute to the money math; co-movement/price observations are excluded (unit-clean).

Real deployments read the full graph from the kotoba Datom log; this self-contained PoC embeds a
bounded `:representative` seed so the component is verifiable offline. Reproduces the documented
finding that capital is rotating risk-on (out of Treasuries+cash into US equities / AI / crypto).

componentize-py wires `WitWorld.compute()` to the WIT world export.
"""

import json

# capital-movement kinds only (rotation / fund-inflow / fund-outflow / fx-flow); a co-movement /
# price observation is in other units, not a capital amount, so it never enters the money math.
CAPITAL_MOVEMENT = ("rotation", "fund-inflow", "fund-outflow", "fx-flow")

# bucket -> (label, asset_class, risk-tag) ; risk tag drives the FACTUAL regime descriptor.
BUCKETS = {
    "us-equities": ("US equities", "equities", "risk"),
    "jp-equities": ("Japan equities", "equities", "risk"),
    "em-equities": ("EM equities", "equities", "risk"),
    "us-govt-bonds": ("US Treasuries", "govt-bonds", "safe"),
    "gold": ("Gold", "commodities", "safe"),
    "crypto-btc": ("Bitcoin / crypto", "crypto", "risk"),
    "cash-usd": ("USD cash", "cash", "safe"),
    "theme-ai": ("AI theme", "equities", "risk"),
}

# (id, source, target, kind, magnitude_usd_bn) — :representative observed flows.
FLOWS = [
    ("f1", "us-govt-bonds", "us-equities", "rotation", 12.4),
    ("f2", "cash-usd", "us-equities", "rotation", 8.1),
    ("f3", "external", "crypto-btc", "fund-inflow", 5.6),
    ("f4", "us-equities", "theme-ai", "rotation", 6.9),
    ("f5", "us-govt-bonds", "external", "fund-outflow", 9.3),
    ("f6", "us-equities", "gold", "rotation", 3.2),
    ("f7", "external", "jp-equities", "fund-inflow", 4.4),
    ("f8", "em-equities", "us-equities", "rotation", 2.7),
]


def _analyze():
    into, outof = {}, {}
    pairs = {}
    for _id, src, tgt, kind, mag in FLOWS:
        if kind not in CAPITAL_MOVEMENT:
            continue
        if tgt and tgt != "external":
            into[tgt] = into.get(tgt, 0.0) + mag
        if src and src != "external":
            outof[src] = outof.get(src, 0.0) + mag
        if src != "external" and tgt != "external" and src != tgt:
            pairs[(src, tgt)] = pairs.get((src, tgt), 0.0) + mag

    net = []
    for b in set(into) | set(outof):
        n = round(into.get(b, 0.0) - outof.get(b, 0.0), 2)
        net.append({"bucket": b, "label": BUCKETS.get(b, (b,))[0], "net": n})
    net.sort(key=lambda r: (-r["net"], r["bucket"]))

    top_pair = max(pairs.items(), key=lambda kv: kv[1]) if pairs else None
    rotation = None
    if top_pair:
        (s, t), m = top_pair
        rotation = {"from": BUCKETS.get(s, (s,))[0], "to": BUCKETS.get(t, (t,))[0], "magnitude": round(m, 2)}

    risk_net = sum(r["net"] for r in net if BUCKETS.get(r["bucket"], ("", "", ""))[2] == "risk")
    safe_net = sum(r["net"] for r in net if BUCKETS.get(r["bucket"], ("", "", ""))[2] == "safe")
    if risk_net == 0 and safe_net == 0:
        regime = "indeterminate"
    elif risk_net > 0 and safe_net <= 0:
        regime = "risk-on"
    elif risk_net < 0 and safe_net >= 0:
        regime = "risk-off"
    else:
        regime = "mixed"

    return {
        "actor": "shionome",
        "metric": "cross-asset-net-capital-flow",
        "sourcing": "representative",
        "no_trade": True,        # G2 — トレードはしない: an observation, never a trade instruction
        "is_mirror": True,       # G5
        "buckets": len(BUCKETS),
        "flows": len(FLOWS),
        "regime": regime,
        "risk_net": round(risk_net, 2),
        "safe_net": round(safe_net, 2),
        "top_inflow": net[0] if net else None,
        "top_outflow": net[-1] if net else None,
        "top_rotation": rotation,
        "net_flow": net,
    }


def compute() -> str:
    """Plain entry point (used by the offline python sanity check)."""
    return json.dumps(_analyze(), ensure_ascii=False)


# componentize-py binds the WIT world's exports to a class named `WitWorld`.
class WitWorld:
    def compute(self) -> str:
        return json.dumps(_analyze(), ensure_ascii=False)
