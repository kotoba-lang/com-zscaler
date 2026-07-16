# funamori 舫 — Maturity

**Stage: R0** (scaffold + runnable method) — ADR-2605265600. 淡水化発電 / marine-renewable
salinity-gradient power (PRO/RED) with open-membrane R&D gates. Instantiates the previously
path-reserved `cells/salinity_gradient_pro_red/` (ADR §5 R0).

| Dimension | State |
|---|---|
| Methods | ✅ `salinity_gradient.cljc` (PRO/RED physics + Charter gates) + `stack_robotics.cljc` (install/maintenance robotics) + `plant.cljc` (tidal generation + grid-tie control) — the full kuni-umi 3-layer pattern (plant/control/kinematics), pure Clojure `.cljc` |
| Tests | ✅ `methods/test_{salinity_gradient,stack_robotics,plant}.cljc` + `cells/test_cells.cljc` — **53 tests / 224 assertions, green** (`./run_tests.sh`, babashka) |
| Datoms | ✅ `kotoba/schema.edn` (`:funamori.salinity.*` + `:funamori.robotics.*` + `:funamori.plant.*` EAVT) + `kotoba/seed.edn` (`:representative` design site/membrane/measure) |
| Lexicons | ✅ 3 under `com.etzhayyim.funamori.*` (salinityGradientMembraneAttestation / salinityGradientSiteAttestation / silenSalinityGradientReview) — ADR §6 |
| Manifest | ✅ `manifest.edn` — 12 gates |
| Cells | ✅ 4 kotoba-native EDN cell SPECS (sanae pattern) — `site_qualification` / `membrane_attestation` / `power_characterization` / `stack_service`; declarative Pregel state-graphs over the `.cljc` methods, validated by `cells/test_cells.cljc` (7 tests / 109 assertions). `.solve()` is R1 (Council + membrane-chemist + mizuho R2) |
| Hardware | ⛔ none (R1 = bench ≤1 kW PRO / ≤500 W RED, Council + membrane-chemist gated) |

## Physics validated by the test (vs ADR-2605265600 Table)

- **Osmotic pressure** — seawater 35 g/L NaCl @20°C → **29 bar** (van't Hoff; textbook 27–28 bar). ✅
- **PRO max power density** — `A·Δπ²/4` at `ΔP=Δπ/2`, A=1e-12 → **2.1 W/m²**, inside ADR **1–3 W/m²** band. ✅
- **RED power density** — closed-form `E_cell²/(8·area-resistance)`, default 4e-3 Ω·m² → **~1.2 W/m²**, inside ADR **0.5–2 W/m²** band; independent of stack size N. ✅
- **Reverse-osmosis region** — `ΔP ≥ Δπ` ⇒ `Jw≤0` ⇒ no power. ✅

## Charter gates pinned by the test (enforced in code, ADR-2605265600)

- **G1/G2/G3 membrane** — open-publication/in-house mandatory; Toray/Hydranautics/GE-Power/Statkraft
  prohibited (§2); PFAS/Nafion prohibited (§1.2 / Charter §2(c)). `assert-membrane-permitted` throws.
- **G4 salinity-floor** — Δsalinity ≥30 g/L; brackish ≤15 g/L → DEFER R4+. `assert-salinity-difference` throws.
- **G5 power-density-floor** — ≥1 W/m² R3 gate. `assert-r3-power-density` throws.
- **G6 site-cap** — ≤50 kW/site, ≤1 site through R3. `assert-site-cap` / `assert-site-count` throw.
- **Integration** — `evaluate-site` runs ALL gates and returns `{:permitted false :violation <gate>}`
  for brackish / commercial-membrane / low-power / over-cap inputs (4 rejection paths tested).

## Robotics design layer pinned by the test (`stack_robotics.cljc`, ADR §5 + ADR-2606091800)

- **Anti-fouling coverage** — `cleaning-path` boustrophedon raster gives **>0.999 coverage** of a
  module face; lane count rises as head-width shrinks; param validation throws.
- **Reuses shared kuni-umi substrate** — planar-arm IK + safety gates via `hikari.methods.substrate`;
  funamori adds only the domain layer (coverage path + module-swap sequence).
- **No-server-key (G11)** — `plan-clean-pass` / `plan-module-swap` throw without a member signature,
  throw if a server signature is supplied; every plan datom carries `server-held-key=false` + `dry-run=true`.
- **Civilian-use (G13)** — forbidden `use` ("weapon") throws via `assert-civilian`.
- **Witness quorum (G14)** — <2 robot DIDs flags `witness-ok=false` + Council escalation.
- **Membrane gate reused at robotics layer** — `plan-module-swap` refuses a commercial (Toray) or
  PFAS (Nafion) replacement membrane (cross-method `assert-membrane-permitted`).

## Plant + grid-tie layer pinned by the test (`plant.cljc`, ADR §4 + ADR-2605264200 §3)

- **Tidal modulation** — output peaks at high tide (phase π/2), troughs at low tide; mean-tide
  output ≈ nameplate rating.
- **Capacity factor** — `generation-series` reports CF = mean/peak ∈ (0,1] over an M2 tidal cycle.
- **Peak-power cap reused** — `assert-plant-cap` (via `salinity-gradient/assert-site-cap`) throws
  when the tidal PEAK exceeds 50 kW, even if the mean would pass.
- **Grid-tie smoothing** — a large battery fully smooths (shortfall 0); a tiny battery reports an
  honest non-zero shortfall; bigger battery monotonically reduces shortfall.
- **hikari handoff** — `couple-to-microgrid` emits a `:funamori.plant/*` datom (sink "hikari",
  `model-only true`); refuses an over-cap plant.

## R0 → R1 gate

Post-Council + **≥1 membrane-chemist on Council** (ADR §5) + **mizuho R2** river-mouth
waterSupplySourceRegistry attestation + Council Lv6+ ≥3 estuarine baseline + LANDS-marine parcel.
Then bench ≤1 kW PRO OR ≤500 W RED single-stack pilot + open-membrane power-density characterization.
