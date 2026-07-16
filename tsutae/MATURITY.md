# tsutae 伝え — Maturity

**Stage: R0** (scaffold) — ADR-2605261300. Open handheld comms device (≤200g): the
anti-surveillance / anti-addiction / repair-rightful inverse of a locked smartphone. Open
hardware + firmware + SoC; closes the EoL loop with kanayama.

| Dimension | State |
|---|---|
| Lexicons | ✅ 6 under `com.etzhayyim.tsutae.*` (chassis/pcb/firmware/device/recyclingCertificate/silenTsutaeReview) — rich gate-hook ledger |
| Cells | 🟡 path-reserved (R0) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) machine-readable |
| Tests | ✅ `methods/test_charter_gates.cljc` — **8 tests, green** (added 2026-06-17); `./run_tests.sh` |
| Methods | 🟡 offline engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **G6 anti-surveillance** — `chassisAttestation` requires `g6CellularRemovableVerified` +
  `g6MicrophoneKillSwitchVerified` (removable cellular + mandatory mic kill switch).
- **G3 repair-rightful** — `chassisAttestation` requires `batteryReplaceableHandToolsOnly` +
  `g3RepairScore`; `batteryChemistry` offers LFP.
- **G8 anti-addiction UX** — `firmwareAttestation` requires `dopamineLoopApiInstalled` +
  `infiniteScrollOsApiInstalled` + `g8CalmDefaultConfig` + `notificationBatchingMinutesDefault`.
- **G2 open OS + unlock-default** — `firmwareAttestation` requires `bootloader` +
  `unlockStateAtShipDefault` + `blobRatioPercent` + `g7BinaryBlobAudit`; firmware/OS names carry
  no proprietary/closed token (Knox/SEP/Snapdragon/…); open OS options present.
- **G9 open SoC mandatory** — `silenTsutaeReview.approvedSoc` is exactly the open-RISC-V /
  iwakura set (Snapdragon / Apple A unrepresentable).
- **secure crypto-erase on recycle** — `recyclingCertificate` requires `cryptoEraseCompleted`
  + `secureWipeAttestation` + `wipeMethod`.
- **G4 witness quorum** — chassis / firmware / pcb / device require `witnessRobotDids`.

## R0 → R1 gate

silenTsutaeReview `r1-benchtop-poc` + `r1-starfive-jh7110-bsp-open-source` + Council Lv6+.

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `tsutae.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
