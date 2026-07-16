# watatsumi (綿津見) — Civilian Submersible Manufacturing Tier-B Actor

**DID**: `did:web:etzhayyim.com:watatsumi`
**Namespace**: `com.etzhayyim.watatsumi.*`
**ADR**: ADR-2605252200 (R0 scaffold), ADR-2605252215 (R1, reserved), ADR-2605252230 (R2, reserved), ADR-2605252245 (R3, reserved)
**Status**: R0 scaffold (2026-05-25) — all cells import-time RuntimeError

## Overview

Civilian deep-sea submersible manufacturing orchestrator. Adopts modular ring-section construction methodology from mature European shipyard practice; civilianises by metallurgical, propulsion, and acoustic-emission constraints.

**R0 scope (civilian only)**:
- Research submersibles (manned ≤3 / unmanned ROV/AUV, design depth ≤6500 m)
- Subsea infrastructure inspection + cable laying support (≤2000 m)
- Aquaculture infrastructure + benthic observation networks (≤200 m)

**Naval weapons + nuclear propulsion + stealth military submersibles are constitutional non-goals** (N1, N2, N3) per Charter Rider §2(a) and ADR-2605192100 §1.15.

Tourist submersibles are deferred to R3+ pending wellbecoming §1.13 Council review.

## 9 Pregel Cells (5-layer construction process)

| Cell | Layer | Murakumo node | Phase |
|---|---|---|---|
| `hull_ring_fabrication` | L1 | naphtali | Pressure hull ring rolling + ring-frame welding |
| `section_assembly` | L2 | zebulun | 10–15 m section stacking + bulkhead + penetrators |
| `weld_inspection` | L3 | joseph | 100% RT/UT/PT NDT (ASME BPVC §VIII Div 3 equivalent) |
| `system_integration` | L4 | simeon | Propulsion (LFP/H2/NH3/methanol) + life support + passive sonar |
| `section_joining` | L5a | dan | Final ring-to-ring multi-pass TIG + PWHT |
| `pressure_test` | L5b | dan | 1.25× design-depth water-pressure test |
| `sea_trial` | L5c | levi | Dock → harbor → deep-water class trial |
| `marine_emissions_audit` | cross-cutting | levi | MARPOL Annex I-VI + BWMC + biofouling |
| `class_certification_binder` | terminal | judah | DNV-RU-UWT / ABS Underwater Vehicles audit binder on kotoba-datomic |

## 14 Constitutional Gates (G1–G14, IMMUTABLE R0–R3)

- **G1**: Pressure hull CAD + FEA + firmware open-source (Apache 2.0 + Charter Rider)
- **G2**: Class certification audit log on kotoba-datomic (DNV/ABS/NK/BV equivalent)
- **G3**: Every weld pass + test step IPFS-pinned photo + video
- **G4**: Witness quorum ≥2 distinct robots per critical weld (Ed25519, DID-bound)
- **G5**: JP + EN bilingual minimum for all permits / class reports / owner's manuals
- **G6**: Charter Rider §2(a-h) scan on every CAD + firmware artifact
- **G7**: Autonomous submerged operation ≤ maritime SAE J3016 Level 4 equivalent (Level 5 = non-goal)
- **G8**: Active sonar ≤180 dB re 1µPa @1m (NMFS Level A cetacean threshold)
- **G9**: CAD only from vendor-free tools (FreeCAD / OpenSCAD / Open CASCADE)
- **G10**: Inference via Murakumo no-VKE mesh only (ADR-2605214000 / ADR-2605215000)
- **G11**: Hot-work / pressure-test / dive operations are SBT-gated personnel
- **G12**: KPI caps — max civilian depth 6500 m / max manned crew 3 / max submerged duration 72 h
- **G13**: Propulsion fuels = LFP / H2 / NH3 / methanol fuel-cell only (nuclear = N2 constitutional)
- **G14**: MARPOL Annex I-VI + BWMC + IMO biofouling guidelines

## 12 Non-Goals (N1–N12, IMMUTABLE R0–R3)

Charter Rider §2(a) + §2(d) + §2(g) + §1.13 + §1.15 constitutional anchors:

- **N1**: Naval weapons (torpedoes, missiles, mines, depth charges, kinetic weapon mounts)
- **N2**: Nuclear propulsion
- **N3**: Military stealth / covert / camouflaged submersibles
- **N4**: Bottom-mounted weapon platforms / hibernating arsenal submersibles
- **N5**: Sovereignty-violating EEZ incursion (except Transparent Force §1.12.B authorised)
- **N6**: Deep-sea mining (polymetallic nodule / hydrothermal sulfide / cobalt-crust)
- **N7**: Salvage of unexploded ordnance / wartime munitions
- **N8**: Submarine cable cutting / sabotage / interdiction
- **N9**: Human depth-record / vanity dive priority missions
- **N10**: Mariana / hadal-zone (≤-10,000 m) R&D as priority
- **N11**: Closed-loop life support beyond 72 h without independent Council review
- **N12**: Proprietary acoustic-stealth coating R&D

## Robotics Classes

**New (R0 reservation)**:
| Class | Role | Phase |
|---|---|---|
| Sango (珊瑚) | Benthic inspection AUV swarm (outer-hull weld witness + biofouling) | R1+ |
| Tako (蛸) | Hull-clinging interior NDT walker (8-leg suction) | R2+ |
| Hibiki (響) | Fixed sonar / acoustic metrology station | R1+ |
| Ama (海女) | ADS-equivalent humanoid subsea welder | R2+ (Hitogata marinization) |

**Inherited (marinized)**:
- Otete-marine (kuni-umi Otete subsea-rated)
- Mimi-marine (kuni-umi Mimi pressure-compensated)
- Funamori (surface support / R3 mother-ship; ADR-2605242745 reuse)

**Cable-laying fleet (ADR-2606012600)** — operational counterpart to **watatsuna 綿津綱** (world cable-network KG actor); see `data/cable-laying-fleet.kotoba.edn` + `CLAUDE.md`:
| Class | Role | Phase |
|---|---|---|
| Tsuna-suki (綱鋤) | Towed sea plough / burial trencher (≤3 m, ≤2000 m) | R1+ |
| Horinuki (掘抜) | Jet-trenching burial / PLIB ROV | R2+ |
| Tsugite (接手) | Splice / repeater-housing manipulation ROV | R2+ |
| Tedori (手繰) | Grapnel cable-recovery ROV — **REPAIR-ONLY** (N8) | R2+ |
| Kikimimi (聞耳) | DAS passive cable-health monitor → feeds watatsuna | R1+ |

> **N8**: this fleet lays / buries / splices / repairs / monitors only. Cutting / interdiction = hard-prohibited. Tedori recovers *faulted* cable for re-splice under a logged G4 witness-quorum work-order, never a healthy one.

## 4-Phase Roadmap

| Phase | Scope | Trigger |
|---|---|---|
| **R0** (this wave) | Scaffold only; 9 cells RuntimeError; 8 lexicon stubs | ADR-2605252200 |
| **R1** | Benchtop ≤500 mm Ø pressure vessel; ≤30 m pool test; ROV ≤1 m PoC | ADR-2605252215 + Council Lv6+ + certified marine surveyor SME |
| **R2** | Pilot ROV ≤2 m; harbor trials ≤200 m; tatekata-shared yard pilot facility | ADR-2605252230 + 30-day public comment |
| **R3** | Research submersible ≤6500 m or infrastructure ROV ≤2000 m; full DNV/ABS class; Funamori mother-ship integration | ADR-2605252245 + 60-day public review |

## Lexicons (8 record types, R0 stubs)

```
com.etzhayyim.watatsumi.{
  pressureHullAttestation         # L1
  sectionAssemblyAttestation      # L2
  weldInspectionRecord            # L3
  systemIntegrationAttestation    # L4
  sectionJoiningAttestation       # L5a
  pressureTestRecord              # L5b
  seaTrialRecord                  # L5c
  silenSubmersibleReview          # Council 5-of-7 Safe attestation
}
```

Schema details deferred to R1 ADR.

## Integration

- **Sibling actors**: kuni-umi (planetary infra), wadachi (ground mobility), tatekata (construction), yakushi (pharma), silicon (semiconductors)
- **Surface counterpart**: kuni-umi.Funamori (船守, ADR-2605242745) — civilian surface bulk cargo
- **Land trust**: R3 community-scale dry-dock allocation will require LANDS.md amendment
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 sigs + human attestation)

## References

- `/90-docs/adr/2605252200-watatsumi-civilian-submersible-r0.md` — Master ADR
- `/20-actors/kuni-umi/README.md` — Funamori surface sibling
- `/CLAUDE.md` — Religious-corp status table row 45
