# narashi 均 — global-inequality observation

**Tier-B actor · R0 design-only · ADR-2607101800 · `did:web:narashi.etzhayyim.com`.**

narashi reads the economic-inequality indicators the World Bank, the UN Statistics Division, and Our
World in Data have **already published** — Gini coefficient, poverty headcount ratio, income share by
decile, SDG 10 shared-prosperity-premium — and registers each observation as a content-addressed
kotoba Datom, normalized to one canonical concept set across sources.

It is the **inequality-outcome sibling of `kanae` 鼎** (which assembles government *fiscal-flow*
data): kanae tracks money moving between jurisdictions, narashi tracks the *distributional outcome*
those flows sit alongside. narashi reads kanae's aid/loan flow datoms **read-only**, to juxtapose —
never to explain.

## Why this is not a redistribution actor

etzhayyim already has a labor-liberation mechanism — the **Liberation Ladder**
(`90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` in `etzhayyim/root`) — but it
delivers in-kind benefits to etzhayyim's own **Adherent SBT holders only**, and its own Non-Goals
immutably reject fiat-replacement UBI and state-welfare replacement. narashi does not extend, mirror,
or substitute for that mechanism. It **observes**; it does not move funds, does not recommend policy,
and does not rank jurisdictions. See ADR-2607101800 §"Context" for the full reasoning.

## Sources — primary published indicators only

| Source | Series | Tier |
|---|---|---|
| **World Bank WDI** | `SI.POV.GINI`, `SI.POV.DDAY`, `SI.POV.NAHC` | A |
| **UN SDG API** (unstats.un.org) | SDG 10.1.1, 10.4.1 | A |
| **Our World in Data** | World Inequality Database mirror (income share by decile) | A |

Ingested from the already-pinned DataLad raw datasets `org.worldbank.api`, `org.un.unstats`,
`org.ourworldindata` — the same Tier-A source family `global-energy-datoms` already uses for SDG 7.

## What it is not

Non-adjudicating (G4): no country rankings, no "doing better/worse than" framing, no policy advice.
Non-causal (G8): a `crossReferenceNote` juxtaposing an aid flow against an inequality trend never
claims the flow caused the trend — `causalClaim` is a schema-level `const false`. Not a redistribution
mechanism (N1) — that role belongs solely to the Liberation Ladder, for adherents only.

## Layout

```
manifest.jsonld                  actor blueprint + DID
CLAUDE.md                        agent reference (hard rules, vocabulary, cells)
methods/test_charter_gates.cljc  constitutional-gate conformance test
run_tests.clj                    bb test runner
kotoba.app.edn                   KOTOBA Mesh deploy manifest
```

Lexicons (`com.etzhayyim.narashi.*`) live in `etzhayyim/root`:
`00-contracts/lexicons/com/etzhayyim/narashi/`.

## Run

```bash
bb run_tests.clj
```

**R0 design-only.** All 3 cells (`metric_ingest`, `cross_reference`, `narrative`) raise on first
invocation until Council ratification (ADR-2607101800 §7).
