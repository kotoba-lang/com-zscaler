# 樋 toi — Compute as Thermal Routing

> 樋 (toi) = a *conduit / flume* that guides flow. Deferrable compute is a movable
> load — an energy-flow valve. The compute leg of the **Energy Order Protocol**:
> route useful compute (not hashes) to where the energy and heat are favourable,
> and feed its waste heat to 燠 okibi.

toi scores each compute **site** and routes each movable **job** to the best one:

```
site-score = 0.30·carbon-factor + 0.25·surplus-renewable + 0.20·cooling-factor
           + 0.15·heat-sink-bonus + 0.10·transparency
```

- **carbon-factor** rewards a clean grid (vs a 450 gCO2/kWh warm-grid baseline)
- **cooling-factor** rewards a low PUE (cold-region machines waste less on cooling)
- **heat-sink-bonus** rewards a site whose waste heat feeds an okibi heat demand
- **transparency** scores Murakumo / donated-mesh above commercial GPU — **G2,
  Rider §2(i) / ADR-2606172359**: a *net score*, not a vendor ban. A clean Murakumo
  site outscores commercial GPU, which is only a fallback (unused while clean capacity
  exists).

A non-movable job (latency-bound, pinned) is **never coerced** — it stays in-place.
Routed savings (avoided carbon, reusable heat) become 澪 mio flow-improvement claims (R1).

## Gates

- **G1 map-not-job-kill** — a routing map, never a forced job-kill / load-shedding
  weapon. No `:toi/dispatch` / `:toi.job/kill-order`. Murakumo fleet + operator actuate.
- **G2 murakumo-default-preferred** — transparency-scored, not a vendor ban.
- **G3 waste-heat-to-okibi** — compute-as-heater only where a heat demand exists.
- **G5 verification-via-mio** — avoided carbon is verified by 澪 mio before it earns.
- **G7 no-server-key** — scheduler/carbon ingest is an operator step.

## Run

```bash
./20-actors/toi/run_tests.sh                                   # 21 tests / 98 assertions
bb --classpath 20-actors 20-actors/toi/methods/analyze.cljc    # render the compute routing map
bb --classpath 20-actors 20-actors/toi/methods/autorun.cljc    # one heartbeat → append (idempotent-by-content)
```

OBSERVATION ONLY. A routing map, **never a forced job-kill**.
ADR-2606211200 · Energy Order Protocol (compute leg).
