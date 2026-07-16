# sarutahiko (猿田彦) — Heavy Truck Manufacturing Tier-B Actor

**DID**: `did:web:etzhayyim.com:sarutahiko`
**Namespace**: `com.etzhayyim.sarutahiko.*`
**ADR**: ADR-2605252500 (R0 scaffold), ADR-2605252515 (R1, reserved), ADR-2605252530 (R2, reserved), ADR-2605252545 (R3, reserved)
**Status**: R0 scaffold (2026-05-25) — all cells import-time RuntimeError
**Organism axis**: Axis 2 — Metabolism (代謝 / 産霊 musuhi) — generative production cycle: metabolizes steel + powertrain lots into heavy trucks (see [`90-docs/2606022500-organism-axis-affiliation-convention.md`](../../90-docs/2606022500-organism-axis-affiliation-convention.md))

## Overview

Heavy-truck manufacturing orchestrator. Manufacturing-side counterpart of `wadachi` (operator-side autonomous mobility, ADR-2605242000). Adopts modular line architecture from Turkey OEM-class large-scale heavy-truck plant practice (Ford Otosan F-MAX / Mercedes-Benz Türk / MAN Türkiye / BMC / DAF / Iveco).

**Wave 1 reference (R0–R3 scope)**: Civilian Class-8 cargo truck (~26-40 t GVWR, 6×2 / 6×4 drive). End-to-end frame → powertrain → cab → marriage → paint+electrical → QA → VIN attestation.

**Wave 2-3 deferred to separate ADRs (Council Lv6+ activation)**:
- Wave 2: Mid + light commercial (Class 3-7, ~3.5–7.5 t GVWR)
- Wave 3: Civilian specialty vehicles (fire engine + ambulance + cold-chain refrigerated — §1.13 wellbecoming positive)

**Military trucks, weapons transport, riot control, mining haul trucks, fossil fuel tankers, surveillance vehicles, MaaS rental fleets are constitutional non-goals** (N1–N12) per Charter Rider §2(a) + §2(d) + §2(g) + §2(b) + §2(c) anchors.

## Positioning vs wadachi (operator side)

| Concern | wadachi (轍) | sarutahiko (猿田彦) |
|---|---|---|
| Domain | Operator (route + motion + safety + telemetry) | Manufacturer (frame + powertrain + cab + assembly + paint + QA) |
| Witness invariant | Robot route signing | Robot weld / marriage / paint witness |
| Output | `missionCompleteRecord` per trip | `vehicleManufactureRecord` per VIN |
| Cells | 5 (route/motion/obstacle/safety/telemetry) | 9 (frame/powertrain/cab/marriage/paint/electrical/road-test/emissions/VIN) |

A vehicle produced by sarutahiko is **operated** under wadachi gates; a vehicle operated under wadachi is **manufactured** under sarutahiko gates. Siblings, not nested.

## 9 Pregel Cells (5-layer assembly process)

| Cell | Layer | Murakumo node | Phase |
|---|---|---|---|
| `frame_fabrication` | L1 | naphtali | HSLA-590/780 ladder-frame robotic MIG/MAG welding, straightness <1 mm/m |
| `powertrain_assembly` | L2 | joseph | Engine + transmission + axle (G7 fuel guard: R0/R1 B100 biodiesel + diesel hybrid; R2+ LFP/H₂/NH₃/methanol fuel-cell only) |
| `cab_body_forming` | L3 | zebulun | Steel/Al sheet hot stamping + robotic spot welding + leak test |
| `final_marriage` | L4 | simeon | Chassis lowering + cab drop + powertrain mount + harness; ≥2 robot witness |
| `paint_finishing` | L5a | simeon | KTL primer + base + clear (water-based, VOC <100 g/L) |
| `electrical_integration` | L5b | levi | Harness routing + ECU flash (G1 open-source firmware mandate) + diagnostics |
| `quality_road_test` | L5c | levi | Roller dyno + 50 km public-road test (SAE Level 3 driver-in-seat, Norimichi) |
| `emissions_audit` | cross-cutting | levi | Continuous Euro 7 + 大気汚染防止法 + Bharat Stage VI compliance |
| `vin_attestation_binder` | terminal | judah | Per-VIN kotoba-datomic anchor (G2 open VIN registry) |

## 14 Constitutional Gates (G1–G14, IMMUTABLE R0–R3)

- **G1**: ECU + electrical firmware open-source (Apache 2.0 + Charter Rider)
- **G2**: Per-VIN manufacturing log kotoba-datomic anchor + open VIN registry
- **G3**: Per-VIN IPFS-pinned photo + video (frame welding / paint / road test)
- **G4**: Every critical weld + final marriage signed by witness quorum ≥2 robots
- **G5**: Operator + service manual JP+EN bilingual minimum + open-source
- **G6**: All CAD + firmware Charter Rider §2(a-h) scan
- **G7**: **R0/R1 transition**: B100 biodiesel + diesel hybrid acceptable. **R2+**: LFP / H₂ / NH₃ / methanol fuel-cell only. Pure-fossil phased out at R2 gate.
- **G8**: Emissions ≤ Euro 7 + 日本 ポスト新長期 + Bharat Stage VI (R0-R1); R2+ zero tailpipe
- **G9**: CAD only from vendor-free tools (FreeCAD / OpenSCAD / Open CASCADE)
- **G10**: Inference via Murakumo no-VKE mesh only
- **G11**: Paint booth + welding zone + hot work = SBT-gated personnel
- **G12**: KPI caps — Wave 1 GVWR ≤40 t / max speed ≤90 km/h civilian / range ≥800 km / **autonomous ≤ SAE J3016 Level 4** (Level 5 = non-goal, wadachi G7 echo)
- **G13**: Per-VIN DID `did:web:etzhayyim.com:sarutahiko:vehicle:<vin>`
- **G14**: EoL recyclability ≥90% by mass (closes loop with kanayama)

## 12 Non-Goals (N1–N12, IMMUTABLE R0–R3)

Charter Rider §2(a) + §2(d) + §2(g) + §2(b) + §2(c) anchors:

- **N1**: Military trucks (4×4/6×6 troop carriers, MRAP, armored vehicles)
- **N2**: Weapons + ammunition transport (ammo trucks, missile/torpedo transporter, ICBM TEL, field artillery prime mover)
- **N3**: Riot control / water-cannon / armored police vehicles
- **N4**: Mining haul trucks (rigid frame for coal / iron ore / bauxite / oil-sand)
- **N5**: Fossil fuel tankers (bio-fuel + water + food tankers remain in scope)
- **N6**: Military surveillance vehicles (ELINT / SIGINT / military lidar swarm)
- **N7**: Fully autonomous unmanned military platforms
- **N8**: Proprietary ECU under NDA
- **N9**: Driver-suppression UX (biometric monitoring data sale, behavior conditioning beyond safety-critical)
- **N10**: Pure-fossil-only powertrain at R2+ (R0/R1 transition only)
- **N11**: Mobile billboard / LED advertising trucks
- **N12**: For-profit MaaS rental fleets (non-profit member delivery + community logistics remain in scope)

## Robotics Classes

**New (R0 reservation)**:
| Class | Role | Phase |
|---|---|---|
| Kasane (重ね) | Frame + chassis MIG/MAG welding manipulator (HSLA-590/780 thick plate) | R1+ |
| Tsutsumi (包み) | Cab body paint booth robot (water-based KTL, VOC <100 g/L) | R2+ |
| Akari (灯り) | Electrical harness routing + ECU flash + diagnostics binding | R1+ |
| Norimichi (乗道) | Public-road test driver (SAE Level 3 driver-in-seat, wadachi inheritance) | R2+ |

**Inherited**:
- Otete-heavy (kuni-umi Otete heavy-payload ≥200 kg variant)
- Mimi-precision (kuni-umi Mimi μm-level alignment)
- Migaki (kanayama coil + body-panel surface inspector reuse)

## 4-Phase Roadmap

| Phase | Scope | Trigger |
|---|---|---|
| **R0** (this wave) | Scaffold only; 9 cells RuntimeError; 9 lexicon stubs | ADR-2605252500 |
| **R1** | Benchtop 1-vehicle prototype (≤2 t cargo van) + manual assembly + B100 biodiesel | ADR-2605252515 + Council Lv6+ + automotive engineering SME |
| **R2** | Pilot ≤10 vehicles/month Class 3-5 cargo (~7.5 t) + LFP battery hybrid | ADR-2605252530 + 30-day public comment + Kasane/Tsutsumi/Akari PoC |
| **R3** | Community-scale Class-8 (~26-40 t) ≥100 vehicles/month + H₂ fuel-cell + zero tailpipe | ADR-2605252545 + 60-day public review + LANDS.md plant-site allocation |

## Lexicons (9 record types, R0 stubs)

```
com.etzhayyim.sarutahiko.{
  frameAttestation
  powertrainAttestation
  cabBodyAttestation
  marriageAttestation
  paintAttestation
  electricalAttestation
  roadTestRecord
  emissionsAuditRecord
  silenVehicleReview
}
```

Plus terminal `vehicleManufactureRecord` (aggregate, R0 emitted by `vin_attestation_binder` cell as derived record).

## Integration

- **Operator-side counterpart**: wadachi (轍, ADR-2605242000) — same vehicle, operator vs manufacturer split
- **Sibling actors**: watatsumi (水), kanayama (金), tatekata (土), yakushi (薬), silicon (半導体), kuni-umi (planet)
- **Upstream supply** (R3): kanayama Wave 1 (Al body coil) + Wave 2 steel (chassis frame) + Wave 3 copper (interconnect)
- **Downstream consumers**: tatekata (material delivery), kanayama (UBC + scrap logistics), yakushi (cold-chain shipment), mitsuho (food distribution), kuni-umi (site logistics)
- **EoL loop**: vehicles back to kanayama Wave 2 for steel recovery, Wave 1 for Al, Wave 3 for copper
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 + human attestation)

## Plant design (full-robotics 4D-BIM + 積込ロボット, ADR-2606013100)

The **factory that builds the Class-8 truck** is designed (design + physics-sim
only; this does NOT activate the cells above — they stay R0 `RuntimeError`):

- Scene SSoT: `70-tools/e7m-sim/scenes/sarutahiko-factory-r0/` — 180m×90m plant,
  7 zones mapping the 5-layer process (受入→L1 フレーム→L2 パワートレイン→L3 キャブBIW
  →塗装→L4 GA結合→L5 EOL→出荷ヤード), 77-part A-L BOM (group F = truck-line
  equipment incl. **F10 積込ロボット**), 25-step 4D 建築手順, 8 構成ロボ, full MEP +
  外構 + 出荷ヤード. Generated: SBOM + 286 kotoba EAVT + 137 robot ops + engineering
  (drainage/避難/消火栓 NG honest findings) + IFC4.
- Production line: `production.edn` — 8-station manufacturing flow (受入→L1→L3→塗装
  →L4→L5→積込); `ProductionLine` simulates **one truck made end-to-end** (body flows
  on physics, cells work it, recoloured at paint, loader ships it).
- Viewer crate: `40-engine/kami-engine/kami-app-sarutahiko-factory/` — **4 WASM
  entries** (完成工場 / 4D建設再生 / **生産ライン** / **積込ロボット**) + browser viewer
  `sarutahiko-factory.htm` (`?mode=live|build|produce|load`). **14 native tests green**
  (incl. end-to-end production + loader pick→carry→settle).

## References

- `/90-docs/adr/2605252500-sarutahiko-heavy-truck-manufacturing-r0.md` — Master ADR
- `/90-docs/adr/2606013100-sarutahiko-truck-factory-full-robotics-and-loader.md` — Plant design ADR
- `/70-tools/e7m-sim/scenes/sarutahiko-factory-r0/README.md` — Plant scene SSoT
- `/20-actors/wadachi/README.md` — Operator-side counterpart
- `/CLAUDE.md` — Religious-corp status table row 52
