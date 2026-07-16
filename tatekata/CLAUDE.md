# 20-actors/tatekata — CLAUDE.md

## Identity

- **Name**: tatekata (建方 — construction method)
- **DID**: `did:web:etzhayyim.com:tatekata`
- **ADR**: ADR-2605250715 (R0 scaffold, 2026-05-25)
- **Status**: R0 scaffold — all cells import-time RuntimeError
- **Parent actor**: kuni-umi (infrastructure Tier-B, ADR-2605201400)

## Architecture

5 Pregel cells arranged in linear Phase sequence:

```
foundation_excavation → structural_assembly → mep_installation → finishing_handoff → commissioning
    (naphtali)              (joseph)               (zebulun)           (simeon)          (levi)
```

Each cell = 1 Pregel graph with super-step semantics (3–5 LangGraph nodes per cell). Cells communicate via lexicon records on MST (`com.etzhayyim.tatekata.*` record types).

## Robotics Fleet

**R0 uses inherited kuni-umi classes** (no tatekata-specific hardware in R0):

| Robot | Class | Function | Firmware |
|---|---|---|---|
| Giemon | crawler + arm | excavation, prep | `kuni-umi.giemon.firmware` (open-source WASM) |
| Otete | chem-resist arm | MEP, shoring | `kuni-umi.otete.firmware` (open-source Rust) |
| Mimi | metrology | verification | `kuni-umi.mimi.firmware` (open-source) |
| Hitogata | humanoid (R2+) | finishing | deferred to R2 ADR |

**CRITICAL**: All firmware is open-source (Apache 2.0) per gate G1. No proprietary control loops.

## Constitutional Gates (G1–G14)

**IMMUTABLE in R0 and R1.** Stored in `manifest.jsonld` under `tatekata:constitutionalGates` array. Changes require Council Lv6+ supermajority + new ADR.

See `ADR-2605250700` for full definitions. Key enforcement:

- **G2**: IPFS pinning — all site photos + depth maps pinned **before** human equipment enters site
- **G3**: Witness quorum — progress records signed by ≥2 distinct robot DIDs (Giemon + Otete + Mimi)
- **G5**: Charter Rider compliance — sourcing audit (no conflict minerals, no rare-earth for R0 scope)
- **G6**: Trajectory determinism — Giemon joint angles logged @ 10 Hz, WASM state machine sealed
- **G9–G14**: Transparency gates (mesh 30-day notice, personnel vetting, KPI caps, waste tracking)

## Non-Goals (N1–N10)

**EXCLUDED from R0–R3 scope** (explicitly documented so future phases cannot violate):

- N1–N2: High-rise (>12 stories), housing
- N3–N4: Nuclear, hazmat, chemistry
- N5–N6: Tunnels (>30m), major bridges (>500m span)
- N7: Historical building preservation
- N8: Deep-sea platform (scope = onshore ≤100m depth)
- N9: Architectural design (execution-only actor)
- N10: Cost estimation / budgeting (capital domain)

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.tatekata`

**Records** (4 types):

1. **`com.etzhayyim.tatekata.siteAttestation`** — Site survey findings (soil classification, existing utilities, hazards)
2. **`com.etzhayyim.tatekata.materialAttestation`** — Batch material arrival + QA (certification, chemical analysis, weight)
3. **`com.etzhayyim.tatekata.constructionProgressRecord`** — Phase boundary + photo/depth CID + anomaly flags
4. **`com.etzhayyim.tatekata.safetyIncidentReport`** — On-site incident (near-miss, injury, equipment damage)

**Deferred to R1+**: Full lexicon schema definitions. R0 uses stub placeholders.

## Pregel Cells (Detailed)

### foundation_excavation

- **Murakumo node**: naphtali (earth-moving specialist)
- **Input**: `siteId` (DID), `boM` (bill of materials / excavation plan summary)
- **Output**: `foundationAuthorized` record
- **LangGraph nodes** (placeholder in R0):
  1. `parse_site_plan` — load site coordinates + soil auth
  2. `survey_existing_utilities` — call municipal DB lookup (external HTTPS)
  3. `giemon_excavation_plan` — trajectory synthesis + volume estimate
  4. `approval_gate` — ≥2 robot attestation + human engineer sign-off
  5. `emit_record` → write `foundationAuthorized` to MST

### structural_assembly

- **Murakumo node**: joseph (structural specialist)
- **Input**: `foundationAuthorized` record
- **Output**: `structuralAuthRecord` (witness-signed)
- **LangGraph nodes** (placeholder):
  1. `load_bim_model` — fetch CAD from vendor-free source (FreeCAD `.fcstd` or Open CASCADE)
  2. `validate_foundations` — compare survey vs design assumptions
  3. `giemon_otete_coordination` — multi-robot trajectory planning (shoring + assembly)
  4. `structural_witness` — Mimi metrology (plumb/level) + ≥2 robot Ed25519 sigs
  5. `emit_record` → write `structuralAuthRecord` to MST

### mep_installation

- **Murakumo node**: zebulun (utilities specialist)
- **Input**: `structuralAuthRecord`
- **Output**: `mepSignoffRecord`
- **LangGraph nodes** (placeholder):
  1. `route_ductwork` — HVAC Otete arm trajectory (ductwork assembly)
  2. `route_conduit` — electrical Otete arm trajectory (conduit + wire pulls)
  3. `route_piping` — water/gas Otete arm trajectory (solder/press fittings)
  4. `pressure_test` — pneumatic/hydro testing (external contractor RPC)
  5. `emit_record` → write `mepSignoffRecord` to MST

### finishing_handoff

- **Murakumo node**: simeon (finishing specialist)
- **Input**: `mepSignoffRecord`
- **Output**: `finishingRecord`
- **LangGraph nodes** (placeholder):
  1. `prep_surfaces` — Giemon prep (substrate cleaning, dust removal)
  2. `drywall_tape_mud` — Hitogata humanoid mock (deferred to R2; R0 uses manual subcontractors)
  3. `paint_seal` — spray + cure (R0 manual or Otete mock)
  4. `trim_install` — manual or Hitogata (R0 manual)
  5. `emit_record` → write `finishingRecord` to MST

### commissioning

- **Murakumo node**: levi (verification specialist)
- **Input**: `finishingRecord`
- **Output**: `projectClosure` record + waste log
- **LangGraph nodes** (placeholder):
  1. `final_systems_test` — HVAC, electrical, plumbing verification
  2. `defect_walkdown` — photo survey + punch-list generation
  3. `waste_inventory` — material waste categorization (reused / recycled / landfill %)
  4. `sign_off` — human project manager + ≥2 robot sigs
  5. `emit_record` → write `projectClosure` to MST

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No real construction. All cells raise `RuntimeError("tatekata R0 scaffold: activate via Council ADR post-ratification")` on first super-step.

**R1 activation trigger**:
1. ADR-2605250715 authored + Council Lv6+ vote
2. SME civil engineer onboarded (Council attestation gate)
3. Giemon PoC firmware tested in benchtop (0.5m × 0.5m excavation ≤1m depth)
4. Cell source replaces RuntimeError with LangGraph stub bodies

**Deployment**:
```bash
cd 20-actors/tatekata
e7m actor deploy .
```

(Returns error in R0; waits for R1 ADR activation.)

## Testing (R0)

**Smoke test**: Verify that all 5 cells import without exception:
```bash
cd 20-actors/tatekata
python -c "from kotodama.cells.foundation_excavation import FoundationExcavationCell; assert FoundationExcavationCell"
python -c "from kotodama.cells.structural_assembly import StructuralAssemblyCell; assert StructuralAssemblyCell"
python -c "from kotodama.cells.mep_installation import MepInstallationCell; assert MepInstallationCell"
python -c "from kotodama.cells.finishing_handoff import FinishingHandoffCell; assert FinishingHandoffCell"
python -c "from kotodama.cells.commissioning import CommissioningCell; assert CommissioningCell"
```

All should pass import; `.solve()` calls should raise `RuntimeError("tatekata R0 scaffold...")`.

## Related Files

- `/20-actors/tatekata/manifest.jsonld` — DID + cell registry
- `/90-docs/adr/2605250715-tatekata-construction-tier-b-actor-r0.md` — ADR (parent)
- `/20-actors/kuni-umi/README.md` — Parent actor (Tier-B infrastructure)
- `/CLAUDE.md` — Status table row 43 (tatekata)
