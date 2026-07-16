# graph-sos-intel

Graph System-of-Systems intelligence actor.

## DID

- `did:web:graph-sos-intel.etzhayyim.com`

## Manifest

- `20-actors/graph-sos-intel/actor-manifest.jsonld`

## Purpose

Continuously inventories and reasons over:

- `vertex_*` tables
- `edge_*` tables
- `mv_*` materialized/read-model relations
- `idx_*` indexes

It writes compact snapshots and findings into graph tables while leaving heavy
DDL execution to the existing RisingWave DDL governance path.

## RisingWave Schema

- `30-graph/graph-schema/migrations/20260507170000_graph_sos_intel.ts`
