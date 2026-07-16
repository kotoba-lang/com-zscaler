# foundation_excavation — Pregel cell

Site survey + excavation plan. Part of tatekata (建方) Phase 1.

**Murakumo node**: naphtali (earth-moving specialist)
**Input**: `siteId` (DID), `boM` (bill of materials)
**Output**: `foundationAuthorized` record
**Status**: R0 scaffold (import-time RuntimeError)

## Design

5-node LangGraph:
1. `parse_site_plan` — load coordinates, soil classification
2. `survey_utilities` — check existing utilities (municipal DB)
3. `giemon_plan` — trajectory synthesis + volume estimate
4. `approval` — ≥2 robot + human sign-off (witness gate G3)
5. `emit` → write `foundationAuthorized` MST record

Activation: R1 ADR-2605250715 + Council Lv6+ vote.
