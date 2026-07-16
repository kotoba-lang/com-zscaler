# 20-actors/seigyo — CLAUDE.md

## Identity

- **Name**: seigyo (制御 — control)
- **DID**: `did:web:etzhayyim.com:seigyo`
- **ADR**: ADR-2606111000 (R0 master, 2026-06-11), ADR-2606111100 (R1)
- **Status**: R0 scaffold — all 5 cells import-time RuntimeError
- **Position**: Tier-B substrate actor — owns ISA-95 L0–L2 for ALL manufacturing actors (igata / silicon / yakushi / futawa / suki / pillow / tsutae / hikari-denki / mizuho). Not a supplier; a layer their commissionings inherit.

## Architecture

```
L4  ADRs + Council attestation                      (governance)
L3  igata_* / silicon_* / pharma_* ... cells        (per-domain actors — NOT seigyo)
─────────────────────────────────────────────────────────────────
L2.5 seigyo_opcua_bridge      OPC UA (open62541); Modbus southbound
L2   seigyo_scada_gateway     FUXA | Rapid SCADA | OpenSCADA
L1   seigyo_plc_program_lifecycle → OpenPLC (IEC 61131-3 ST)
L1S  HARDWIRED interlocks     seigyo_interlock_attestation (attest ONLY)
L0   sensors + actuators      seigyo.ioPointRegistry
     telemetry: site historian → seigyo_historian_aggregate (N7 buckets north)
```

5 cells in `kotoba/crates/kotoba-kotodama/cells/seigyo_*/` (judah ×3, benjamin, simeon).

## NON-NEGOTIABLE invariants (read before ANY edit here)

1. **§3 safety path**: interlocks are hardwired / safety-relay (L1S). NO LLM,
   NO Murakumo inference, NO kotoba cell, NO network round trip in the safety
   path — a safety function must complete with the network cable cut. Cells
   attest the safety layer; they are never in it. Never write code that
   bypasses, masks, or resets an interlock.
2. **§3.4 setpoint envelopes**: optimization proposals (Murakumo-only per
   ADR-2605215000) land ONLY through min/max + rate-of-change envelopes
   compiled into the attested PLC program. Widening an envelope = new program
   version = new attestation.
3. **§2 vendor prohibition**: no proprietary DCS/PLC/SCADA runtime, ever.
   Interop with embedded third-party controllers is boundary-only (OPC UA /
   Modbus), trust class `untrusted-external`.
4. **§4 program identity**: a controller whose loaded-program hash ≠ attested
   CID gets NO recipe dispatch until resolved.
5. **§5 telemetry**: full-rate data never leaves the site historian.
   Northbound = aggregate buckets (≥1 min process, ≥1 h person-attributable).
   Alarms (`alarmEventRecord`) are exempt — operational facts, full fidelity.

## R1 (ADR-2606111100)

Single benchtop loop paired with igata R1 (ADR-2605261215): ≤2 kW electric
mock-furnace temperature PID — the die-preheat (220°C) analog — exercising the
full §4 lifecycle with a physical E-stop verification record. Reference ST
program + envelope table live in [`reference/`](reference/).

## Conventions

- Cell scaffolds follow the L5-wave pattern: `cell.py` raises `RuntimeError`
  until `COUNCIL_FLEET_ATTESTATION_TX_HASH` + `SILEN_SEIGYO_BASELINE_REVIEW_CID`
  are set non-`None` (R1 adds `CONTROLS_ENGINEER_REGISTRY_CID`).
- IEC 61131-3 **Structured Text is the canonical committed form** — LD/FBD may
  be authored but ST is what gets CID-attested.
- IEC 61508/61511 are design references, not certification claims (honest
  limitation acknowledged through R3).
