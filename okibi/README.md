# 燠 okibi — Thermal Matching Market

> Heat is **local**. 燠 (okibi) = *embers / residual heat* — the warmth still worth
> capturing. The waste-heat leg of the **Energy Order Protocol**: find where N°C of
> waste heat at M kW sits near a demand that needs it, and route it there.

okibi matches waste-heat **sources** to heat-demand **sinks** under two hard physical
gates, then submits realized matches to the 澪 mio verification backbone (R1).

```
match requires:  source.temp ≥ sink.required_temp + 5°C   (temperature cascade)
            and:  distance(source, sink) ≤ 5 km            (heat is local)
quality = (1 − distance/5km) × min(availability)
greedy allocation by quality → matched kW + unmatched surplus + unmatched demand
```

The temperature cascade is why one hot geothermal source can serve both a 60°C
hot-water demand and a 70°C drying demand, while a 65°C datacenter cannot serve a
90°C absorption chiller. A cooling **load** is not a heat sink — the classic
"waste heat sent where cooling is needed" anti-pattern is structurally unrepresentable.

## Gates

- **G1 map-not-dispatch** — okibi maps matches; never issues a dispatch order. No
  `:okibi/dispatch`. hikari actuates under Council gate.
- **G2 physical-feasibility** — a match must pass cascade + distance; infeasible pairs
  can never match (no fabrication). Cooling-load is not a sink.
- **G3 verification-via-mio** — delivered kWh is verified by 澪 mio (signed BTU meter).
- **G5 net-not-positive-carbon** — a match must not induce additional fossil heat.
- **G7 no-server-key** — meter ingest is an operator step; the loop does no network I/O.

## Run

```bash
./20-actors/okibi/run_tests.sh                                   # 21 tests / 92 assertions
bb --classpath 20-actors 20-actors/okibi/methods/analyze.cljc    # render the thermal matching map
bb --classpath 20-actors 20-actors/okibi/methods/autorun.cljc    # one heartbeat → append (idempotent-by-content)
```

OBSERVATION ONLY. A matching map, **never a dispatch order**; unmet demand is a gap,
never a target-list. ADR-2606211200 · Energy Order Protocol (waste-heat leg).
