# itonami 営み — factory-operations agent (ADR-2606082300)

The charter-clean inversion of an **"AI Factory Brain" / NVIDIA Factory Operations
Blueprint (FOX)**. FOX wires AI agents into a running plant to optimize energy / quality /
throughput for the operator. itonami does the same *observation + optimization* maths —
**OEE, energy/good, idle-energy, scrap-rate** — but inverts the telos and the boundary:

| FOX (operator-optimizing) | itonami (charter-clean) |
|---|---|
| AI agents can write back to the OT bus / actuate | **observe → recommend only**; never actuates (no-server-key, liveActuation:false) |
| factory floor monitoring → worker productivity | **station/line scale only**; per-worker monitoring **structurally unrepresentable** (G2) |
| efficiency → throughput / line-speed-up | efficiency → **improvement + Wellbecoming**, never labor intensification |
| proprietary platform, telemetry | open-source, kotoba Datom-native, Murakumo-only |

## What it is

Reads a kotoba-EDN operations log (`:station/*` cells + `:tick/*` scan-cycle observations —
the **kotoba-os scan-cycle = Datom transaction** analog, ADR-2606031600) and computes,
aggregate-first:

- **OEE = Availability × Performance × Quality** per station + line rollup (gated by the
  weakest station — a serial line *is* its bottleneck)
- **energy/good-unit (kWh)** + **idle-energy fraction** — the FOX "cut 10% energy" lever
- **scrap-rate** — routed to vision inspection (**manako**, ADR-2606034800) + root-cause

Findings are *routed* to a human / Council, never written back.

## Where it sits

The **"run the factory"** observer paired with the **"build the factory"** sims:
- builds: giemon-factory (ADR-2606010030), sarutahiko truck line (ADR-2606013100),
  tatekata (ADR-2605250715) — physics on kami-genesis
- runs: kotoba-os scan-cycle Datoms (ADR-2606031600) → **itonami** OEE/energy/quality
- quality → manako vision · energy → hikari · ledger → toritate

The seed (`data/seed-factory-ops.kotoba.edn`) is the sarutahiko 8-cell line, tying the
observer to the existing build sim.

## Gates (constitutional — read `manifest.jsonld` for full text)

- **G1** observe→recommend, NEVER actuate. No write-back to the OT bus.
- **G2** station/line scale ONLY — no `:worker/*`/`:person/*`; anti-labor-surveillance.
- **G3** non-adjudicating — states/counts are disclosed facts; KPIs are read-time aggregates
  flagged `:bond/is-transient`, never durable verdicts.
- **G4** civilian producing actors only (Charter §1.12).
- **G5** sourcing honesty — R0 seed is `:representative` synthetic, never live OT.
- **G6** outward-gated — live OT/SCADA ingest (Modbus/OPC-UA/EtherCAT via kotoba-os device
  worlds) requires Council + operator DID; R0 = analyzer + schema + seed.
- **G7** Murakumo-only narration (ADR-2605215000).

## Run

```bash
python3 tests/test_analyze.py          # 10 tests — KPI engine, pure stdlib
python3 tests/test_optimize.py         # 6 tests — R1 proposal engine
python3 tests/test_inspect.py          # 7 tests — R2 vision hand-off
python3 tests/test_ingest.py           # 10 tests — R3 SCADA/OT scan-cycle ingest
python3 tests/test_digest.py           # 11 tests — R4 daily digest + R6 throughput fold
python3 tests/test_plan.py             # 8 tests — R5 throughput / line-balance plan
python3 tests/test_trend.py            # 8 tests — R7 KPI trend / drift detector
python3 tests/test_alert.py            # 9 tests — R9 operational threshold alerts
python3 tests/test_fleet.py            # 7 tests — R10 multi-line fleet rollup
python3 methods/analyze.py             # → out/operations-report.md
python3 methods/datom_emit.py --tx 1   # → out/itonami-datoms.kotoba.edn
python3 methods/optimize.py            # → out/optimization-proposals.md (+ proposal datoms)
python3 methods/inspect.py             # → out/vision-inspection.md (+ inspect datoms)
python3 methods/ingest.py              # kotoba-os scan stream → out/ingested-ticks.kotoba.edn
python3 methods/digest.py              # → out/daily-digest.md (Murakumo-narrated, G7)
python3 methods/plan.py                # → out/throughput-plan.md (units/day + relief)
python3 methods/trend.py               # → out/trend-report.md (KPI drift over days)
python3 methods/alert.py               # → out/alerts.md (graded threshold alerts, advisory)
python3 methods/fleet.py               # → out/fleet-rollup.md (multi-line plant rollup + rank)
```

End-to-end (gap-(3) loop): `ingest.py` (scan-cycle Datoms → ticks) → `analyze.py` (KPIs) →
`optimize.py` / `inspect.py` (proposals + vision) — OT field data to operations intelligence
on the canonical Datom log.

## Status / roadmap

- **R0** — analyzer + datom-emit + ontology + seed + 10 tests. ✅
- **R1 (landed)** — `methods/optimize.py`: idle-power-down energy-reduction proposal
  (recoverable kWh × conservative fraction → line %) + bottleneck-relief proposal (lift worst
  station to 2nd-worst → line OEE uplift). Proposals-only (G1), efficiency-not-intensification
  within takt (G2), honest energy % (not inflated to FOX's headline 10%). 6 tests. ✅
- **R2 (landed)** — `methods/inspect.py`: vision-inspection hand-off. From the quality_target,
  build a manako 眼 (ADR-2606034800) inspection request (watch-classes, sample-rate, on-device/
  no-biometric constraints) + reconcile manako detections → defect-class Pareto (root-cause
  hint) + scan-cycle scrap cross-check. Object-only — the inspected entity is a line PART,
  never a person (G2); inspection INFORMS, never auto-rejects (G1). 7 tests. ✅
- **R3 (landed)** — `methods/ingest.py`: SCADA/OT scan-cycle ingest. Parse a recorded
  kotoba-os `plc-host-runner` Datom stream → EAVT fold → interval ticks (Wh→kWh, most-severe
  state never hides a stop) that the rest of the pipeline consumes. OFFLINE replay only — the
  live OT socket is gated by construction (G6); no PLC write-back (G1); a stream carrying a
  person/worker attr is rejected (G2). 10 tests. ✅
- **R4 (landed)** — `methods/digest.py`: daily operations digest. Fuses analyze + optimize +
  inspect into one operator-facing summary + a Murakumo-narrated headline (G7 — narration
  routes only through the Murakumo LiteLLM loopback; deterministic offline fallback when
  unreachable, never an external LLM). Recommends not actuates (G1), station-scale (G2),
  transient datoms (G3). 8 tests. ✅
- **R5 (landed)** — `methods/plan.py`: throughput / line-balance plan. Per-station takt-capacity
  (uptime÷takt) → line throughput bottleneck (paint), which is DISTINCT from the OEE bottleneck
  (frame-weld) — the useful two-lens insight → units/day (documented operating-hours) +
  availability-recovery relief (+50% on the seed). Relief is within takt (G2), plan-not-actuate
  (G1), documented hours (G5). 8 tests. ✅
- **R6 (landed)** — `digest.py` folds in the R5 throughput two-lens insight: the daily brief +
  Murakumo narration now surface BOTH the OEE bottleneck (frame-weld) and the throughput
  bottleneck (paint, ~units/day), so the line lead relieves the right station for the right
  goal. +3 digest tests (52 total). ✅
- **R7 (landed)** — `methods/trend.py`: KPI trend / drift detector. Reads durable daily
  `:opsday/*` snapshots (as-of series; Wellbecoming = trajectory not snapshot) and surfaces drift
  via least-squares slope + polarity-aware direction {improving/flat/degrading} — catches a slow
  OEE decline / scrap creep before any single day alarms. Line/station scope only, no worker
  trajectory (G2); recommend-not-actuate (G1); directions transient (G3). 8 tests. ✅
- **R8 (landed)** — `digest.py` folds in the R7 drift detector: when a daily-snapshot history
  is supplied, the brief + narration surface multi-day drift (e.g. "N series degrading, worst is
  cab-weld scrap +120%"), so a slow regression is flagged before any single day alarms.
  Backward-compatible (no history → no drift section). +2 digest tests (62 total). ✅
- **R9 (landed)** — `methods/alert.py`: operational threshold alerts (the HMI alarm half).
  Compares each line/station KPI to documented (warn, critical) thresholds, polarity-aware →
  graded advisory alerts (seed: 1 critical cab-weld scrap, 5 warn). **ADVISORY ONLY** — raises a
  flag for a human/Council, NEVER halts the line / trips an e-stop / writes to the OT bus (no
  actuation token is even representable in the output, test-enforced). Thresholds caller-
  overridable (G5). 9 tests. ✅
- **R10 (landed)** — `methods/fleet.py`: multi-line FLEET rollup (whole-plant scope — a plant
  brain oversees >1 line). Groups stations by `:station/line`, runs per-line analyze + alert,
  rolls up a plant view, and ranks lines worst-first (critical alerts, then lowest OEE). On the
  fleet seed: sarutahiko (OEE 37.5%, criticals) ranked above giemon (83.3%, clean). Recommends
  which line to attend, never actuates (G1); ranks LINES not people (G2). 7 tests. ✅
- **R11** — live scan-cycle socket (Modbus/OPC-UA/EtherCAT via kotoba-os device worlds, Council
  + operator DID gated); cross-check OEE against the sarutahiko produce sim; componentize-py
  WASM build + CID advertisement.
