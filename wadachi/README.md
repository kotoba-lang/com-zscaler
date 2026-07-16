# wadachi — Autonomous Mobility R&D Tier-B Actor

**DID**: `did:web:etzhayyim.com:wadachi`
**Namespace**: `com.etzhayyim.wadachi.*`
**ADR**: ADR-2605242000 (R0 scaffold), ADR-2605242015 (R1), ADR-2605242030 (R2), ADR-2605242045 (R3)
**Status**: R0 scaffold (2026-05-23) — all cells import-time RuntimeError

## Overview

Autonomous mobility orchestration for on-site + inter-site ground transport. SAE J3016 Level ceiling = 4 (Level 5 = non-goal).

**Input**: `deliveryRoute` (origin, destination, obstacles, payloads)
**Output**: `missionCompleteRecord` (telemetry, safety flags, delivery confirmation)

## 5 Pregel Cells (Autonomous mission sequence)

### route_planning
- **Input**: `deliveryRoute` (GPS origin/dest, payload weight, site map)
- **Output**: `trajectoryPlan` (waypoints, obstacle buffer zones, speed profile)
- **Node**: Murakumo sora (aerial planning)

### motion_control
- **Input**: `trajectoryPlan`
- **Output**: `steeringCommands` (wheel angle, throttle, brake over time)
- **Node**: Murakumo wadachi-unit-1 (ground vehicle executor)

### obstacle_avoidance
- **Input**: `steeringCommands`, `sensorFusion` (LIDAR, camera, radar)
- **Output**: `avoidanceAdjustments` (dynamic path replanning if obstacle detected)
- **Node**: Murakumo wadachi-unit-1

### safety_monitoring
- **Input**: `motionTelemetry` (speed, acceleration, tilt, wheel slip)
- **Output**: `safetyStatus` (pass/critical_anomaly/halt)
- **Node**: Murakumo wadachi-unit-1

### telemetry_log
- **Input**: Full mission telemetry (GPS track, sensor streams, commands)
- **Output**: `missionCompleteRecord` (IPFS CID + MST record)
- **Node**: Murakumo sora

## 12 Constitutional Gates (G1–G12) + 8 Non-Goals (N1–N8)

**Gates**:
- G1: Firmware open-source (no proprietary vehicle control)
- G2: GPS track + sensor logs IPFS-pinned
- G3: Witness signature (≥1 safety monitor, ≥1 route authority)
- G5: Charter Rider compliance (no armed vehicle, no weapons integration)
- G6: Trajectory determinism (replay-able path @ 10 Hz)
- G9–G12: Transparency, personnel vetting, KPI caps, energy budgets

**Non-Goals**:
- N1: High-traffic roads (>20 vehicles/hr)
- N2: Pedestrian zones (Level 5 not supported)
- N3: Weather extremes (rain/snow/fog > 50m visibility)
- N4: Night operation (no headlights autonomous control, R2+)
- N5: Ferry/barge (waterborne, separate actor)
- N6: Railway crossing (level crossing automation, separate)
- N7: Tunnel/underground (communications loss, R3+)
- N8: Military/combat zones

## 4-Phase Roadmap

- **R0** (this wave): Scaffold, mock sensor fusion
- **R1**: Intra-site ≤1 m/s (foundations + MEP material delivery, tatekata partner)
- **R2**: Inter-site Level-3 SAE (driver-in-seat, 30 km/h, local roads)
- **R3**: Level-4 ODD (full autonomy on pre-mapped routes, ≤5 km)
