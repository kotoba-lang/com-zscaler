# data-center-ops

Data Center operations actor scaffold.

## DID

- `did:web:data-center-ops.etzhayyim.com`

## Manifest

- `20-actors/data-center-ops/actor-manifest.jsonld`

## Lexicons

- `com.etzhayyim.apps.dataCenterOps.infrastructure.getFacility`
- `com.etzhayyim.apps.dataCenterOps.infrastructure.listFacilities`
- `com.etzhayyim.apps.dataCenterOps.infrastructure.listRacks`
- `com.etzhayyim.apps.dataCenterOps.infrastructure.getPowerZones`
- `com.etzhayyim.apps.dataCenterOps.infrastructure.getSlaSummary`
- `com.etzhayyim.apps.dataCenterOps.dependency.seedBaseline`
- `com.etzhayyim.apps.dataCenterOps.dependency.collectGlobal`
- `com.etzhayyim.apps.dataCenterOps.dependency.listNodes`
- `com.etzhayyim.apps.dataCenterOps.dependency.listEdges`
- `com.etzhayyim.apps.dataCenterOps.dependency.getReverseTopo`
- `com.etzhayyim.apps.dataCenterOps.health`
- `com.etzhayyim.apps.dataCenterOps.coverage.get`

Lexicon files are under `00-contracts/lexicons/com/etzhayyim/apps/dataCenterOps/`.

## BPMN

- `etzhayyim-root/60-apps/etzhayyim-project-auto-sales-erp/bpmn/data-center-ops-operations.bpmn`
- `etzhayyim-root/60-apps/etzhayyim-project-auto-sales-erp/bpmn/data-center-ops-dependency-reverse-topo.bpmn`

Process outline:
- operations: telemetry collection -> reverse dependency topology -> capacity evaluation -> SLA check -> (risk? incident escalation : dashboard update)
- dependency: seed baseline -> list nodes -> list edges -> collect global -> reverse topo resolve

Dependency baseline includes:
- land
- facility
- permit/approval
- ISCO workforce
- APQC operations framework
- power
- rack
- server
- license/compliance

## MCP

MCP tool exposure is driven by T1 manifest XRPC triggers and actor capability registration.
The above XRPC NSIDs are intended MCP-callable surfaces for Data Center Ops reads/health/coverage.

## RisingWave Schema

Physical graph schema is defined by migration:

- `30-graph/graph-schema/migrations/20260416124000_data_center_ops_dependency_graph.ts`

Objects created:

- vertex table: `vertex_data_center_dependency`
- vertex table (global): `vertex_data_center_dependency_global`
- edge table: `edge_data_center_dependency`
- edge table (global): `edge_data_center_dependency_global`
- materialized views:
  - `mv_data_center_dependency_reverse_topology`
  - `mv_data_center_dependency_domain_summary`
  - `mv_data_center_dependency_global_actor`
  - `mv_data_center_dependency_global_edge`

Baseline seed rows include:

- `dc-operations`
- `apqc-operations-framework`
- `isco-workforce`
- `sla-governance`
- `license-compliance`
- `server-fleet`
- `rack-capacity`
- `power-grid`
- `facility-site`
- `permit-approval`
- `land-plot`
