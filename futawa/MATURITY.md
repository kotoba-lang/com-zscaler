# futawa 二輪 — Maturity

**Stage: R0** (scaffold) — ADR-2605261330. Small-displacement motorcycle manufacturing
(≤250cc / ≤15kW / ≤200kg). ABS-mandatory, build-time anti-surveillance, right-to-repair
forward-publishing. Lifecycle surveillance/repair loop closed with hodoki (EOL).

| Dimension | State |
|---|---|
| Lexicons | ✅ 8 — `lex/*.edn` (first-tier, datomic-native) + central `com.etzhayyim.futawa.*` JSON mirror (engine/frame/electrical/paint/vehicleLot/partsCatalog/testRecord/silenMobilityReview) |
| Cells | 🟡 path-reserved (`40-engine/.../cells/`; `.solve()` import-time RuntimeError at R0) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) machine-readable |
| Tests | ✅ **green** — `70-tools/scripts/audit/test_futawa_invariants.py` (**25 passed**, charter invariants) + `py/test_agent.py` (**19 passed**, agent layer) |
| Methods | 🟡 agent only; offline build engine = R1 |

## Charter gates (manifest G1–G14 — key enforcement)

- **G7 ABS-mandatory** (≥125cc / ≥6kW electric) — safety-mandatory-at-build constitutionally
  elevated; no ABS-delete / track-only carve-out / cost-down strip.
- **G8 build-time anti-surveillance** — NO GPS / connected-app / telematics / V2X / proprietary
  diagnostic-DRM built in; only passive odometer + safety ECU (+ optional user-installed-after
  open-source GPS). §2(c); companion to hodoki G8 (EOL data destroy).
- **G11 KPI cap** — ≤250cc / ≤15kW / ≤200kg.
- **G12 right-to-repair forward-publishing** — every vehicle ships IPFS-pinned full parts
  catalog + CAD + firmware source + service manual + open diagnostic protocol; no lock-in;
  part discontinuation prohibited 30 years post-launch. §2(e); companion to hodoki G12.
- **G1/G2/G3/G6/G9/G13** — open CAD/firmware; mass-balance ≥98%; ≥2-robot witness; sound ≤80 dB;
  Murakumo-only inference; hodoki VIN pre-registration + ≥10% recycled.

## Substrate-native status

Lexicons exist as first-tier `lex/*.edn` (datomic-native) — ready for a cljc charter-gate
suite on the kawaraban pattern (reads `lex/*.edn` via `clojure.edn`). The current charter
test (`test_futawa_invariants.py`) is Python in `70-tools/scripts/audit/`; a cljc port +
prune is the pending substrate-native step (ADR-2606160842), tracked alongside the other
70-tools audit suites.

## R0 → R1 gate

ADR-2605261330 Council Lv6+ ratify + motorcycle-engineering SME + benchtop assembly PoC;
cell `.solve()` stays R0-gated until then.
