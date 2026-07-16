# busshi (物資) — world commodity & raw-materials observatory

**DID**: `did:web:etzhayyim.com:busshi` · **Namespace**: `com.etzhayyim.busshi.*`
**ADR**: ADR-2606161730 (clj-native R0) · **Risk axis**: ADR-2606161700 (§2(l) v3.2)
**Status**: R0 — clj-native, kotoba-Datom-native, tests green

## Overview

A KG-mirror **observatory** over the world's commodities & raw materials — gold (金),
silver (銀), rare metals (レアメタル), energy (石油/ガス/石炭), agricultural softs — keyed
on the material. It mirrors supply concentration and **multi-generational (子・孫) ×
wellbecoming risk** into the kotoba Datom log, routed to resilience.

**OBSERVATION ONLY**: 取引しない (never trades) · 採掘しない (never extracts) · never
forecasts. A **resilience map, NEVER a target-list** and never a market signal.

## Wave 1 (all-domains-thin) — 5 classes, 25 commodities (`:representative`)

| class | commodities |
|---|---|
| precious-metal | gold, silver, platinum, palladium |
| base-metal | copper, aluminium, zinc, nickel, lead, tin |
| rare-metal | lithium, cobalt, rare-earths (agg.→ rare-earth-coverage), gallium, germanium, tungsten, antimony |
| energy | crude oil, natural gas, coal, uranium |
| ag-soft | wheat, corn, soybean, coffee, sugar |

## Analytical core

top-producer-share + named-HHI → chokepoint-risk; multigen-risk =
0.40·monopoly + 0.30·carbon + 0.30·irreversibility; route ∈ {resilience,
de-monopolization, restoration}. Plus per-class aggregates + coverage gap worklist.

## Run

```bash
bb --classpath 20-actors 20-actors/busshi/methods/analyze.cljc        # print resilience map
./20-actors/busshi/run_tests.sh                                       # both clj test suites
```

## Constitutional

Gates G1–G8 + non-goals N1–N5 in `manifest.edn`; full rationale in `CLAUDE.md` and
ADR-2606161730. Apache 2.0 + Charter Compliance Rider v3.2.
