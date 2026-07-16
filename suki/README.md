# suki (鋤) — Farm Tractor Manufacturing Tier-B Actor

**DID**: `did:web:etzhayyim.com:suki`
**Namespace**: `com.etzhayyim.suki.*`
**ADR**: ADR-2605261500 (R0 master), ADR-2605261515 (R1, reserved), ADR-2605261530 (R2, reserved), ADR-2605261545 (R3, reserved)
**Status**: R0 scaffold (2026-05-26) — all 9 cells import-time RuntimeError
**Methodology source**: YouTube `aSmoLQKdX9E` — "Inside Ultra-Modern European Factories Building Massive Farm Tractors From Scratch" (manufacturing methodology adopted; **John Deere-class DRM ECU + dealer-locked diagnostics + patent-locked seed integration + mega-farm consolidation + fossil-fuel-only powertrain + surveillance hardware integration all explicitly rejected**)

## Overview

religious-corp first-party **farm tractor manufacturing** Tier-B actor. **Manufacturing-side counterpart of mitsuho operator-side** (ADR-2605261015) — sibling pattern parallel to sarutahiko ↔ wadachi (truck manufacturing ↔ operator). suki = artisan tool that produces agricultural mobility; mitsuho = field operator that uses it.

**R0 scope**: Farm tractor assembly (~50-200 hp Wave 1; community-scale farming target); mega-tractor ≥400 hp deferred to N3 post-R3 Council Lv6+ supermajority.

## Why "suki" (鋤)

**鋤 = plow — foundational agricultural artisan tool**. Multi-gen agricultural craft echo (古事記 から伊勢神宮 御田植祭 まで連続する agricultural tool kami)。silicon Wave 1 iwakura (磐座) / fuigo (鞴) / igata (鋳型) / kanayama (金山) と同じ「物事/工房道具/概念で命名」系譜。

**G10 + N4 = religious-corp first explicit Right-to-Repair constitutional first-class invariant** at firmware level (combined with tsutae G3 device-level = dual-layer R2R).

## Robotics Classes

| Class | Role | Inherited from | Notes |
|---|---|---|---|
| Otete-heavy | Heavy-component handling ≥200 kg | kuni-umi (sarutahiko-heavy) | Frame + powertrain placement |
| Mimi-precision | μm-level alignment + AOI | kuni-umi | Chassis weld + ECU board |
| Migaki | Coil + body-panel surface inspector | kanayama | Paint pre-inspection |
| Kasane 重ね | Heavy-frame welding HSLA-590/780 multi-pass | sarutahiko (R1+ inheritance) | Frame welding |
| Tsutsumi 包み | Paint booth water-based KTL robot | sarutahiko (R2+ inheritance) | Cab + chassis paint |
| Akari 灯り | Electrical harness + ECU flash + diagnostics | sarutahiko (R1+ inheritance) | ECU + CAN bus load |
| **Norikata 乗り方** *(new, suki-native, R2+)* | Public-field test driver SAE Level 3 driver-in-seat | new | Drawbar + PTO + 3-point lift test; ag-mechanic license-bound |
| **Kuwa 鍬** *(new, suki-native, R2+)* | Precision hitch + PTO assembly robot | new | ISO 730 Cat I/II/III + PTO 540/1000 RPM torque control |

## Pregel Cells (9, all R0 import-time RuntimeError)

| Cell | Murakumo node | Phase | Input → Output |
|---|---|---|---|
| `suki_chassis_fabrication` | naphtali | frame | rawSteelLotIds + chassisDesignCid → chassisAttestation (HSLA-590/780) |
| `suki_powertrain_assembly` | joseph | powertrain | chassis + engine/transmission/hydraulic lots → powertrainAttestation (G7 fuel guard) |
| `suki_cab_assembly` | zebulun | cab | chassis + cab body + interior → cabAttestation (ROPS/FOPS certified) |
| `suki_hitch_pto_assembly` | joseph | hitch | powertrain + hitch + PTO → hitchPtoAttestation (Cat I/II/III + 540/1000 RPM) |
| `suki_paint_finishing` | simeon | paint | cab + hitchPto → paintAttestation (water-based KTL VOC <100 g/L) |
| `suki_electrical_ecu_load` | levi | ecu | paint + ecuFirmwareCid → electricalEcuAttestation (**G9 open ECU + G10 no DRM**) |
| `suki_quality_field_test` | levi | test | ecu → fieldTestRecord (drawbar + PTO + 3-point lift; Norikata R2+) |
| `suki_emissions_audit` | levi | emissions | fieldTest → emissionsAuditRecord (Stage V / EPA Tier 4 / 日本特殊自動車) |
| `suki_vehicle_attestation_binder` | judah | attest | emissions → **vehicleManufactureRecord** (per-VIN DID + open VIN + IPFS pin + repair-history-ready) |

Linear sequence with terminal binder on judah (sarutahiko 9-cell + judah binder parity).

## Constitutional Gates (G1–G14)

See ADR-2605261500 for full list. **IMMUTABLE** per R0..R3.

Key gates:
- **G1 + G2**: Open hardware (chassis CAD + 3-point hitch + PTO + hydraulic) + open firmware (ECU + CAN bus + dash UI); **bootloader unlock = default state**
- **G3**: Modular implement attachment — Cat I/II/III 3-point hitch + standard PTO 540/1000 RPM (ISO 730 + ISO 500); no parts pairing
- **G4**: Witness quorum ≥2 robot signers (Mimi precision + Otete handling) per vehicleManufactureRecord
- **G7**: **Fuel transition** R0/R1 = B100 biodiesel + diesel hybrid; **R2+ = LFP / H₂ / methanol fuel-cell only** (sarutahiko G7 parallel)
- **G8**: Emissions Stage V / EPA Tier 4 Final / 日本特殊自動車 R0-R1 tailpipe → R2+ zero
- **G9**: **§2(b) anti-IP locking** — open ECU + open CAN bus + open hydraulic control + open implement-detection; ECU re-flash via published JTAG/UART
- **G10**: **Right-to-Repair constitutional invariant** — no software lockouts requiring dealer authorization; replacement parts catalogued openly; ECU re-flash on parts swap not manufacturer-gated; **religious-corp first explicit RTR constitutional first-class invariant** at firmware level (tsutae G3 = hardware; suki G10 = firmware; dual-layer R2R)
- **G11**: Operator vetting — Adherent SBT + 大型特殊自動車免許 / ag-machine operator certification + ag-mechanic SME
- **G12**: **KPI cap** Wave 1 ≤200 hp / road ≤40 km/h / field ≤15 km/h / **autonomous ≤ SAE J3016 Level 3** / max axle load ≤8 t
- **G14**: Every tractor = open VIN + per-vehicle DID `did:web:etzhayyim.com:suki:vehicle:<vin>` + IPFS-pinned BoM + **repair-history-ready blockchain record** + EOL recyclability ≥85% by mass (kanayama loop)

## Non-Goals (N1–N12, sarutahiko + tsutae density)

Explicitly excluded from R0–R3:

- **N1**: Military tractor (artillery prime mover / military landrover / military engineering / SOF support) — **NEVER** (§2(a))
- **N2**: Riot-control / armored police tractor — **NEVER** (§2(a))
- **N3**: Mega-tractor ≥400 hp (Case IH Steiger 600 / John Deere 9R class) — post-R3 + Council Lv6+ supermajority (igata N1 giga press parity)
- **N4**: **Closed ECU / proprietary CAN bus DRM / dealer-locked diagnostics / John Deere-style software lockout** — **NEVER** (§2(b) + §2(e); G9 + G10)
- **N5**: Patent-locked seed integration (e.g., John Deere SeedStar planters DRM-locking seed brand) — **NEVER** (§2(b) + §2(e); mitsuho G2 seed sovereignty)
- **N6**: **Fully autonomous tractor SAE J3016 Level 5** (unattended field operation) — **NEVER** (§1.13 Wellbecoming farmer-land-relationship; wadachi G7 echo)
- **N7**: Mining / construction tractor (bulldozer derivative / excavator / mining haul) — post-R3 + Council Lv6+ supermajority (tatekata adjacency)
- **N8**: Fossil-fuel-only powertrain R2+ — **NEVER** R2+ (G7 fuel sunset)
- **N9**: External commercial sale — SBT↔SBT internal carve-out only
- **N10**: Pesticide-spraying integrated tractor — **NEVER** (mitsuho G6 + §2(g))
- **N11**: **Surveillance hardware integration** (always-on camera / GPS data sale to seed/chemical company / farm telemetry monetization) — **NEVER** (§2(c) farmer privacy; tsutae G6 echo)
- **N12**: Cross-jurisdiction mass production / Walmart-style centralized mass-distribution — **NEVER** (G12 + LANDS.md locality)

## Roadmap

| Phase | Timeline | Scope | Murakumo | Gate |
|---|---|---|---|---|
| **R0** | 2026-05-26 | Scaffold. 9 cells RuntimeError. | No deploy | ✅ Proposed (ADR-2605261500) |
| **R1** | post-Council | Benchtop 1-tractor prototype ≤50 hp + manual + B100 biodiesel + Kuwa/Norikata PoC | naphtali + joseph + zebulun + levi + judah (5 nodes) | ADR-2605261515 + Council Lv6+ + 3-DID SME (ag eng + ECU eng + ag mechanic) + open RISC-V ECU BSP review |
| **R2** | post-R1 | Pilot ≤10 tractors/year + LFP hybrid 50-150 hp + Kuwa+Norikata field test + 30-day public | 6 nodes (+ simeon paint) | ADR-2605261530 + 30-day public + Kasane/Tsutsumi/Akari sarutahiko inheritance Council attestation + ag-machine operator license verification |
| **R3** | post-R2 | Community-scale ≤100 tractors/year + H₂ fuel-cell + zero tailpipe + 60-day public + LANDS.md + cross-actor mitsuho.harvest_robotics + kanayama EoL loop closure | Full 7-node fleet | ADR-2605261545 + 60-day public + cross-actor mitsuho R3 + kanayama R3 + 法務 (道路運送車両法 + 農業機械化促進法) audit |

## Lexicons (9, R0 stub deferred to R1+)

```
com.etzhayyim.suki.{
  chassisAttestation       # HSLA-590/780 frame + ≥2 robot witness
  powertrainAttestation    # Engine + transmission + hydraulic + G7 fuel type
  cabAttestation           # Cab body + ROPS/FOPS + interior + G14 open serial
  hitchPtoAttestation      # 3-point Cat I/II/III + PTO 540/1000 RPM (ISO standard)
  paintAttestation         # Water-based KTL + VOC + cure
  electricalEcuAttestation # **G9 open ECU + G10 no DRM** + bootloader unlock default
  fieldTestRecord          # Drawbar + PTO + 3-point lift test
  emissionsAuditRecord     # Stage V / EPA Tier 4 / 日本特殊自動車; R2+ zero
  silenSukiReview          # Council Lv6+ baseline review per R-phase
}
```

Terminal `vehicleManufactureRecord` emitted by `suki_vehicle_attestation_binder` (sarutahiko binder parity).

## Cross-Actor Supply Loop

| Direction | Counter-actor | Relationship | Wire (R-phase) |
|---|---|---|---|
| Upstream | kanayama Wave 1+2+3 | Al + steel + Cu supplier | suki.chassis_fabrication ← kanayama Wave 1+2+3 (R2+) |
| Upstream | igata | HPDC engine block / transmission housing | suki.powertrain_assembly ← igata.partAttestation (R3 only) |
| Upstream | silicon Wave 1 (iwakura) | Open ECU SoC | suki.electrical_ecu_load ← silicon.chip_manufacturing (R2+; tsutae G9 parallel) |
| Downstream | mitsuho (food/agriculture) | Operator of suki tractors | mitsuho.harvest_robotics + field_cultivation ← suki.vehicleManufactureRecord (R2+ pilot cooperative model) |
| Downstream | kanayama (EoL) | Al + steel + Cu recovery | kanayama.intake_qa ← suki.recycling_intake (R3 future cell) |
| Robotics | sarutahiko | Kasane / Tsutsumi / Akari / Norimichi → Norikata inheritance | Robotics reuse only |

## Integration

- **Parent actor**: none (peer of sarutahiko / wadachi / igata / kanayama / silicon Wave 1+2 / mitsuho)
- **Methodology source**: YouTube `aSmoLQKdX9E` European farm tractor factory documentary
- **Constitutional first**: Right-to-Repair firmware-level invariant (G10 + N4); dual-layer R2R with tsutae G3 (hardware level)
- **Vendor independence chain**: 6th link (after silicon GPU + yakushi OTC + watatsumi 潜水艇 + kanayama Al + igata HPDC + tsutae handheld + sarutahiko truck + suki tractor; agricultural mobility tier)
- **Sibling pattern**: manufacturing-side (suki) ↔ operator-side (mitsuho.harvest_robotics), parallel to sarutahiko ↔ wadachi (truck)
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 sigs + human attestation)

## References

- `/90-docs/adr/2605261500-suki-farm-tractor-tier-b-actor-r0.md` — Full ADR
- `/20-actors/sarutahiko/README.md` — Sibling (manufacturing-side truck precedent + Kasane/Tsutsumi/Akari robotics lineage)
- `/20-actors/mitsuho/README.md` — Sibling (food/agriculture operator-side; consumer of suki tractors via harvest_robotics)
- `/20-actors/wadachi/README.md` — Sibling (operator-side autonomous-mobility precedent + N6 SAE Level ≤3 invariant)
- `/20-actors/kanayama/README.md` — Sibling (Al + steel + Cu upstream + EoL downstream)
- `/20-actors/igata/README.md` — Sibling (HPDC engine/transmission housing upstream R3)
- `/20-actors/silicon/README.md` — Sibling (iwakura SoC open ECU upstream R2+)
- `/20-actors/tsutae/README.md` — Sibling (R2R constitutional first precedent; tsutae G3 hardware + suki G10 firmware = dual-layer R2R)
- `/CHARTER-RIDER.md` — §2(b) IP + §2(c) surveillance + §2(e) repair + §2(g) sustainability
- `/CLAUDE.md` — Status table row 57
