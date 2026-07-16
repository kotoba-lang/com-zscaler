# tatekata R2 Benchtop PoC Specification

**Date**: 2026-05-26  
**Gate**: R1 Activation (ADR-2605250730)  
**Location**: Murakumo node naphtali (Giemon unit 1 benchtop, 192.168.1.15)

## Test Pit Setup

```
┌─────────────────┐
│  0.5m × 0.5m   │  Depth: 1.0m
│  Sand pit       │  N-value: 10-15 (loose)
│  Target depth:  │  ±50mm tolerance
│  1000mm ±50mm   │  (per gate G6 determinism)
└─────────────────┘
```

## Giemon Excavation FSM

1. **INIT** → Load FoundationState (siteId, completionPct=0)
2. **SURVEY** → Parse mock site plan (pit dimensions, target depth)
3. **PLANNING** → Giemon trajectory synthesis (5 bucket passes @ 1200s each, 200mm depth per pass)
4. **EXECUTION** → Run 5 passes:
   - Pass 1: 0–200mm (bucket load 40 cycles)
   - Pass 2: 200–400mm (bucket load 40 cycles)
   - Pass 3: 400–600mm (bucket load 38 cycles, loose sand flow)
   - Pass 4: 600–800mm (bucket load 36 cycles)
   - Pass 5: 800–1000mm (bucket load 33 cycles)
5. **METROLOGY_CHECK** → Mimi lasercan final depth (target: 1000 ± 50mm)
6. **WITNESS_WAIT** → Collect Giemon + Otete Ed25519 sigs (mock)
7. **COMPLETE** → Emit `constructionProgressRecord` to MST (testnet)

## Sensor Telemetry (Streamed @ 10 Hz)

```json
{
  "timestamp": "2026-06-15T09:00:00Z",
  "pass": 1,
  "bucket_depth_mm": 185,
  "bucket_load_count": 38,
  "arm_torque_nm": [125, 128, 122, 130, 126],
  "accelerometer_peak_g": 1.8,
  "bucket_load_sequence": "fill-lift-rotate-dump-return"
}
```

**Streaming**: IPFS pinning via local Kubo node, manifest.json published every 30s.

## Success Gate Checklist

- [ ] **Depth accuracy**: Final depth 1000 ± 50mm (gate G6 tolerance)
- [ ] **No anomalies**: Vibration < 2.0g, torque within spec per FoundationState.executionMetrics
- [ ] **Determinism**: 5 passes ± 60 seconds (gate G6 replayability @ 10 Hz)
- [ ] **IPFS pinning**: All telemetry CIDs resolvable via Kubo
- [ ] **MST record**: constructionProgressRecord emitted + cryptographically verified
- [ ] **Witness sigs**: ≥2 robot Ed25519 signatures (Giemon + Otete mock DIDs)
- [ ] **Zero intervention**: No human commands during EXECUTION → METROLOGY phases
- [ ] **SME sign-off**: Civil engineer attests all gates passed

## Rollback Conditions

If any gate fails:

1. **Depth overshoot** (>50mm deeper than target) → halt, manual inspection, redo with adjusted trajectory
2. **Vibration spike** (>2.0g) → halt, check bucket load saturation, adjust bucket size
3. **Anomaly flagged** → transition to halt_on_anomaly, escalate to SME
4. **No witness sigs** → fail PoC, retry signature collection

Rollback does NOT reset the test pit — continue from failed pass with SME-adjusted trajectory.

## Murakumo Dependencies

- **naphtali** (Giemon unit 1): Provide benchtop access, power, firmware upload
- **benjamin** (Otete mock sigs): Mock Ed25519 keypair for Otete-unit-2 DID
- **judah** (IPFS pinner): Manage local Kubo node, persist CIDs
- **dan** (MST operator): Local geth-private instance, account unlock for witness sigs

## Post-PoC Deliverables

1. **PoC Report** (markdown): Depth vs. time graph, anomaly log, telemetry sample, sign-off
2. **Telemetry Archive** (IPFS CID): All sensor streams pinned, manifest.json
3. **Firmware Log**: Giemon WASM execution trace (10 Hz samples, JSON Lines format)
4. **MST Records**: constructionProgressRecord + alert_on_anomaly (if triggered)
5. **SME Sign-Off**: Civil engineer attestation (signature, date, conditions for R2 activation)

---

**Timeline**: 2026-07-10 → 2026-07-30 (21 days allocated)  
**Go/No-Go Decision**: 2026-07-31 (approve R2 pilot site, or iterate PoC)
