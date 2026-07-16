# 撓 tawami — Proof of Flexibility

> The value is not generation but the **ability to bend a future energy flow in time.**
> 撓 (tawami) = *flexure* — the bending. The flexibility leg of the **Energy Order
> Protocol**, feeding the 澪 mio verification backbone.

What is worth most on a future grid is not how much you generate but **when you can move
your draw**. tawami maps that shiftability across resource classes and scores it:

```
flex-value = energy-capacity-kWh × availability × responsiveness × (0.5 + 0.5·shiftability)
  energy-capacity = shiftable-kW × duration(h)
  responsiveness  = 1.0 (≤1min) · 0.9 (≤10) · 0.7 (≤30) · 0.5 (≤60) · 0.3 (else)
  shiftability    = min(1, (advance-window + defer-window)/24h)
```

Each asset is tiered (`:fast-flex` / `:mid-flex` / `:slow-flex`) and assigned its
best-use **mio flow-class** (battery/heat-pump → peak-shave · EV/cold-store →
renewable-absorb · datacenter → compute-routing · industrial → flexibility). When a
flexibility is actually *used*, it becomes a 澪 mio flow-improvement claim (R1).

## Gates

- **G1 map-not-dispatch** — tawami maps flexibility; it never issues a dispatch/curtail
  order. No `:tawami/dispatch` attribute exists. hikari actuates under Council gate.
- **G2 aggregate-first-no-person** — no per-person load profile; `:tawami.person/*` is
  structurally absent (Rider §2(c) reciprocity).
- **G3 no-trade-no-signal** — flexibility is observed, never traded.
- **G5 reciprocal-reward** — provision earns non-monetary decaying moyai credit (cash≡0).
- **G7 no-server-key** — telemetry ingest + claim submission are operator/member-gated.

## Run

```bash
./20-actors/tawami/run_tests.sh                                   # 20 tests / 134 assertions
bb --classpath 20-actors 20-actors/tawami/methods/analyze.cljc    # render the flexibility map
bb --classpath 20-actors 20-actors/tawami/methods/autorun.cljc    # one heartbeat → append (idempotent-by-content)
```

OBSERVATION ONLY. A flexibility map, **never a dispatch order**.
ADR-2606211200 · Energy Order Protocol (flexibility leg).
