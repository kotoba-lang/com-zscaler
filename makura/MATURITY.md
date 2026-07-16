# makura 枕 — Maturity

**Stage: R0** (scaffold) — foam-pillow manufacturing actor, 5-layer assembly. Closes the
take-back loop with hodoki (ELV seat-foam crumb → recycled fill). G14 CONSTITUTIONAL-FIRST:
no embedded electronics (anti-surveillance for consumer goods).

| Dimension | State |
|---|---|
| Lexicons | ✅ 8 under `com.etzhayyim.makura.*` (fabric / foamBatch / pillowLot / qc / packaging / recyclingCertificate / workerExposure / silenComfortReview) |
| Cells | 🟡 9 path-reserved (5-layer foam-pillow assembly, R0) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) + `nonGoals` (N1–N10) machine-readable |
| Tests | ✅ **14 green** — `methods/test_charter_gates.py` (**8**, added 2026-06-16: gate set + no-electronics + KPI/BoM/take-back + worker-exposure + FR + witness/bilingual + charter scan + recycling loop) **+** `py/test_agent.py` (6, agent layer); `./run_tests.sh` aggregates both |
| Methods | 🟡 agent present; offline foam engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **G14 no embedded electronics** — `pillowLotAttestation` requires `g14NoEmbeddedElectronics`;
  `embeddedElectronics` enum is structurally `{none}` only (anti-surveillance consumer good).
- **G11/G12/G13** — pillow lot requires `withinG11Cap` + `g12FullBomDisclosed` +
  `g13TakeBackQrPresent` + `bom` + `pillowDid`.
- **G6 isocyanate** — `workerExposureRecord` requires `g6Compliant` + `agent` (MDI/TDI exposure).
- **G8 FR** — `pillowLotAttestation` records `fireRetardant` (FR-free baseline disclosed).
- **G3/G4** — witness quorum (`attestingRobots`) on lot/foam/qc/fabric + `g4BilingualMinimumMet`.
- **charter scan** — `fabricAttestation` requires `charterScan`; §2c surveillance scan is a
  3-state {clear, warn, violation} verdict.
- **G13 recycling loop** — `recyclingCertificate` requires `recycledBlend` + `chainEntryCid` +
  `intakeCenterDid` + `returnedPillowDid` (cross-actor take-back with hodoki seat foam).

## R0 → R1 gate

silenComfortReview `r1-activation` + Council Safe tx; cell `.solve()` stays R0-gated. G6
exposure limits (MDI ≤5 ppb / TDI ≤2 ppb), G7 VOC, G11 size/mass caps enforced in R1 cell logic.
