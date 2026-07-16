# commissioning — Pregel cell

Final systems testing + defect log + waste inventory + project closure. Part of tatekata Phase 5.

**Murakumo node**: levi (verification specialist)
**Input**: `finishingRecord`
**Output**: `projectClosure` record + waste log
**Status**: R0 scaffold (import-time RuntimeError)

## Design

5-node LangGraph: final systems test (HVAC, electrical, plumbing) → defect walkdown (photo survey, punch-list) → waste inventory (reuse/recycle/landfill %) → human sign-off (≥2 robot sigs) → emit closure record.

**Gate G14 (IMMUTABLE)**: projectClosure record must include waste log. Charter Rider §2(h) assessment.

Activation: R1 ADR-2605250715 + Council vote.
