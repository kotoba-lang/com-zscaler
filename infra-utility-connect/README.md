# infra-utility-connect — Utility Company Coordination Tier-B Actor

**DID**: `did:web:etzhayyim.com:infra-utility-connect`
**Namespace**: `com.etzhayyim.infra.*`
**ADR**: ADR-2605250900 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26)

## Overview

Phase 5+ actor: Coordinates final utility connections (water, gas, electric, telecom) with service providers.

**Input**: `mepSignoffRecord` (from tatekata commissioning), `utilityRequirements`
**Output**: `utilityActivationRecord` (all services live, meters installed)

## 4 Pregel Cells (Utility activation sequence)

### service_request
- **Input**: `mepSignoffRecord` + site coordinates
- **Output**: `serviceRequestIds` (water dept, gas co, electric co, ISP)

### provider_approval
- **Input**: `serviceRequestIds`
- **Output**: `approvalRecords` (utility companies grant permission to connect)

### meter_install
- **Input**: `approvalRecords`
- **Output**: `meterInstallationConfirmation` (water/gas/electric meters installed + calibrated)

### activation_test
- **Input**: `meterInstallationConfirmation`
- **Output**: `utilityActivationRecord` (all services tested, live)

## 12 Constitutional Gates (G1–G12)

- **G1**: All utility RPC calls open-source (no proprietary utility APIs)
- **G2**: Meter certification documents IPFS-pinned
- **G3**: Utility provider signature (≥1 per service: water/gas/electric/telecom)
- **G5**: No gatekeeping (services activated within legal SLA)
- Others: transparency, KPI tracking, outage logging

## 4-Phase Roadmap

- **R0**: Scaffold, mock utility RPC endpoints
- **R1**: Tokyo Water Bureau + Tokyo Gas + TEPCO + NTT APIs
- **R2**: 5+ prefecture utilities
- **R3**: Full utility mesh integration (upstream to kuni-umi power generation)
