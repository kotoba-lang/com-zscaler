# tatekata 建方 — Maturity

**Stage: R0** (scaffold) — construction actor (onshore civil + MEP, low-rise). EXECUTION-ONLY:
no architectural design (N9), no cost estimation (N10), no high-rise >12 stories (N1). Supplies
building-stock exposure + safe-site pre-designation to sonae; consumes igata column castings.

| Dimension | State |
|---|---|
| Lexicons | ✅ 4 under `com.etzhayyim.tatekata.*` (siteAttestation / materialAttestation / constructionProgressRecord / safetyIncidentReport) |
| Cells | 🟡 path-reserved (foundation → structural → MEP → finishing → commissioning, R0) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) + `nonGoals` (N1–N10) machine-readable |
| Tests | ✅ **17 green** — `methods/test_charter_gates.py` (**8**, added 2026-06-16: gate set + scope non-goals + witness + material provenance + site survey + safety transparency) **+** `py/test_agent.py` (9, agent layer); `./run_tests.sh` aggregates both |
| Methods | 🟡 agent present; offline construction engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **Scope discipline** — N1 excludes high-rise (>12 stories); N9 = execution-only (no
  architectural design); N10 = no cost estimation / budgeting (finance domain).
- **G3 witness quorum** — `constructionProgressRecord` / `materialAttestation` /
  `siteAttestation` require `attestingRobots`.
- **G5 sourcing audit** — `materialAttestation` requires `grade` + `standard` + `qcResult` +
  `supplierName`.
- **G2 site survey** — `siteAttestation` requires `soilClassification` + `surveyDate`
  (survey + IPFS pin before entry).
- **safety transparency** — `safetyIncidentReport` requires `incidentType` + `severity` +
  `incidentDate` + `reportDate`.

## R0 → R1 gate

Council Lv6+ baseline + the 5 construction cells; cell `.solve()` stays R0-gated. G6
trajectory determinism (Giemon joints @ 10 Hz) + KPI caps enforced in the R1 cell logic.
