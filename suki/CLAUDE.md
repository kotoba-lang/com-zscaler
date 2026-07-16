# 20-actors/suki — CLAUDE.md

## Identity

- **Name**: suki (鋤 — plow; foundational agricultural artisan tool)
- **DID**: `did:web:etzhayyim.com:suki`
- **ADR**: ADR-2605261500 (R0 master, 2026-05-26)
- **Status**: R0 scaffold — all 9 cells import-time RuntimeError
- **Sibling Tier-B actors**: sarutahiko (truck mfg), wadachi (mobility operator), mitsuho (food/ag operator), igata, kanayama, silicon Wave 1+2, tsutae, watatsumi, yakushi, makura, tatekata (peer)
- **Manufacturing-side / operator-side sibling**: suki manufactures tractors; **mitsuho operates them** (mitsuho.harvest_robotics + field_cultivation = operator-side)
- **Methodology source**: YouTube `aSmoLQKdX9E` (European farm tractor factory documentary). Manufacturing methodology adopted; **John Deere-class DRM ECU + dealer-locked diagnostics + patent-locked seed integration + mega-farm consolidation + fossil-fuel-only powertrain + surveillance hardware integration** explicitly rejected.

## Architecture

9 Pregel cells in linear assembly sequence + judah terminal binder:

```
chassis_fabrication → powertrain_assembly → cab_assembly → hitch_pto_assembly
   (naphtali)            (joseph)            (zebulun)         (joseph)
                                                                  |
                                                                  v
paint_finishing → electrical_ecu_load → quality_field_test → emissions_audit
   (simeon)            (levi)               (levi)            (levi)
                                                                  |
                                                                  v
                                                  vehicle_attestation_binder
                                                          (judah, terminal)
```

Sarutahiko 9-cell + judah binder parity. judah anchors per-VIN kotoba-datomic record.

## Robotics Fleet

**R0 = inherited from kuni-umi / kanayama / sarutahiko + 2 new suki-native**:

| Robot | Class | Function | Firmware |
|---|---|---|---|
| Otete-heavy | heavy-component arm ≥200 kg | Frame + powertrain placement | `kuni-umi.otete.firmware` (open Rust) |
| Mimi-precision | μm-level alignment + AOI | Chassis weld + ECU board | `kuni-umi.mimi.firmware` (open) |
| Migaki | coil + body-panel surface inspector | Paint pre-inspection | kanayama reuse |
| Kasane 重ね (R1+) | heavy-frame welding HSLA-590/780 multi-pass | sarutahiko inheritance | Frame welding |
| Tsutsumi 包み (R2+) | water-based KTL paint robot | sarutahiko inheritance | Cab + chassis paint |
| Akari 灯り (R1+) | electrical harness + ECU flash | sarutahiko inheritance | ECU + CAN bus |
| Norikata 乗り方 (R2+, new) | SAE Level 3 driver-in-seat field test driver | suki-native | new firmware (R2 ADR designs); ag-mechanic license-bound |
| Kuwa 鍬 (R2+, new) | precision hitch + PTO assembly | suki-native | new firmware (R2 ADR designs); ISO 730 Cat I/II/III alignment + 540/1000 RPM torque |

**CRITICAL**: All firmware open-source (Apache 2.0 + Charter Rider) per G2 + G9. No John Deere proprietary CAN bus, no Bosch closed BSP.

## Constitutional Gates (G1–G14)

**IMMUTABLE in R0..R3.** Stored in `manifest.jsonld` under `suki:constitutionalGates` array.

See `ADR-2605261500` for full definitions. Key enforcement:

- **G1 + G2**: Open hardware (chassis CAD + 3-point hitch + PTO + hydraulic schematic) + open firmware (ECU + CAN bus + dash UI); **bootloader unlock = default state**
- **G3**: Modular implement attachment — Cat I/II/III 3-point hitch + standard PTO 540/1000 RPM (ISO 730 + ISO 500); no parts pairing
- **G4**: Witness quorum ≥2 robot signers (Mimi precision + Otete handling) per vehicleManufactureRecord
- **G7**: **Fuel transition** R0/R1 B100 biodiesel + diesel hybrid → **R2+ LFP / H₂ / methanol fuel-cell only** (sarutahiko G7 parallel; religious-corp 第 2 actor declared phase-gate fuel transition)
- **G8**: Emissions Stage V / EPA Tier 4 Final / 日本特殊自動車排ガス R0-R1 tailpipe → R2+ zero
- **G9**: **§2(b) anti-IP locking** — open ECU + open CAN bus + open hydraulic control + open implement-detection; ECU re-flash via published JTAG/UART
- **G10**: **Right-to-Repair constitutional invariant** — no software lockouts requiring dealer authorization; replacement parts catalogued openly; ECU re-flash on parts swap not manufacturer-gated; **religious-corp first explicit RTR constitutional first-class invariant** at firmware level. Combined with tsutae G3 (device-level R2R) = **dual-layer R2R constitutional**.
- **G11**: Operator vetting — Adherent SBT + 大型特殊自動車免許 (JP) / ag-machine operator certification + ag-mechanic SME
- **G12**: **KPI cap** — Wave 1 ≤200 hp / road ≤40 km/h / field ≤15 km/h / **autonomous ≤ SAE J3016 Level 3** / max axle load ≤8 t; anti-corporate-ag + multi-gen farmer-land-relationship
- **G13**: Murakumo mesh + LANDS.md land-trust integration + 30-day prior notice + 1 km community feedback
- **G14**: Every tractor = open VIN + per-vehicle DID `did:web:etzhayyim.com:suki:vehicle:<vin>` + IPFS-pinned BoM + **repair-history-ready blockchain record** + EOL recyclability ≥85% by mass (kanayama loop closure)

## Non-Goals (N1–N12, sarutahiko + tsutae density)

**EXCLUDED from R0–R3 scope**:

- N1: Military tractor (artillery prime mover, military landrover-derivative, military engineering, SOF support) — **NEVER** §2(a)
- N2: Riot-control / armored police tractor — **NEVER** §2(a)
- N3: Mega-tractor ≥400 hp — post-R3 + Council Lv6+ supermajority (igata N1 parity)
- N4: **Closed ECU / proprietary CAN bus DRM / dealer-locked diagnostics / John Deere-style software lockout** — **NEVER** §2(b) + §2(e); G9 + G10 invariant
- N5: Patent-locked seed integration (John Deere SeedStar planters DRM-locking seed brand) — **NEVER** §2(b) + §2(e); mitsuho G2 seed sovereignty alignment
- N6: **Fully autonomous tractor SAE J3016 Level 5** (unattended field operation) — **NEVER** (§1.13 Wellbecoming farmer-land-relationship preservation; wadachi G7 echo)
- N7: Mining / construction tractor — post-R3 + Council Lv6+ supermajority (tatekata adjacency; kanayama N1 mining echo)
- N8: Fossil-fuel-only powertrain R2+ — **NEVER** R2+ (G7 fuel sunset)
- N9: External commercial sale — SBT↔SBT internal carve-out only
- N10: Pesticide-spraying integrated tractor — **NEVER** (mitsuho G6 + §2(g))
- N11: **Surveillance hardware integration** (always-on camera, GPS data sale to seed/chemical company, farm telemetry monetization) — **NEVER** §2(c) (tsutae G6 echo)
- N12: Cross-jurisdiction mass production / Walmart-style centralized — **NEVER** (G12 + LANDS.md locality value)

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.suki`

**Records** (9 types):

1. **`com.etzhayyim.suki.chassisAttestation`** — Frame fabrication (HSLA-590/780 + weld inspection + ≥2 robot witness)
2. **`com.etzhayyim.suki.powertrainAttestation`** — Engine + transmission + hydraulic (G7 fuel type R-phase gate)
3. **`com.etzhayyim.suki.cabAttestation`** — Cab body + ROPS/FOPS rollover-protection certification + interior + G14 open serial
4. **`com.etzhayyim.suki.hitchPtoAttestation`** — 3-point hitch Cat I/II/III + PTO 540/1000 RPM (ISO 730 + ISO 500)
5. **`com.etzhayyim.suki.paintAttestation`** — Water-based KTL + VOC + cure profile
6. **`com.etzhayyim.suki.electricalEcuAttestation`** — **G9 open ECU + G10 no DRM** + bootloader unlock default + CAN bus open protocol
7. **`com.etzhayyim.suki.fieldTestRecord`** — Drawbar (ASAE S496) + PTO (ASAE S217) + 3-point lift (ISO 730) test
8. **`com.etzhayyim.suki.emissionsAuditRecord`** — Stage V / EPA Tier 4 Final / 日本特殊自動車; R0-R1 tailpipe → R2+ zero
9. **`com.etzhayyim.suki.silenSukiReview`** — Council Lv6+ baseline review for R-phase activation

Terminal `vehicleManufactureRecord` emitted by `suki_vehicle_attestation_binder` as per-VIN derived record (sarutahiko binder parity).

## Pregel Cells (Detailed)

### suki_chassis_fabrication
- **Murakumo node**: naphtali
- **Input**: `rawSteelLotIds` (HSLA-590/780 + Cu-bearing structural), `chassisDesignCid` (FreeCAD `.fcstd`)
- **Output**: `chassisAttestation`
- **LangGraph nodes** (placeholder R0): verify_steel_provenance / kasane_welding_dispatch / straightness_metrology (< 1 mm/m) / weld_inspection_2robot_witness / emit chassisAttestation

### suki_powertrain_assembly
- **Murakumo node**: joseph
- **Input**: `chassisAttestation` + engine/transmission/hydraulic lot IDs
- **Output**: `powertrainAttestation`
- **G7 invariant**: R0/R1 = B100 biodiesel + diesel hybrid acceptable; R2+ = LFP / H₂ / methanol fuel-cell only
- **LangGraph nodes**: fuel_type_g7_gate / engine_mount / transmission_couple / hydraulic_charge / emit powertrainAttestation

### suki_cab_assembly
- **Murakumo node**: zebulun
- **Input**: `chassisAttestation` + cab body + interior
- **Output**: `cabAttestation` (ROPS/FOPS certified per ISO 5700 + ISO 3471)
- **LangGraph nodes**: rops_fops_certification / cab_drop / interior_install / g14_open_serial_mint / emit cabAttestation

### suki_hitch_pto_assembly
- **Murakumo node**: joseph
- **Input**: `powertrainAttestation` + hitch + PTO
- **Output**: `hitchPtoAttestation`
- **G3 invariant**: ISO 730 Cat I/II/III 3-point hitch + ISO 500 PTO 540/1000 RPM; no DRM signature gate per G9
- **LangGraph nodes**: hitch_category_select / pto_shaft_torque_certify (Kuwa R2+) / implement_detection_open_protocol / emit hitchPtoAttestation

### suki_paint_finishing
- **Murakumo node**: simeon
- **Input**: `cabAttestation` + `hitchPtoAttestation`
- **Output**: `paintAttestation` (water-based KTL VOC <100 g/L)
- **LangGraph nodes**: surface_prep_migaki / ktl_primer / base_clear (Tsutsumi R2+) / cure_profile_log / emit paintAttestation

### suki_electrical_ecu_load
- **Murakumo node**: levi
- **Input**: `paintAttestation` + `ecuFirmwareCid` (open BSP) + `canBusConfigCid`
- **Output**: `electricalEcuAttestation`
- **G9 + G10 invariant**: open ECU + open CAN bus + bootloader unlock default state + no DRM signature on parts pairing
- **LangGraph nodes**: harness_route_akari (R1+) / ecu_flash_open_bsp / can_bus_open_protocol_verify / bootloader_unlock_default_check / emit electricalEcuAttestation

### suki_quality_field_test
- **Murakumo node**: levi
- **Input**: `electricalEcuAttestation`
- **Output**: `fieldTestRecord`
- **Test suite**: Drawbar (ASAE S496), PTO (ASAE S217), 3-point lift (ISO 730), Norikata public-field test driver R2+
- **LangGraph nodes**: roller_dyno / drawbar_test_asae_s496 / pto_test_asae_s217 / lift_test_iso_730 / norikata_field_drive_50km (R2+) / emit fieldTestRecord

### suki_emissions_audit
- **Murakumo node**: levi
- **Input**: `fieldTestRecord`
- **Output**: `emissionsAuditRecord`
- **G8 invariant**: Stage V + EPA Tier 4 Final + 日本特殊自動車 R0-R1 tailpipe; R2+ zero tailpipe
- **LangGraph nodes**: nox_pm_co_hc_measure / certification_per_jurisdiction / emit emissionsAuditRecord

### suki_vehicle_attestation_binder (terminal)
- **Murakumo node**: judah
- **Input**: All upstream records
- **Output**: `vehicleManufactureRecord` (per-VIN final attestation)
- **G14 invariant**: open VIN + per-vehicle DID `did:web:etzhayyim.com:suki:vehicle:<vin>` + IPFS-pinned BoM + repair-history-ready
- **LangGraph nodes**: vin_did_mint / full_bom_ipfs_pin / repair_history_log_seed / lineage_chain_assembly / emit vehicleManufactureRecord

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No live tractor assembly. All 9 cells raise `RuntimeError("suki R0 scaffold: activate via Council ADR-2605261515 post-ratification")` on import.

**R1 activation trigger**:
1. ADR-2605261515 authored + Council Lv6+ vote
2. SME registration: agricultural engineering DID + ECU engineer DID + ag-mechanic SME DID
3. Kuwa + Norikata PoC firmware tested in benchtop
4. Open RISC-V ECU BSP review Council-attested (R1 alternative to commercial Bosch ECU; R2+ iwakura SoC integration)
5. Cell source replaces RuntimeError with LangGraph stub bodies

**Deployment**:
```bash
cd 20-actors/suki
e7m actor deploy .
```

(Returns error in R0; waits for R1 ADR activation.)

## Testing (R0)

**Smoke test**: Verify all 9 cells fail import with `RuntimeError("suki R0 scaffold-only ...")`:

```bash
cd /tmp
for cell in suki_chassis_fabrication suki_powertrain_assembly suki_cab_assembly suki_hitch_pto_assembly suki_paint_finishing suki_electrical_ecu_load suki_quality_field_test suki_emissions_audit suki_vehicle_attestation_binder; do
  PYTHONPATH=/path/to/etzhayyim-root/40-engine/kotoba/crates/kotoba-kotodama/cells python3 -c "import ${cell}.cell" 2>&1 | tail -1
done
```

All 9 should raise `RuntimeError` per Council activation gate.

## Cross-Actor Supply Loop

| Direction | Counter-actor | Wire | R-phase |
|---|---|---|---|
| Upstream | kanayama Wave 1+2+3 | suki.chassis_fabrication ← kanayama Al+steel+Cu | R2+ |
| Upstream | igata | suki.powertrain_assembly ← igata.partAttestation (engine block / transmission housing) | R3 |
| Upstream | silicon Wave 1 (iwakura) | suki.electrical_ecu_load ← silicon.chip_manufacturing (open ECU SoC) | R2+ |
| Downstream | mitsuho (food/agriculture) | mitsuho.harvest_robotics + field_cultivation ← suki.vehicleManufactureRecord | R2+ (cooperative model) |
| Downstream | kanayama (EoL) | kanayama.intake_qa ← suki.recycling_intake (R3 future cell) | R3 |
| Sibling | sarutahiko | Robotics class lineage (Kasane / Tsutsumi / Akari / Norimichi → Norikata) | Robotics reuse only |
| Sibling | tsutae | R2R constitutional first-class precedent (G3 hardware + G10 firmware = dual-layer) | Constitutional pattern only |
| Sibling | wadachi | N6 SAE Level 5 NEVER pattern (G7 echo) | Constitutional pattern only |

R0 = declaration only.

## Related Files

- `/20-actors/suki/manifest.jsonld` — DID + cell registry + constitutional gates
- `/90-docs/adr/2605261500-suki-farm-tractor-tier-b-actor-r0.md` — Full R0 master ADR
- `/20-actors/sarutahiko/README.md` — Sibling (truck mfg + Kasane/Tsutsumi/Akari/Norimichi lineage)
- `/20-actors/mitsuho/README.md` — Sibling (food/ag operator-side; tractor consumer)
- `/20-actors/wadachi/README.md` — Sibling (mobility operator; SAE Level ≤3 invariant precedent)
- `/20-actors/tsutae/README.md` — Sibling (R2R dual-layer: tsutae G3 hardware + suki G10 firmware)
- `/20-actors/kanayama/README.md` — Sibling (Al + steel + Cu supplier + EoL)
- `/20-actors/igata/README.md` — Sibling (HPDC engine block / transmission housing R3)
- `/CLAUDE.md` — Status table row 57
- `/CHARTER-RIDER.md` — §2(b) IP + §2(c) surveillance + §2(e) repair + §2(g) sustainability
