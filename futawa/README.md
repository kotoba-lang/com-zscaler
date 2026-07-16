# futawa (二輪) — Small-Displacement Motorcycle Manufacturing Tier-B Actor

**DID**: `did:web:etzhayyim.com:futawa`
**Namespace**: `com.etzhayyim.futawa.*`
**ADR**: ADR-2605261330 (R0 scaffold), ADR-2605261345 (R1, reserved), ADR-2605261400 (R2, reserved), ADR-2605261415 (R3, reserved)
**Status**: R0 scaffold (2026-05-26) — all cells import-time RuntimeError

## Overview

Small-displacement motorcycle manufacturing Tier-B actor for adherent personal mobility. Adopts mature global motorcycle OEM methodology (frame → drivetrain → harness/suspension/paint → final → dyno); religious-corp-ised by ABS-mandatory safety + build-time anti-surveillance + right-to-repair forward-publishing + 30-year service life + ≥10% recycled content via hodoki+kanayama.

**Source video**: YouTube `1xxRYlHY2e8` "Inside Massive German Factory Building BMW Motorrad Bikes From Scratch" — methodology adopted; luxury / premium / ≥500cc / racing-focus / connected-app surveillance retail positioning rejected per §2(e) + §2(c) + §1.13.

**R0 scope:**

- ≤250cc 4-stroke single-cylinder gasoline ICE **OR** ≤15kW peak electric
- Commuter + utility positioning (rural / mountain / weather accessibility)
- Curb mass ≤200kg, cargo ≤30kg total panniers
- Full chain: frame welding → engine/motor → drivetrain → harness → suspension+brake → body+paint → final → test → provenance (with hodoki VIN pre-registration)

**≥500cc + racing + luxury + connected-app surveillance + trikes/sidecars + pure bicycles + H2 FCV are constitutional non-goals** (N2, N3, N4, N5, N6, N7, N9).

## 9 Pregel Cells (5-layer motorcycle assembly process)

| Cell | Layer | Murakumo node | Phase |
|---|---|---|---|
| `moto_frame_welding` | L1 | naphtali | Steel/Al tube + sheet frame welding (Cu wire kanayama Wave 3) + Tsugite witness |
| `moto_engine_assembly` | L2a | joseph | ≤250cc 4-stroke single OR ≤15kW electric motor assembly |
| `moto_drivetrain_assembly` | L2b | zebulun | Transmission + chain/belt drive |
| `moto_electrical_harness` | L3a | simeon | Wiring harness + safety ECU (G8 NO GPS/telematics/connected-app) |
| `moto_suspension_brake` | L3b | dan | Fork + monoshock + **G7 ABS-mandatory ≥125cc** + caliper/rotor |
| `moto_body_paint` | L4 | simeon | Body panel + 2K acrylic urethane paint + G5 artwork Charter scan |
| `moto_final_assembly` | L5a | dan | Bolt-up + bilingual VIN + **G12 IPFS parts catalog published at manufacture** + G13 hodoki VIN pre-registration |
| `moto_test_dyno_road` | L5b | levi | Dyno + emissions + G6 sound ≤80 dB + ABS function test + road test |
| `moto_provenance_binder` | terminal | judah | KotobaDatomic anchoring (material lots → VIN + parts catalog + tests + hodoki pre-reg) |

## 14 Constitutional Gates (G1–G14, IMMUTABLE R0–R3)

- **G1**: CAD + firmware + tool fixtures open-source (foundation for G12)
- **G2**: Mass-balance ≥98% closure on kotoba-datomic (inherits kanayama + hodoki pattern)
- **G3**: ≥2-robot witness quorum per critical step (frame weld + engine + brake torque)
- **G4**: JP + EN bilingual owner + service manual + safety labels + parts catalog
- **G5**: Charter §2(a-h) scan on paint artwork + decals (no military / licensed-IP / addictive-thrill / racing-glorification)
- **G6**: Sound emissions ≤80 dB @ 7.5m (UN-ECE R41); no loud-exhaust enablement
- **G7** (**CONSTITUTIONAL FIRST**): **ABS MANDATORY on all 4-stroke ≥125cc + all electric ≥6kW** — no ABS-delete, no track-only carve-out, no cost-down strip; dual-channel hydraulic safety-redundant; elevates EU 168/2013 Annex II to constitutional invariant
- **G8** (**CONSTITUTIONAL FIRST**): **NO GPS / NO connected-app / NO telematics / NO V2X / NO proprietary diagnostic-DRM built-in**; only passive odometer + safety ECU + optional user-installed-after-purchase open-source GPS module — **§2(c) anti-surveillance at vehicle BUILD time; companion to hodoki G8 (EOL data destroy)** — closes surveillance loop across full lifecycle
- **G9**: Murakumo no-VKE inference (defect / paint / dyno / weld-vision)
- **G10**: Hot work + brake assembly + paint booth + battery handling SBT-gated personnel
- **G11**: KPI caps R0–R1 — ≤250cc OR ≤15kW; curb ≤200kg; cargo ≤30kg panniers; ≥500cc / >15kW / racing = N2/N3 Wave 2
- **G12** (**CONSTITUTIONAL FIRST**): **RIGHT-TO-REPAIR FORWARD-PUBLISHING** — IPFS-pinned full parts catalog + CAD + firmware source + service manual + open diagnostic protocol shipped with EVERY new vehicle at manufacture; no proprietary lock-in; no anti-DRM; no warranty void on user repair; **part discontinuation prohibited 30 years post-launch** — **companion to hodoki G12 (EOL parts catalog)** — closes right-to-repair loop across full lifecycle
- **G13**: Cross-actor circular feed — VIN pre-registered with hodoki at production; ≥10% recycled-content mass from kanayama-via-hodoki by R3; battery cells for electric models from hikari R2+ second-life (SoH ≥85%)
- **G14** (**CONSTITUTIONAL FIRST**): **30-year MINIMUM design service life**; mandatory parts availability 30 years post-discontinue; planned obsolescence prohibited; firmware 30-year update commitment — §1.13 wellbecoming applied to durable goods

## 10 Non-Goals (N1–N10, IMMUTABLE R0–R3)

- **N1**: Military motorcycles / armed scout / weapon-mount-equipped (§2(a))
- **N2**: ≥500cc displacement / sport-tourer / supersport / cruiser ≥1000cc (Wave 2 ADR)
- **N3**: Racing / track-only / unlimited-class supersport (§1.13 + §2(e))
- **N4**: Premium / luxury / licensed-IP co-branding (Disney / sports / vintage-IP)
- **N5**: Connected-app / telematics / GPS / V2X / paired-phone / cloud-ECU built-in
- **N6**: Trikes / sidecars / ATVs / quad / UTV
- **N7**: Pure human-powered bicycles / pedal-assist e-bikes (Wave 2)
- **N8**: Aftermarket loud-exhaust enablement / street-illegal "performance" mods
- **N9**: Hydrogen fuel-cell motorcycles (Wave 2 + dedicated safety review)
- **N10**: Surveillance-tier touring (passenger camera / V2V always-on / pillion data collection)

## Robotics Classes

**New (R0 reservation)**:

| Class | Role | Phase |
|---|---|---|
| Tsugite (継ぎ手) | Welding-joint robot (TIG/MIG vision-guided) for frame + exhaust | R1+ |
| Suri (摺) | Paint-sprayer robot (electrostatic + HVLP) with G5 artwork integration | R1+ |

**Inherited**:

- kanayama Yokin — engine block + transmission case pour (Al Wave 1 / steel Wave 2)
- kuni-umi Otete — harness routing + bolt-up
- kuni-umi Mimi — metrology + dyno data + defect classification
- kuni-umi Quad — vehicle logistics + finished-bike transport
- hodoki Tokike — assembly bolt-down with reversed torque

## 4-Phase Roadmap

| Phase | Scope | Trigger |
|---|---|---|
| **R0** (this wave) | Scaffold only; 9 cells RuntimeError; 8 lexicon stubs | ADR-2605261330 |
| **R1** | Benchtop ≤1 vehicle prototype + Tsugite/Suri prototypes + IPFS parts catalog publication + hodoki integration handshake | ADR-2605261345 + Council Lv6+ + motorcycle engineer SME + ABS calibration SME |
| **R2** | Pilot ≤10/month + tatekata-shared shop + parts catalog live ≥5 variants + hodoki VIN pre-reg operational | ADR-2605261400 + 30-day public comment + hodoki integration verified |
| **R3** | Community-scale ≤500/month + ≥10% recycled content + 30-year service-life commitment registry | ADR-2605261415 + 60-day public review |

## Lexicons (8 record types, R0 stubs)

```
com.etzhayyim.futawa.{
  frameAttestation              # L1
  engineAttestation             # L2a
  electricalAttestation         # L3a — includes G8 NO surveillance manifest
  paintAttestation              # L4
  vehicleLotAttestation         # L5a — includes G12 catalog CID + G13 hodoki pre-reg
  testRecord                    # L5b — dyno + emissions + sound + ABS function
  partsCatalog                  # G12 forward-publishing companion to hodoki partsHarvestCatalog
  silenMobilityReview           # Council 5-of-7 Safe attestation
}
```

## Integration

- **Sibling actors**: wadachi (vehicle build R&D), hodoki (ELV EOL — build/EOL companion via G8 + G12 + G13), kanayama (recycled metals supplier), makura (consumer-good anti-surveillance pattern), kuni-umi, tatekata
- **Cross-actor feed**:
  - Receives: Al frame + steel + Cu wire from kanayama (via hodoki for recycled mass)
  - Receives: ECU PCB from silicon (R2+)
  - Receives: battery cells from hikari R2+ second-life (SoH ≥85%) for electric models
  - Emits: VIN pre-registration to hodoki at manufacture (G13 build-side closure)
- **Lifecycle invariants closed**:
  - G8 build (futawa) ↔ G8 EOL (hodoki) = surveillance loop closed
  - G12 build (futawa parts catalog at manufacture) ↔ G12 EOL (hodoki parts catalog at EOL) = right-to-repair loop closed
  - G13 build pre-registration (futawa) ↔ G13 EOL recovery (hodoki) = circular material loop closed
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 sigs)
- **Source methodology**: YouTube `1xxRYlHY2e8` BMW Motorrad Berlin Spandau (methodology adopted; luxury / surveillance / racing retail layer rejected)

## References

- `/90-docs/adr/2605261330-futawa-motorcycle-tier-b-actor-r0.md` — Master ADR
- `/20-actors/wadachi/README.md` — sibling (autonomous mobility R&D)
- `/20-actors/hodoki/README.md` — EOL companion (G8 + G12 + G13 cross-lifecycle)
- `/20-actors/kanayama/README.md` — recycled metals supplier
- `/CLAUDE.md` — Religious-corp status table row 54
