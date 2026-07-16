# funadaiku 船大工 — Nagi 凪 voyage energy budget

> ADR-2606013400 · reduced-order analytic model (`methods/voyage_energy.py`) · :representative
> **No fossil engine** (G13/N5). Hydrogen must be green (G14, well-to-wake).

## Inputs

| Vessel (Nagi 凪 (coastal cargo)) | | Voyage (representative coastal short-sea leg) | |
|---|---|---|---|
| DWT | 3000 | Distance | 200 nm |
| Displacement | 4500 t | Service speed | 10 kn |
| Solar | 160 kWp | Voyage time | 20.0 h |
| Fuel cell | 2400 kW | Solar capacity factor | 13.0% |
| Battery | 2000 kWh | Wind-assist saving | 18.0% |
| Rotor sails | 2 | FC efficiency (LHV) | 52.0% |

## Result

- Propulsion shaft power @ 10 kn: **606 kW** (Admiralty law, calm water)
- Bus propulsion power (÷ QPC 0.62): **977 kW**
- Voyage energy demand (propulsion + hotel): **21939 kWh**

### Energy met by source

| Source | Energy (kWh) | Share | Role |
|---|---:|---:|---|
| Wind-assist (2× rotor sail) | 3517 | **16.0%** | primary fuel-saver |
| Solar deck | 416 | **1.9%** | hotel + top-up |
| Hydrogen fuel cell (residual) | 18006 | **82.1%** | electrical prime mover |
| **Fossil engine** | 0 | **0.0%** | none (G13) |

- **Green hydrogen demand for this leg: 1039 kg** (LHV 33.33 kWh/kg ÷ FC 52.0%)
- Battery covers a harbour manoeuvre (~60% prop + hotel) for **170 min** → zero-emission at berth/port.

## Honest reading

Wind + solar together meet **17.9%** of this representative
coastal leg; hydrogen carries the remaining **82.1%** as the prime mover —
exactly the survey conclusion that **no single source is a complete prime mover** at cargo
scale. Wind-assist share rises on windier/longer routes and falls on calm ones; solar is
capped to hotel + a little propulsion by construction (low areal power). Tank-to-wake CO₂ is
**zero**; the well-to-wake figure depends entirely on the **green-H₂ chain-of-custody** (G14) —
hydrogen made from fossil power would erase the benefit. This is a first-order model, not CFD.
