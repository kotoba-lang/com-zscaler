# hodoki 解き — Maturity

**Stage: R0** (scaffold) — ELV (end-of-life vehicle) disassembly + materials recovery, 5-layer
processing chain. Closes the circular loop with kanayama (metals) / makura (seat foam) /
silicon (ECU) / hikari (second-life batteries). Two CONSTITUTIONAL-FIRST gates: G8 mandatory
data wipe + G12 right-to-repair part catalog.

| Dimension | State |
|---|---|
| Lexicons | ✅ 8 under `com.etzhayyim.hodoki.*` (elvIntake / depollution / batteryHandling / catalystRecovery / partsHarvestCatalog / dataWipe / shredOutput / silenDeconstructionReview) |
| Cells | 🟡 9 path-reserved (5-layer ELV chain, R0) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) + `nonGoals` (N1–N10) machine-readable |
| Tests | ✅ **25 green** — `methods/test_charter_gates.py` (**9**, added 2026-06-16: gate set + data-wipe + F-gas + Li-ion + RTR + PGM + circular feed + intake screen) **+** `py/test_agent.py` (16, agent layer); `./run_tests.sh` aggregates both |
| Methods | 🟡 agent present; offline disassembly engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14; N1 (military) + N2 (aerospace) excluded.
- **G8 data wipe (CONSTITUTIONAL FIRST)** — `dataWipeAttestation` requires `ecuWipes` +
  `method` + `verified` + `g8Compliant`; method includes cryptographic-destruction +
  physical-chip-shred fallback (anti-surveillance before disassembly).
- **G6 F-gas** — `depollutionAttestation` requires `fgasCapture` + `recoveryRatePct` +
  `atmosphericVentingDetected` + `g6Compliant` (≥95% capture, no venting).
- **G7 Li-ion safety** — `batteryHandlingRecord` requires `g7Compliant` + `soh` +
  `thermalBaseline` + `routingDecision`.
- **G12 right-to-repair (CONSTITUTIONAL FIRST)** — `partsHarvestCatalog` requires
  `g12RightToRepairInvariant` + `g12NoDrmCircumvention` + `g12NoProprietaryLockIn` +
  `g12PublicDiscovery` + `vinProvenance` + `partDid` + `catalogCid`.
- **G14 PGM** — `catalystRecoveryRecord` requires `g14Compliant` + `yieldAudit` (Pt/Pd/Rh ≥95%).
- **G13 circular feed** — `shredOutputAttestation` requires `g13Compliant` + `kanayamaHandoff`
  + ferrous/non-ferrous mass (cross-actor circular feed to kanayama).
- **Intake screen** — `elvIntakeRecord` requires `charterScan` + `stolenRegistryCheck` +
  `n7TitleVerified` + `vin`.

## R0 → R1 gate

silenDeconstructionReview `r1-activation` + Council Safe tx; cell `.solve()` stays R0-gated.
G2 (≥98% mass-balance) / G6 / G13 / G14 quantitative thresholds enforced in the R1 cell logic.
