# kanayama (金山) — Circular Metallurgy Tier-B Actor

**DID**: `did:web:etzhayyim.com:kanayama`
**Namespace**: `com.etzhayyim.kanayama.*`
**ADR**: ADR-2605252400 (R0 scaffold), ADR-2605252415 (R1, reserved), ADR-2605252430 (R2, reserved), ADR-2605252445 (R3, reserved)
**Status**: R0 scaffold (2026-05-25) — all cells import-time RuntimeError

## Overview

Closed-loop material recovery / circular metallurgy orchestrator. Adopts modern European integrated recycling-rolling mill methodology (Novelis Latchford / Constellium Neuf-Brisach / Speira Grevenbroich class). Constitutional posture is **strongly favorable** — §2(g) habitat + §2(h) circular economy + §2(e) anti-gatekeeping all positively align.

**Wave 1 reference (R0–R3 scope)**: closed-loop aluminum used beverage container (UBC) recycling — end-to-end UBC bale → can-stock coil within 3xxx + 5xxx alloy series.

**Wave 2-4 deferred to separate ADRs** (Council Lv6+ activation):
- Wave 2: Steel / iron (Yokin 1500°C ratings + EAF furnace)
- Wave 3: Copper / brass / bronze (secondary smelter + electrolytic refining)
- Wave 4: Rare-earth element recovery (ionic-liquid + bioleaching; §2(g) strict review)

**Primary mining (bauxite / iron ore / copper ore / etc.) is out of kanayama scope (N1)** — kanayama is the recovery/recycling actor and stays recovery-first **by preference**. Primary mining is NOT constitutionally forbidden: per ADR-2606161700 (Rider §2(l) v3.2) extraction is gated by a multi-generational (子・孫) × wellbecoming risk assessment, not a blanket ban; any extraction capability → its own actor + that gate.

## 9 Pregel Cells (5-layer closed-loop process)

| Cell | Layer | Murakumo node | Phase |
|---|---|---|---|
| `intake_qa` | L1 | naphtali | UBC bale weighing + Cl/moisture/magnetic QA |
| `decoating_separation` | L2 | zebulun | ~500°C lacquer/paint burnoff + shred + magnetic/eddy-current sort |
| `melting_furnace` | L3 | joseph | Twin-chamber Al furnace ~720°C + N₂/Cl₂ degas + alloy adjust |
| `dross_recovery` | L3 cross | joseph | Secondary Al recovery from dross + salt cake (G14) |
| `dc_casting` | L4 | simeon | DC slab casting + 540-580°C homogenization |
| `hot_rolling` | L5a | dan | Hot rolling ~500°C |
| `cold_rolling_finishing` | L5b | dan | Cold rolling + temper + 0.27 mm coil + surface QA |
| `air_emissions_audit` | cross-cutting | levi | Continuous PFC / NOx / SO₂ / particulate / dioxin stack monitor |
| `mass_balance_binder` | terminal | judah | input/output mass balance ≥98% closure on kotoba-datomic (G2 + G14) |

## 14 Constitutional Gates (G1–G14, IMMUTABLE R0–R3)

- **G1**: Furnace + casting + rolling firmware open-source (Apache 2.0 + Charter Rider)
- **G2**: Mass-balance audit on kotoba-datomic — `input_mass = output_metal + dross + emission` ≥98% closure
- **G3**: Per-batch + per-coil IPFS-pinned photo + video
- **G4**: Every pour signed by witness quorum ≥2 robots (Ed25519, DID-bound)
- **G5**: All permits + emissions reports JP + EN bilingual minimum + public disclosure
- **G6**: All alloy specs + firmware artifacts pass Charter Rider §2(a-h) scan
- **G7**: All autonomous robots open-source firmware
- **G8**: Air emissions ≤ EU IED 2010/75/EU + 日本大気汚染防止法 + EN 12457 solid-waste leachate
- **G9**: CAD only from vendor-free tools (FreeCAD / OpenSCAD / Open CASCADE)
- **G10**: Inference paths via Murakumo no-VKE mesh only
- **G11**: Hot-work zones + high-temp metal pour are SBT-gated personnel
- **G12**: KPI caps — recovery rate ≥95%; energy ≤6 kWh/kg for Wave 1 Al
- **G13**: Energy source = renewable + grid-balanced only (captive coal / petcoke prohibited)
- **G14**: Waste outputs (dross / salt cake / fume filter / leachate) IPFS lot-tracked + quarterly §2(h) report

## 8 Non-Goals (N1 = scope boundary per ADR-2606161700; N2–N8 IMMUTABLE R0–R3)

- **N1**: Primary mining out of kanayama scope (recovery-first by preference; NOT extraction-forbidden — gated by §2(l) multi-gen risk assessment per ADR-2606161700)
- **N2**: Hall-Héroult primary Al electrolysis + equivalent primary smelting for Wave 2+
- **N3**: Munitions casing / spent cartridge brass recovery (war-contamination transfer)
- **N4**: Nuclear-decontamination metal recovery (radiological feedstock)
- **N5**: Proprietary alloy compositions under NDA
- **N6**: E-waste with integrated PCB/IC boards co-processed without separate WEEE ADR
- **N7**: Deep-sea polymetallic nodule feedstock
- **N8**: Conflict-mineral feedstock (3TG: Tin / Tantalum / Tungsten / Gold) without source attestation

## Robotics Classes

**New (R0 reservation)**:
| Class | Role | Phase |
|---|---|---|
| Kamado (竈) | Refractory furnace tending (slag scoop, temp probe, refractory inspection) | R1+ |
| Yokin (溶金) | Molten-metal pour manipulator (Wave 1 Al ~720°C / Wave 2 steel ~1600°C) | R1+ |
| Migaki (磨き) | Rolled-coil surface inspector (visual + eddy-current + UT thickness) | R1+ |

**Inherited (thermal-upgrade)**:
- Otete-thermal (kuni-umi Otete high-temp variant)
- Mimi-thermal (kuni-umi Mimi high-temp metrology)
- Funamori (UBC bulk surface logistics; Wave 3 international maritime, reuse from ADR-2605242745)

## 4-Phase Roadmap

| Phase | Scope | Trigger |
|---|---|---|
| **R0** (this wave) | Scaffold only; 9 cells RuntimeError; 8 lexicon stubs | ADR-2605252400 |
| **R1** | Benchtop ≤1 kg pot melt + manual rolling PoC; UBC ≤10 kg/batch | ADR-2605252415 + Council Lv6+ + certified metallurgist SME |
| **R2** | Pilot 100 kg/day plant; Kamado + Yokin + Migaki PoC | ADR-2605252430 + 30-day public comment |
| **R3** | Community-scale 10 t/day integrated mill; coil shippable grade | ADR-2605252445 + 60-day public review + LANDS.md plant-site allocation |

## Lexicons (8 record types, R0 stubs)

```
com.etzhayyim.kanayama.{
  intakeRecord
  decoatingAttestation
  meltingAttestation
  dcCastingAttestation
  rollingAttestation
  coilQualificationRecord
  airEmissionsAuditRecord
  silenRecyclingReview
}
```

Schema details deferred to R1 ADR.

## Integration

- **Sibling actors**: watatsumi (水), tatekata (土), wadachi (陸), yakushi (薬), silicon (半導体), kuni-umi (planet)
- **Elemental complement**: watatsumi (海神) ↔ kanayama (金属神) — water and metal elemental pair
- **Downstream consumers** (Wave 1 Al coil): yakushi (sterile packaging), wadachi (vehicle frames), kuni-umi (conductor extrusion), tatekata (curtain wall / ductwork)
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 + human attestation)

## References

- `/90-docs/adr/2605252400-kanayama-circular-metallurgy-r0.md` — Master ADR
- `/CLAUDE.md` — Religious-corp status table row 46
