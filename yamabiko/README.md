# yamabiko (山彦) — High-Speed Rail Manufacturing Tier-B Actor

**DID**: `did:web:etzhayyim.com:yamabiko`
**Namespace**: `com.etzhayyim.yamabiko.*`
**ADR**: ADR-2605252600 (R0 scaffold), ADR-2605252615 (R1, reserved), ADR-2605252630 (R2, reserved), ADR-2605252645 (R3, reserved)
**Status**: R0 scaffold (2026-05-25) — all cells import-time RuntimeError

## Overview

High-speed rail trainset manufacturing orchestrator. Adopts modern friction-stir-welded extruded-aluminum carbody + bogie modular + multi-system integration methodology (Hitachi A-Train / Kawasaki efSET / Talgo Avril / Siemens Velaro / Alstom Avelia class).

**Wave 1 reference (R0–R3 scope)**: Civilian Shinkansen-class trainset (~250-320 km/h, 8-16 cars). End-to-end carbody → bogie → interior → traction → final assembly → dynamic test → homologation.

**Wave 2-3 deferred (Council Lv6+ activation)**:
- Wave 2: Conventional rail (EMU / DMU / BEMU, ~120 km/h commuter + intercity)
- Wave 3: Light rail / tram (LRT, ≤80 km/h, low-floor, urban)

**Military rail, NBC transport, riot-control rail, R2+ diesel locomotives, mass-surveillance trains, advertising wraps, luxury-only trains are constitutional non-goals** (N1–N12) per Charter Rider §2(a) + §2(d) + §2(g) + §2(c) + §2(e) + §1.13 anchors.

## Sibling positioning

| Actor | Domain |
|---|---|
| **yamabiko (this)** | Rail manufacturer (track-bound trainsets) |
| sarutahiko (ADR-2605252500) | Road manufacturer (heavy commercial trucks) |
| wadachi (ADR-2605242000) | Road operator (autonomous mobility software + light hardware) |
| watatsumi (ADR-2605252200) | Submerged manufacturer (civilian submersibles) |
| kanayama (ADR-2605252400) | Material recovery / circular metallurgy |
| tatekata (ADR-2605250715) | Construction (incl. track-bed civil works cross-actor for Wave 1) |
| hikari | Energy (electrification cross-actor for Wave 1 traction power) |

## 9 Pregel Cells (5-layer assembly process)

| Cell | Layer | Murakumo node | Phase |
|---|---|---|---|
| `carbody_fabrication` | L1 | naphtali | FSW Al 6N01/A6005C extrusion carbody seam welding |
| `bogie_assembly` | L2 | joseph | Cast steel bogie frame + air spring + tread brake + axle + wheel set + traction motor |
| `interior_hvac` | L3 | zebulun | Al-honeycomb floor + fire-retardant seating + wheelchair accessibility + HEPA HVAC + multilingual PIS |
| `traction_electrical` | L4 | levi | 25 kV AC / 1500 V DC pantograph + traction inverter + ATP/ATO (G1 open-source firmware mandate) |
| `final_assembly` | L5a | simeon | Carbody + bogie + interior + electrical marriage + cab + livery |
| `dynamic_test` | L5b | dan | ≥100 km test track |
| `homologation_binder` | L5c | judah | EN 50126/50128/50129 RAMS / 日本鉄道事業法 / FRA Tier I-III |
| `emissions_acoustic_audit` | cross-cutting | levi | Continuous ISO 3095 / 騒音規制法 / IEC 62236 EMC |
| `silenRailReview` | governance | judah | Council 5-of-7 Safe (new Wave / new trainset / new jurisdiction) |

## 14 Constitutional Gates (G1–G14, IMMUTABLE R0–R3)

- **G1**: Control firmware (ATP/ATO/traction) + carbody CAD + bogie CAD open-source (Apache 2.0 + Charter Rider)
- **G2**: Per-trainset manufacturing log kotoba-datomic anchor + open trainset registry
- **G3**: Per-trainset IPFS-pinned photo + video (FSW seam / dynamic test / homologation)
- **G4**: Every critical FSW + bogie marriage signed by witness quorum ≥2 robots
- **G5**: Operator manual + passenger PIS JP+EN+local trilingual minimum
- **G6**: All CAD + firmware Charter Rider §2(a-h) scan
- **G7**: **R0/R1 transition**: BEMU + H₂ fuel-cell hybrid acceptable. **R2+: full electrification + H₂/NH₃ hybrid only — diesel locomotive phased out.**
- **G8**: Wayside noise ≤ ISO 3095 + 日本騒音規制法 + vibration ≤60 dB + EMC IEC 62236
- **G9**: CAD only from vendor-free tools
- **G10**: Inference via Murakumo no-VKE mesh only
- **G11**: High-voltage + bogie + paint = SBT-gated personnel
- **G12**: KPI caps — commercial max ≤320 km/h / trainset length ≤450 m / **autonomous ≤ GoA 3** (GoA 4 driverless = N7)
- **G13**: Per-trainset DID `did:web:etzhayyim.com:yamabiko:trainset:<serial>`
- **G14**: EoL recyclability ≥90% by mass (closes loop with kanayama)

## 12 Non-Goals (N1–N12, IMMUTABLE R0–R3)

- **N1**: Military trains (armored, missile-rail TEL, troop transport)
- **N2**: Border guard / police / riot control rail
- **N3**: NBC material transport
- **N4**: R2+ diesel locomotives
- **N5**: Proprietary signalling + ATP/ATO firmware NDA
- **N6**: Third-party advertising train wraps (route maps + safety OK)
- **N7**: GoA 4 fully autonomous unmanned operation Wave 1
- **N8**: Mass-surveillance trains (face recognition PIS / behavior tracking)
- **N9**: Passenger surveillance UX (biometric data sale)
- **N10**: Luxury first-class / premium-only trains
- **N11**: National prestige vanity projects (ROI-blind politically-driven)
- **N12**: Proprietary coupling / bogie / gauge breaking interoperability

## Robotics Classes

**New (R0 reservation)**:
| Class | Role | Phase |
|---|---|---|
| Tsugite (継手) | FSW manipulator (Al 6N01/A6005C carbody seams) | R1+ |
| Wadasa (輪佐) | Wheel set + bogie installation manipulator (≥2 t payload) | R1+ |
| Toritsuke (取付) | Interior + seating + HVAC fitting | R2+ |
| Pantagora (パンタゴラ) | Pantograph + high-voltage harness routing (R0 name placeholder) | R2+ |

**Inherited**:
- Otete-heavy (sarutahiko derivative reuse)
- Mimi-precision (sarutahiko derivative reuse)
- Akari (sarutahiko ECU/electrical reuse)

## 4-Phase Roadmap

| Phase | Scope | Trigger |
|---|---|---|
| **R0** (this wave) | Scaffold only; 9 cells RuntimeError; 9 lexicon stubs | ADR-2605252600 |
| **R1** | Benchtop 1-car mockup + manual assembly + rail engineering SME | ADR-2605252615 + Council Lv6+ + civil engineer SME |
| **R2** | Pilot 1 trainset (3-4 car EMU) ≤120 km/h commuter class | ADR-2605252630 + 30-day public comment |
| **R3** | Community-scale 8-16 car ≥1 trainset/month Shinkansen ≤320 km/h | ADR-2605252645 + 60-day public review + LANDS.md depot + test track ≥10 km allocation |

## Lexicons (9 record types, R0 stubs)

```
com.etzhayyim.yamabiko.{
  carbodyAttestation
  bogieAttestation
  interiorAttestation
  tractionElectricalAttestation
  finalAssemblyAttestation
  dynamicTestRecord
  acousticEmissionsAuditRecord
  homologationRecord
  silenRailReview
}
```

## Integration

- **Land-mobility siblings**: sarutahiko (road manufacturer) + wadachi (road operator)
- **Material upstream** (R3): kanayama Wave 1 Al body coil + Wave 2 steel + Wave 3 Cu (cross-actor supply loop)
- **Track-bed cross-actor**: tatekata (civil works, track laying, depot construction)
- **Electrification cross-actor**: hikari (overhead line, substation, traction power)
- **EoL loop**: scrapping yard returns Al/steel/Cu/interior plastic to kanayama
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 + human attestation)

## References

- `/90-docs/adr/2605252600-yamabiko-high-speed-rail-manufacturing-r0.md` — Master ADR
- `/20-actors/sarutahiko/README.md` — Road sibling
- `/CLAUDE.md` — Religious-corp status table row 56
