# kasa 嵩 — Maturity Scorecard

Honest R0 status. **This is a proof-of-model, not coverage.** Coverage is gated by `:sourcing`
(G5): only `:authoritative` rows (parsed from a primary dataset row) count as real coverage. The
seed ships **zero** `:authoritative` rows — every observation is `:representative` (a public
*headline* figure, rounded) or `:estimated` (a nowcast with a documented method). The point of R0
is to prove the vocabulary + the four-domain growth model end-to-end, not to have ingested the
world's compute stats. Per ADR-2606072000.

## Seed contents (R0, 2026-06-07)

`data/seed-compute-capacity.kotoba.edn`:

| Vocabulary | Count | Sourcing |
|---|---|---|
| `:compute.source/*` | **8** — WSTS · SIA · TrendForce · IDC · JPR · TOP500 · Epoch AI (CC-BY) · Our World in Data (CC-BY) | `:representative` |
| `:compute.series/*` | **11** across 8 domains | `:representative` (9) / `:estimated` (2) |
| `:compute.obs/*` | **52** observations, 2020–2024 | `:representative` (42) / `:estimated` (10) |

## Coverage breadth (what the proof-of-model demonstrates)

| Axis | Coverage in seed |
|---|---|
| Domains | **8** — semiconductor · DRAM · NAND · storage · GPU · CPU · FLOPS · datacenter |
| Founder-named metrics | **all four** — storage size (HDD+SSD EB) · memory size (DRAM+NAND $) · GPU/CPU (units + DC-accel $) · compute (TOP500 + training FLOPS) |
| Units spanned | **6** — USD · exabytes · units · FLOP · watts · (ratio, in growth) — one EAVT vocabulary |
| Years (as-of) | 2020–2024 → YoY (consecutive) + full-span CAGR computed |
| Sourcing tiers | **3** exercised — `:representative` (headline) · `:estimated` (nowcast+method) · `:synthesized` (derived growth/agg) |
| Aggregation safety | memory (`:dram`/`:nand`) NEVER folded into `:semiconductor`; TOP500 `:petaflops` never summed with raw `:flops` |

## Tests

`./run_tests.sh` — **23/23 passed** (stdlib, runnable directly or under pytest):
- `tests/test_kasa.py` (15) — EDN scientific-notation parse · seed load · G1 admissibility ·
  YoY/CAGR arithmetic · non-consecutive-year skip · storage HDD⊕SSD aggregate · memory-not-
  double-counted · FLOPS scale-separation · derived=`:synthesized` · ingest G1 refusal · merge
  precedence · report render.
- `tests/test_invariants.py` (8) — non-adjudicating + no-forecast report language · no future-
  dated obs · `:estimated` carries method · all seed sources admissible · derived `:synthesized` ·
  aggregate uniqueness + no double-count.

## Cells

| Cell | Status |
|---|---|
| `sources` | **runnable** — 9 admissible + 10 prohibited publishers; `admissible()` unit-tested |
| `ingest` (rows-JSON bridge + G1 gate) | **runnable offline**; live `--fetch-epoch` is **G7** Council+operator gated |
| `analyze` (YoY + CAGR + aggregates) | **runnable** — emits `out/intel-report.md` + `out/compute-growth.kotoba.edn`; unit-tested |

## What is NOT done (by design at R0)

| Question | Status |
|---|---|
| World's compute-capacity stats fully ingested? | **NO** — 8 sources / 11 series (proof-of-model). |
| Any `:authoritative` row in the seed? | **NO** — every obs is `:representative` (headline, rounded) or `:estimated` (nowcast). Authoritative requires the exact dataset row via `ingest.py`, which is **G7** gated. |
| Full open-dataset parse (Epoch AI CSV, WSTS series, IDC tables)? | **NO** — the rows-JSON bridge runs offline on supplied data; the live CC-BY fetch path exists but parsing into rows is **R1**. |
| Regional / country breakdown? | **NO** — `:world` only at R0; the `:geography` axis is modeled but not populated. |
| Live atproto / kotoba-server publish? | **NO** — lexicons path-reserved (`com.etzhayyim.kasa.*`); serving is R1+. |
| Revision (supersede) chains populated? | **NO** — `:compute.obs/superseded-by` modeled + documented; no restatement in the seed yet. |

## Path to R1

1. Implement the Epoch AI CC-BY CSV parser (beyond the raw-fetch + rows-JSON bridge) → first
   `:authoritative` FLOPS-training rows.
2. Bridge the WSTS/SIA + TrendForce + TOP500 public series into `:authoritative` rows, replacing
   the `:representative` headline figures, all under the **G7** operator gate.
3. Populate the `:geography` axis (regional semiconductor sales, country DC power) — geography
   aggregates, still coverage-honest.
4. Wire `com.etzhayyim.kasa.*` publish to the kotoba-server; hand `:compute.obs` series to
   `mitooshi` 見通し as forecastable inputs (kasa supplies actuals; mitooshi forecasts).
