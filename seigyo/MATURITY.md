# seigyo 制御 — Maturity

**Stage: R0** (scaffold) — ADR-2606111000. ISA-95 L0–L2 industrial CONTROL layer for the
manufacturing actors (igata / tsukuru / yakushi / futawa / suki / hikari / mizuho …).
Open stack only (OpenPLC + FUXA/Rapid SCADA + open62541); commercial DCS/PLC/SCADA
runtimes prohibited. Safety invariant ABSOLUTE: interlocks hardwired / safety-relay only,
no LLM/network in the safety path, actuation only via attested setpoint envelope.

| Dimension | State |
|---|---|
| Lexicons | ✅ 9 under `com.etzhayyim.seigyo.*` (ioPointRegistry / plcProgramAttestation / runtimeAttestation / scadaProjectAttestation / setpointEnvelope / interlockVerificationRecord / alarmEventRecord / telemetryAggregateRecord / silenSeigyoReview) |
| Cells | 🟡 5 path-reserved in `40-engine/.../cells/seigyo_*` (R0 import-time RuntimeError) |
| Manifest | ✅ present |
| Tests | ✅ `methods/test_charter_gates.py` — **8 tests, green** (added 2026-06-16; previously ZERO tests anywhere) — pins the safety + no-commercial-DCS + attestation gates; `./run_tests.sh` |
| Methods | ⛔ no offline engine yet (R1 benchtop loop, ADR-2606111100) |

## Charter / safety gates pinned by the test

- **No commercial DCS/SCADA** — `scadaProjectAttestation.scadaStack` enum is **exactly**
  {fuxa, rapid-scada, openscada}; no commercial vendor (Siemens/Honeywell/Yokogawa/Emerson/
  Rockwell/ABB/Schneider/Mitsubishi/AVEVA/Ignition…) is a representable enum value anywhere.
- **Safety interlock physical** — `interlockVerificationRecord` requires `physicallyVerified`;
  `interlockType` covers {estop, over-pressure, over-temperature, gas-detection, light-curtain, loto}.
- **IO trust isolation** — `ioPointRegistry.trustClass` = {attested, untrusted-external,
  safety-mirror-readonly} (external untrusted, safety mirror read-only).
- **Program attestation + runtime match** — `plcProgramAttestation` requires
  `councilAttestationRef` + `programCid` + `stSourceCid`; `runtimeAttestation` requires
  `loadedProgramHash` + `attestedProgramCid` + `match` (the running program == the attested CID).
- **Advisory-only actuation** — `setpointEnvelope` carries `minMilli` + `maxMilli` +
  `maxRateOfChangeMilli` (Murakumo optimization reaches the valve only via a bounded envelope).
- **G6 surveillance** — `telemetryAggregateRecord` requires `personAttributable` (declared, not covert).

## R0 → R1 gate

ADR-2606111100 benchtop loop baseline + Council Lv6+ + controls-engineer SME registration
(silenSeigyoReview); cell `.solve()` stays R0-gated (no live actuation) until then.
