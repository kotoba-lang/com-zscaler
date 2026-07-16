# suki 鋤 — Maturity

**Stage: R0** (scaffold) — ADR-2605261500. Open-hardware farm-tractor actor (mitsuho's
mfg-side sibling). Right-to-Repair constitutional first-class; open ECU/CAN; fuel-transition
phase-gate (no fossil-only R2+); ≤SAE J3016 Level 3 autonomy; no surveillance hardware.

| Dimension | State |
|---|---|
| Lexicons | ✅ 9 under `com.etzhayyim.suki.*` (chassis / powertrain / electricalEcu / hitchPto / cab / paint / emissionsAudit / fieldTest / silenSukiReview) |
| Cells | 🟡 9 path-reserved in `40-engine/.../cells/suki_*` (R0) |
| Manifest | ✅ present (carries G1–G14) |
| Tests | ✅ `methods/test_charter_gates.py` — **8 tests, green** (added 2026-06-16; previously NO dedicated test) — pins G9/G10 RTR, G7 fuel, G8 emissions, G4 witness, N11 no-surveillance, G3 implement, G12 KPI-cap; `./run_tests.sh` |
| Methods | ⛔ no offline engine yet (R1 benchtop 50hp loop) |

## Charter gates pinned by the test

- **G9/G10 Right-to-Repair** — `electricalEcuAttestation` requires `ecuReflashNotManufacturerGated`
  + `noDealerLockoutAttest` + `replacementPartsCataloguedOpen` + `diagnosticCodesOpenInterpretable`
  + `g10RtrInvariantVerify` + `unlockStateAtShipDefault`; bootloader options are all open;
  CAN bus is the open ISOBUS only (no proprietary CAN).
- **G7 fuel transition** — `powertrain.fuelType` + `emissions.currentFuelType` are exactly the
  open fuel set (biodiesel / LFP-hybrid at R0-R1; LFP / H₂ / methanol at R2+); no fossil-only
  R2+ powertrain representable; `g7PhaseGateVerify` required.
- **G8 emissions** — `emissionsAuditRecord` requires NOx + PM + ISO-8178 NRSC/NRTC pass +
  jurisdiction certifications.
- **G4 witness quorum** — every attestation requires `witnessRobotDids` (≥2 robot DIDs).
- **N11 no surveillance** — `cabAttestation` requires `noSurveillanceHardwareVerified`.
- **G3 modular implement** — `hitchPtoAttestation` requires `noDrmDetectionVerified` +
  `multiVendorCompatVerify`; open ISOBUS detection; standard Cat-I/II/III hitch only.
- **G12 KPI cap** — `fieldTestRecord` requires `g12KpiCapVerify` + `saeAutonomyLevel` +
  road/field speed + axle-load caps.

## R0 → R1 gate

silenSukiReview `r1-benchtop-prototype-50hp-baseline` + Council Lv6+; cell `.solve()` stays
R0-gated until then. KPI value caps (≤200 hp / road ≤40 / field ≤15 km/h / ≤SAE L3 / axle ≤8 t)
enforced in the R1 cell logic.
