# 20-actors/futawa — CLAUDE.md

## Identity

- **Name**: futawa (二輪 — two-wheel; concrete-noun style mirroring wadachi 轍 wheel-rut)
- **DID**: `did:web:etzhayyim.com:futawa`
- **ADR**: ADR-2605261330 (R0 scaffold, 2026-05-26)
- **Status**: R0 scaffold — all cells import-time RuntimeError on `.solve()`
- **Parent actor**: etzhayyim religious-corp (small-displacement motorcycle manufacturing Tier-B)
- **Source methodology**: YouTube `1xxRYlHY2e8` BMW Motorrad Berlin Spandau (methodology adopted; luxury / surveillance / racing retail layer rejected)

## Architecture

9 Pregel cells implementing 5-layer motorcycle assembly (L1 → L2 → L3 → L4 → L5):

```
moto_frame_welding ──> moto_engine_assembly ──> moto_drivetrain_assembly
   (L1, naphtali)         (L2a, joseph)             (L2b, zebulun)
                                                            │
                              moto_electrical_harness <─────┘
                                  (L3a, simeon)
                                       │
                                       ▼
                              moto_suspension_brake (G7 ABS-mandatory)
                                  (L3b, dan)
                                       │
                                       ▼
                                  moto_body_paint
                                  (L4, simeon)
                                       │
                                       ▼
                           moto_final_assembly (G12 parts catalog publish)
                                  (L5a, dan)  ──> hodoki VIN pre-reg (G13)
                                       │
                                       ▼
                                  moto_test_dyno_road
                                  (L5b, levi)
                                       │
                                       ▼
                              moto_provenance_binder
                                  (terminal, judah)
```

## Robotics Fleet (R0 reservation only)

| Robot | Class | Status | Function |
|---|---|---|---|
| Tsugite (継ぎ手) | Welding-joint | R1+ reservation | TIG/MIG vision-guided frame + exhaust weld |
| Suri (摺) | Paint-sprayer | R1+ reservation | Electrostatic + HVLP with G5 artwork integration |
| Yokin (kanayama) | Molten-metal pour | R2+ reuse | Engine block + transmission case |
| Otete | kuni-umi manipulator | reuse | Harness routing + bolt-up |
| Mimi | kuni-umi metrology | reuse | Dyno + defect + dimensional |
| Quad | kuni-umi logistics | R2+ reuse | Vehicle logistics |
| Tokike | hodoki | reuse | Assembly bolt-down (reversed torque) |

**G1**: All robot firmware open-source (Apache 2.0 + Charter Rider).

## Constitutional Gates (G1–G14)

**IMMUTABLE R0–R3.** Stored in `manifest.jsonld` under `futawa:constitutionalGates`. Changes require Council Lv6+ supermajority + new ADR.

See `ADR-2605261330` §4 for definitions. **4 constitutional firsts:**

- **G7 ABS-MANDATORY ≥125cc / ≥6kW electric**: First time safety-mandatory-at-build is constitutionally elevated for a religious-corp actor; no ABS-delete, no track-only carve-out, no cost-down strip. §1.13 adherent safety non-negotiable.
- **G8 BUILD-TIME ANTI-SURVEILLANCE**: NO GPS / NO connected-app / NO telematics / NO V2X / NO proprietary diagnostic-DRM built-in. Only passive odometer + safety ECU + optional **user-installed-after-purchase** open-source GPS module. §2(c) operationalized at build; **companion to hodoki G8 (EOL data destroy)** — surveillance loop closed across full vehicle lifecycle.
- **G12 RIGHT-TO-REPAIR FORWARD-PUBLISHING**: Every new vehicle ships with IPFS-pinned full parts catalog + CAD + firmware source + service manual + open diagnostic protocol; no proprietary lock-in; no anti-DRM; no warranty void on user repair; part discontinuation prohibited 30 years post-launch. §2(e) operationalized at build; **companion to hodoki G12 (EOL parts catalog)** — right-to-repair loop closed.
- **G14 30-YEAR SERVICE LIFE**: Frame + engine + transmission + motor housing + structural suspension designed for 30-year fatigue + corrosion + wear; mandatory parts availability 30 years post-discontinue; planned obsolescence prohibited; firmware 30-year update commitment. §1.13 wellbecoming applied to durable goods.

Other gates: G1 open-source CAD/firmware/fixtures / G2 mass-balance ≥98% / G3 ≥2-robot witness / G4 bilingual / G5 Charter §2(a-h) artwork scan / G6 sound ≤80 dB / G9 Murakumo inference / G10 SBT-gated personnel / G11 ≤250cc/≤15kW/≤200kg cap / G13 hodoki VIN pre-registration + ≥10% recycled.

## Non-Goals (N1–N10)

**EXCLUDED from R0–R3 scope.** Amendment requires Council Lv6+ supermajority + new ADR.

- N1: Military motorcycles (§2(a))
- N2: ≥500cc / sport-tourer / supersport / cruiser ≥1000cc (Wave 2 ADR)
- N3: Racing / track-only / unlimited-class
- N4: Premium / luxury / licensed-IP co-branding
- N5: Connected-app / telematics / GPS / V2X / cloud-ECU built-in
- N6: Trikes / sidecars / ATVs / UTV
- N7: Pure human-powered bicycles / pedal-assist e-bikes (Wave 2)
- N8: Aftermarket loud-exhaust / street-illegal "performance" mods
- N9: Hydrogen fuel-cell motorcycles (Wave 2 + safety review)
- N10: Surveillance-tier touring

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.futawa`

**Records (8 types, R0 stubs)**:

1. `com.etzhayyim.futawa.frameAttestation` — L1 frame weld + roundness + material lot
2. `com.etzhayyim.futawa.engineAttestation` — L2a engine/motor + torque + ≤250cc/≤15kW cap
3. `com.etzhayyim.futawa.electricalAttestation` — L3a harness + ECU + **G8 NO surveillance manifest**
4. `com.etzhayyim.futawa.paintAttestation` — L4 paint + artwork Charter scan + VOC
5. `com.etzhayyim.futawa.vehicleLotAttestation` — L5a VIN + **G12 parts catalog CID** + **G13 hodoki pre-registration**
6. `com.etzhayyim.futawa.testRecord` — L5b dyno + emissions + G6 sound + ABS function
7. `com.etzhayyim.futawa.partsCatalog` — **G12 forward-publishing companion to hodoki partsHarvestCatalog at EOL**
8. `com.etzhayyim.futawa.silenMobilityReview` — Council 5-of-7 Safe attestation

**Deferred to R1+**: Full lexicon schema definitions.

## Cross-Lifecycle Invariants (futawa ↔ hodoki)

futawa is the **build-side** companion of hodoki (EOL-side). Together they close three constitutional loops across the full vehicle lifecycle:

| Loop | Build side (futawa) | EOL side (hodoki) |
|---|---|---|
| **Anti-surveillance (§2(c))** | G8: NO GPS/telematics/connected-app built-in at manufacture | G8: MANDATORY ECU/infotainment/telematics data wipe before disassembly |
| **Right-to-repair (§2(e))** | G12: IPFS-pinned parts catalog + CAD + firmware source published at manufacture | G12: IPFS-pinned EOL parts catalog with VIN provenance |
| **Circular materials (§2(h))** | G13: VIN pre-registered with hodoki at production; ≥10% recycled mass via kanayama | G13: ≥95% material recovery; cross-actor feed to kanayama + makura + silicon |

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No physical manufacturing. All cells raise `RuntimeError("futawa R0 scaffold: activate via Council ADR-2605261345 post-ratification")` on `.solve()`.

**R1 activation trigger**:

1. ADR-2605261345 authored + Council Lv6+ vote
2. Certified motorcycle engineer SME onboarded
3. Certified ABS calibration SME onboarded
4. Benchtop ≤1 vehicle prototype demonstrated
5. First IPFS-pinned parts catalog publication demonstrated
6. hodoki integration handshake verified (test VIN pre-registration)

## Testing (R0)

**Smoke test**: Verify all 9 cells import without exception:

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama
python -c "from cells.moto_frame_welding import MotoFrameWeldingCell; assert MotoFrameWeldingCell"
python -c "from cells.moto_engine_assembly import MotoEngineAssemblyCell; assert MotoEngineAssemblyCell"
python -c "from cells.moto_drivetrain_assembly import MotoDrivetrainAssemblyCell; assert MotoDrivetrainAssemblyCell"
python -c "from cells.moto_electrical_harness import MotoElectricalHarnessCell; assert MotoElectricalHarnessCell"
python -c "from cells.moto_suspension_brake import MotoSuspensionBrakeCell; assert MotoSuspensionBrakeCell"
python -c "from cells.moto_body_paint import MotoBodyPaintCell; assert MotoBodyPaintCell"
python -c "from cells.moto_final_assembly import MotoFinalAssemblyCell; assert MotoFinalAssemblyCell"
python -c "from cells.moto_test_dyno_road import MotoTestDynoRoadCell; assert MotoTestDynoRoadCell"
python -c "from cells.moto_provenance_binder import MotoProvenanceBinderCell; assert MotoProvenanceBinderCell"
```

All should pass import; `.solve()` calls should raise `RuntimeError("futawa R0 scaffold...")`.

## Related Files

- `/20-actors/futawa/manifest.jsonld` — DID + cell registry + gates + non-goals
- `/90-docs/adr/2605261330-futawa-motorcycle-tier-b-actor-r0.md` — Master ADR
- `/20-actors/hodoki/README.md` — EOL companion (G8 + G12 + G13 cross-lifecycle)
- `/20-actors/kanayama/README.md` — recycled metals supplier
- `/20-actors/wadachi/README.md` — sibling (4-wheel autonomous R&D)
- `/CLAUDE.md` — Religious-corp status table row 54
