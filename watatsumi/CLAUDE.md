# 20-actors/watatsumi — CLAUDE.md

## Identity

- **Name**: watatsumi (綿津見 / わだつみ — Shinto sea kami; counter-form of Funamori 船守 surface→submerged)
- **DID**: `did:web:etzhayyim.com:watatsumi`
- **ADR**: ADR-2605252200 (R0 scaffold, 2026-05-25)
- **Status**: R0 scaffold — all cells import-time RuntimeError on `.solve()`
- **Parent actor**: etzhayyim religious-corp (civilian submersible manufacturing Tier-B)
- **Surface counterpart**: kuni-umi.Funamori (船守, ADR-2605242745)

## Architecture

9 Pregel cells implementing modular ring-section construction (L1 → L2 → L3 → L4 → L5) + 2 cross-cutting:

```
hull_ring_fabrication → section_assembly → weld_inspection → system_integration
       (L1, naphtali)       (L2, zebulun)      (L3, joseph)      (L4, simeon)
                                                                        ↓
                          ↓ marine_emissions_audit (cross, levi)        ↓
                                                                        ↓
       sea_trial ← pressure_test ← section_joining ←──────────────────┘
        (L5c, levi)    (L5b, dan)     (L5a, dan)
              ↓
       class_certification_binder (terminal, judah)
```

## Robotics Fleet (R0 reservation only)

| Robot | Class | Status | Function |
|---|---|---|---|
| Sango (珊瑚) | Benthic AUV swarm | R1+ reservation | Outer-hull weld witness + biofouling monitoring |
| Tako (蛸) | Hull-clinging crawler | R2+ reservation | 8-leg suction interior NDT walker |
| Hibiki (響) | Fixed sonar / acoustic metrology | R1+ reservation | Acoustic-emission witness during pressure test |
| Ama (海女) | ADS-equivalent humanoid | R2+ reservation | Subsea welding + critical interior inspection |
| Otete-marine | kuni-umi Otete marinization | R1+ reservation | Subsea-rated manipulator |
| Mimi-marine | kuni-umi Mimi marinization | R1+ reservation | Pressure-compensated metrology |
| Funamori | kuni-umi class reuse | R3 reuse | Surface support / R3 mother-ship |

**G1**: All firmware open-source (Apache 2.0 + Charter Rider).

## Cable-Laying Robotics Fleet (ADR-2606012600)

Operational counterpart to **watatsuna 綿津綱** (the world submarine-cable-network knowledge-graph actor). watatsuna *knows* where the network is fragile; this fleet *acts* — lay / bury / splice / repair / monitor. Missions are planned OFF watatsuna's resilience output (lay diverse routes where `redundancy-gap`; pre-stage repair where `chokepoint-load` is high). Manifest: `data/cable-laying-fleet.kotoba.edn`.

**Tasking input (R2)**: `watatsuna/methods/plan.py` emits `watatsuna/out/resilience-plan.kotoba.edn` — `:plan/*` recommendations (`:lay-diverse-route` / `:pre-stage-repair` / `:monitor`) that name the robot classes below. The plan contains **redundancy + repair + monitor only — no interdiction output by construction** (G2 + N8).

| Robot | Glyph | Role | Status |
|---|---|---|---|
| Tsuna-suki | 綱鋤 | Towed sea plough / burial trencher (≤3 m, ≤2000 m) | R1+ |
| Horinuki | 掘抜 | Jet-trenching burial / PLIB ROV | R2+ |
| Tsugite | 接手 | Splice / repeater-housing manipulation ROV (inherits Otete-marine) | R2+ |
| Tedori | 手繰 | Grapnel cable-recovery ROV — **REPAIR-ONLY** | R2+ |
| Kikimimi | 聞耳 | DAS passive cable-health monitor → feeds watatsuna `flagCableFault` | R1+ |

**N8 invariant (CRITICAL).** Every unit acts ONLY to lay / bury / splice / repair / monitor. Cutting / interdiction / sabotage = **N8 hard-prohibited** (Charter Rider §2(d)). **Tedori** carries grapnel cut-and-hold for recovering a *faulted* cable to deck for re-splice; it operates solely under a logged, **G4 witness-quorum (≥2 robots)** repair work-order and is NEVER tasked against a healthy cable. **Kikimimi** monitoring exports no location data beyond the cable's own route (watatsuna G1).

## Constitutional Gates (G1–G14)

**IMMUTABLE R0–R3.** Stored in `manifest.jsonld` under `watatsumi:constitutionalGates`. Changes require Council Lv6+ supermajority + new ADR.

See `ADR-2605252200` §4 for definitions. Key enforcement:

- **G1**: Pressure hull CAD + FEA open-source (FreeCAD `.fcstd` / Open CASCADE / OpenSCAD only — vendor-free per G9)
- **G2**: Every class certification stage (DNV/ABS/NK/BV equivalent) anchored on kotoba-datomic
- **G3**: Every weld pass + test step has IPFS-pinned photo + video
- **G4**: Witness quorum ≥2 distinct robots (Sango + Tako, or Ama + Otete-marine) for critical welds
- **G7**: Autonomous submerged operation ≤ maritime SAE J3016 Level 4 equivalent
- **G8**: Active sonar ≤180 dB re 1µPa @1m (cetacean protection)
- **G12**: KPI caps — max depth 6500 m / max crew 3 / max submerged 72 h
- **G13**: Propulsion = LFP / H₂ / NH₃ / methanol fuel-cell only (nuclear = N2)

## Non-Goals (N1–N12)

**EXCLUDED from R0–R3 scope.** Amendment requires Council Lv6+ supermajority + new ADR.

- N1: Naval weapons (constitutional §2(a))
- N2: Nuclear propulsion (§2(g) + §1.15)
- N3: Military stealth submersibles (§2(a) + §2(d))
- N4: Bottom-mounted weapon platforms
- N5: EEZ sovereignty violation (except Transparent Force §1.12.B)
- N6: Deep-sea mining (§2(g) habitat)
- N7: UXO salvage (§2(a) war-contamination)
- N8: Submarine cable sabotage
- N9: Human depth-record vanity missions
- N10: Hadal-zone R&D priority
- N11: Closed-loop life support >72 h without Council review
- N12: Proprietary acoustic-stealth coating

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.watatsumi`

**Records (8 types, R0 stubs)**:

1. `com.etzhayyim.watatsumi.pressureHullAttestation` — L1 material lot / roundness / NDT result
2. `com.etzhayyim.watatsumi.sectionAssemblyAttestation` — L2 ring stacking + bulkhead + penetrators
3. `com.etzhayyim.watatsumi.weldInspectionRecord` — L3 100% NDT pass log
4. `com.etzhayyim.watatsumi.systemIntegrationAttestation` — L4 propulsion + life support + sensors
5. `com.etzhayyim.watatsumi.sectionJoiningAttestation` — L5a final ring-to-ring weld
6. `com.etzhayyim.watatsumi.pressureTestRecord` — L5b 1.25× design depth pressure test
7. `com.etzhayyim.watatsumi.seaTrialRecord` — L5c dock / harbor / deep-water trial
8. `com.etzhayyim.watatsumi.silenSubmersibleReview` — Council 5-of-7 Safe attestation, all new craft classes

**Deferred to R1+**: Full lexicon schema definitions. R0 ships stub JSON with `id` + `defs.main.type=record` only.

## Pregel Cells (Detailed)

### hull_ring_fabrication (L1)
- **Murakumo node**: naphtali
- **Input**: `materialLot` (HSLA-80 plate or Ti-6Al-4V ELI), `ringSpec` (Ø, thickness, frame pattern)
- **Output**: `pressureHullAttestation`
- **Key constraints**: roundness < 0.5% Ø, ring-frame TIG/SAW, no HY-100 without Council attestation

### section_assembly (L2)
- **Murakumo node**: zebulun
- **Input**: N × `pressureHullAttestation` (≥10 m sections), penetrator inventory
- **Output**: `sectionAssemblyAttestation`
- **Key constraints**: no weapon-mount penetrators (N1), internal bulkhead + hatch only

### weld_inspection (L3)
- **Murakumo node**: joseph
- **Input**: `sectionAssemblyAttestation`
- **Output**: `weldInspectionRecord` (100% RT/UT/PT per ASME BPVC §VIII Div 3 equivalent)
- **Witness**: Sango AUV swarm in-process + radiographer human SBT-gate

### system_integration (L4)
- **Murakumo node**: simeon
- **Input**: `weldInspectionRecord`
- **Output**: `systemIntegrationAttestation`
- **Subsystems**: propulsion (LFP / H₂ / NH₃ / methanol fuel-cell only), pressure-compensated electrical penetrations, ballast/trim, CO₂ scrubber + O₂ generator, passive sonar, acoustic modem, RF surface comm
- **Forbidden**: nuclear propulsion (N2), active sonar >180 dB (G8), proprietary stealth coatings (N12)

### section_joining (L5a)
- **Murakumo node**: dan
- **Input**: `systemIntegrationAttestation`
- **Output**: `sectionJoiningAttestation`
- **Key constraints**: multi-pass TIG + 100% RT on every final ring weld, PWHT mandatory

### pressure_test (L5b)
- **Murakumo node**: dan
- **Input**: `sectionJoiningAttestation`
- **Output**: `pressureTestRecord`
- **Protocol**: 1.25× design depth water pressure, Hibiki acoustic-emission monitoring continuous

### sea_trial (L5c)
- **Murakumo node**: levi
- **Input**: `pressureTestRecord`
- **Output**: `seaTrialRecord`
- **Stages**: dock trial → harbor dive → deep-water trial per IMCA D-001 equivalent

### marine_emissions_audit (cross-cutting)
- **Murakumo node**: levi
- **Input**: continuous telemetry stream from L1–L5c
- **Output**: continuous MARPOL Annex I-VI + BWMC + biofouling compliance record
- **G14 enforcement**

### class_certification_binder (terminal)
- **Murakumo node**: judah
- **Input**: all prior records (pressureHull → sea_trial + emissions)
- **Output**: `classCertificationRecord` (kotoba-datomic-anchored audit binder; DNV-RU-UWT / ABS Underwater Vehicles / NK 同等)

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No physical fabrication. All cells raise `RuntimeError("watatsumi R0 scaffold: activate via Council ADR post-ratification")` on `.solve()` call.

**R1 activation trigger**:
1. ADR-2605252215 authored + Council Lv6+ vote
2. Certified marine surveyor SME onboarded (Council attestation gate)
3. Benchtop pressure vessel ≤500 mm Ø fabricated + ≤30 m pool test demonstrated
4. Cell source replaces RuntimeError with LangGraph stub bodies

**Deployment** (R1+):
```bash
cd 20-actors/watatsumi
e7m actor deploy .
```

Returns error in R0; waits for R1 ADR activation.

## Testing (R0)

**Smoke test**: Verify all 9 cells import without exception:

```bash
cd 20-actors/watatsumi
python -c "from cells.hull_ring_fabrication import HullRingFabricationCell; assert HullRingFabricationCell"
python -c "from cells.section_assembly import SectionAssemblyCell; assert SectionAssemblyCell"
python -c "from cells.weld_inspection import WeldInspectionCell; assert WeldInspectionCell"
python -c "from cells.system_integration import SystemIntegrationCell; assert SystemIntegrationCell"
python -c "from cells.section_joining import SectionJoiningCell; assert SectionJoiningCell"
python -c "from cells.pressure_test import PressureTestCell; assert PressureTestCell"
python -c "from cells.sea_trial import SeaTrialCell; assert SeaTrialCell"
python -c "from cells.marine_emissions_audit import MarineEmissionsAuditCell; assert MarineEmissionsAuditCell"
python -c "from cells.class_certification_binder import ClassCertificationBinderCell; assert ClassCertificationBinderCell"
```

All should pass import; `.solve()` calls should raise `RuntimeError("watatsumi R0 scaffold...")`.

## Related Files

- `/20-actors/watatsumi/manifest.jsonld` — DID + cell registry + gates + non-goals
- `/90-docs/adr/2605252200-watatsumi-civilian-submersible-r0.md` — Master ADR
- `/20-actors/kuni-umi/README.md` — Funamori surface sibling (ADR-2605242745)
- `/CLAUDE.md` — Religious-corp status table row 45
