# tatekata (建方) — Construction Tier-B Actor

**DID**: `did:web:etzhayyim.com:tatekata`
**Namespace**: `com.etzhayyim.tatekata.*`
**ADR**: ADR-2605250700 (R0 scaffold), ADR-2605250715 (R1), ADR-2605250730 (R2), ADR-2605250745 (R3)
**Status**: R0 scaffold (2026-05-25) — all cells import-time RuntimeError

## Overview

Construction robotics orchestration actor for infrastructure projects (civil + MEP). Coordinates multi-robot site workflows, material routing, permit compliance, and safety governance.

**R0 scope (≤2 story, ≤5000m² civil + MEP)** excludes housing, high-rise (>12 stories), nuclear, hazmat manufacture, design (execution-only actor).

## Robotics Classes

| Class | Role | Inherited from | Notes |
|---|---|---|---|
| Giemon | crawler + arm | kuni-umi | site excavation, foundation prep |
| Otete | chem-resist arm | kuni-umi | MEP ductwork, conduit, temporary shoring |
| Hitogata | humanoid (future R2+) | kuni-umi | fine finishing, drywall, trim (deferred to R2) |
| Mimi | metrology | kuni-umi | structural verification, plumb/level/flatness |

## Pregel Cells (5)

All R0 cells are import-time RuntimeError (gate G14 + non-goal N9 activation barriers).

| Cell | Murakumo node | Phase | Input → Output |
|---|---|---|---|
| `foundation_excavation` | naphtali | site prep | siteId + boM → foundationAuthorized record |
| `structural_assembly` | joseph | structure | foundationAuthorized → structuralAuthRecord (Ed25519×2 witness) |
| `mep_installation` | zebulun | utilities | structuralAuthRecord → mepSignoffRecord |
| `finishing_handoff` | simeon | finishes | mepSignoffRecord → finishingRecord |
| `commissioning` | levi | closeout | finishingRecord → projectClosure (waste log) |

## Constitutional Gates (G1–G14)

See ADR-2605250700 for full list. **IMMUTABLE** per R0 scope.

Key gates:
- **G1**: Firmware open-source (WASM/Rust)
- **G2**: Photos/depth IPFS-pinned before human entry
- **G3**: ≥2 distinct robot signers per progress
- **G4**: Bilingual permit PDFs (EN + JA)
- **G5**: Charter Rider §2(g) material compliance
- **G6**: Giemon trajectory deterministic/replayable
- **G7–G8**: No chemistry R0; vendor-free CAD
- **G9–G14**: Mesh transparency, personnel vetting, safety KPIs, energy cap, schedule calendar, waste tracking

## Non-Goals (N1–N10)

Explicitly deferred or forbidden:
- N1–N2: High-rise, housing (separate ADR)
- N3–N4: Nuclear, hazmat, chemistry
- N5–N6: Tunnels, major bridges
- N7: Historical preservation (separate actor)
- N8: Deep-sea (scope = onshore ≤100m depth)
- N9: Architectural design (not construction)
- N10: Cost/budget (capital domain)

## Roadmap

| Phase | Timeline | Scope | Council gate |
|---|---|---|---|
| **R0** | 2026-05-25 | Scaffold | ✅ Proposed (awaits ADR-2605250700 merge) |
| **R1** | post-Council | Benchtop PoC (0.5m × 0.5m, depth ≤1m excavation) | ADR-2605250715 (SME onboarding + Giemon PoC) |
| **R2** | post-R1 | Pilot site (≤100m², ≤2 story, prefab). Hitogata future. | ADR-2605250730 (30-day public comment) |
| **R3** | post-R2 | Community scale (≤5000m², campus). Full Murakumo. | ADR-2605250745 (60-day public review) |

## Lexicons (4, all deferred to R1+)

```
com.etzhayyim.tatekata.{
  siteAttestation         # Site survey findings (soil, utilities, hazards)
  materialAttestation     # Material delivery + QA per batch
  constructionProgressRecord    # Phase transition + sensor CID + photo CID
  safetyIncidentReport    # On-site incident (falls, electrical, near-miss)
}
```

## Integration

- **Parent actor**: kuni-umi (Phase 3 multi-utility orchestration)
- **Peer actors**: yakushi (pharma), wadachi (mobility), silicon (ternary ASICs)
- **Domain**: Infrastructure + civil engineering
- **Witness quorum**: Per ADR-2605191524 (≥2 robot Ed25519 sigs + human attestation)
- **Land registry**: Integrates with global land trust (ADR-2605192245)

## References

- `/90-docs/adr/2605250700-tatekata-construction-tier-b-actor-r0.md` — Full ADR
- `/20-actors/kuni-umi/README.md` — Parent Tier-B actor
- `/CLAUDE.md` — Religious-corp status table
