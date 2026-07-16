# mizuho 水穂 — Maturity

**Stage: R0** (scaffold) — ADR-2605263100. Community-scale water + sanitation substrate
(≠ mitsuho 瑞穂 food). No bottled water, no mandatory fluoridation, waqf-inalienable sources.

| Dimension | State |
|---|---|
| Lexicons | ✅ 5 under `com.etzhayyim.mizuho.*` (waterSupplySourceRegistry/waterQuality/wastewaterDischarge/waterContaminationIncident/silenMizuhoReview) |
| Manifest | ✅ `constitutionalGates` (G1–G12) |
| Tests | ✅ `methods/test_charter_gates.cljc` — **7 tests, green** (2026-06-17) |

## Gates pinned
- G5 const bottledWaterUnitsDistributed=0 · G6 const fluoridationAdditionAttested=false.
- G11 source const waqfInalienabilityAttested=true (silen 100%).
- G12 operatorVocationFlow=100% · G4 commercialUtilitySoftware=0.
- G3 waterQuality requires whoLimit + overallComplianceStatus (non-compliant-critical-halt).
- wastewater under jurisdictionalPermitCid + permitCompliant; contamination notifiedAtUtc + severity.

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `mizuho.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
