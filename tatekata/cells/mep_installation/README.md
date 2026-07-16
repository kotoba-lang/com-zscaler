# mep_installation — Pregel cell

MEP (Mechanical, Electrical, Plumbing) routing + testing. Part of tatekata Phase 3.

**Murakumo node**: zebulun (utilities specialist)
**Input**: `structuralAuthRecord`
**Output**: `mepSignoffRecord`
**Status**: R0 scaffold (import-time RuntimeError)

## Design

5-node LangGraph: route HVAC ductwork (Otete arm) → route electrical conduit → route water/gas piping → pneumatic/hydro testing → emit record.

Activation: R1 ADR-2605250715 + Council vote.
