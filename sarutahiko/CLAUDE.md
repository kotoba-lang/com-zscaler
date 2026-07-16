# 20-actors/sarutahiko — CLAUDE.md

## Identity

- **Name**: sarutahiko (猿田彦 / さるたひこ — Shinto kami of roads, crossroads, paths; 道祖神 / dosojin lineage; the kami who guides Ninigi-no-Mikoto in the 天孫降臨 descent)
- **DID**: `did:web:etzhayyim.com:sarutahiko`
- **ADR**: ADR-2605252500 (R0 scaffold, 2026-05-25)
- **Status**: R0 scaffold — all cells import-time RuntimeError on `.solve()`
- **Parent actor**: etzhayyim religious-corp (heavy truck manufacturing Tier-B)
- **Operator-side counterpart**: wadachi (轍, ADR-2605242000)
- **Wave 1 reference**: civilian Class-8 cargo truck (~26-40 t GVWR, 6×2 / 6×4)

## Architecture

9 Pregel cells implementing 5-layer assembly (L1 → L2 → L3 → L4 → L5) + 2 cross-cutting:

```
frame_fabrication ──┐
                    ├─→ final_marriage → paint_finishing → electrical_integration → quality_road_test
powertrain_assembly ┤      (L4)            (L5a)              (L5b)                    (L5c)
                    │     simeon          simeon              levi                     levi
cab_body_forming ───┘                                                                    ↓
       (L3)                                                                              ↓
       zebulun                                                                           ↓
                                                                                         ↓
                          emissions_audit (cross, levi) ←──────────────────────────────┘
                                            ↓
                          vin_attestation_binder (terminal, judah) — kotoba-datomic anchor
```

## Robotics Fleet (R0 reservation only)

| Robot | Class | Status | Function |
|---|---|---|---|
| Kasane (重ね) | Heavy-frame welding manipulator | R1+ reservation | HSLA-590/780 chassis frame MIG/MAG multi-pass |
| Tsutsumi (包み) | Paint booth robot | R2+ reservation | Water-based KTL + base + clear, VOC <100 g/L |
| Akari (灯り) | Electrical integrator | R1+ reservation | Harness routing + ECU flash + diagnostics |
| Norimichi (乗道) | Public-road test driver | R2+ reservation | SAE Level 3 driver-in-seat (wadachi inheritance) |
| Otete-heavy | kuni-umi Otete heavy variant | R1+ reservation | ≥200 kg payload manipulator |
| Mimi-precision | kuni-umi Mimi μm-level | R1+ reservation | Marriage station alignment |
| Migaki | kanayama surface inspector reuse | R2+ reservation | Body-panel surface QA |

**G1 + N8**: All firmware open-source (Apache 2.0 + Charter Rider). No proprietary ECU.

## Constitutional Gates (G1–G14)

**IMMUTABLE R0–R3.** Stored in `manifest.jsonld` under `sarutahiko:constitutionalGates`.

Key enforcement:

- **G1**: ECU + all electrical firmware open-source
- **G2**: Per-VIN manufacturing log kotoba-datomic anchor + open VIN registry
- **G3**: Per-VIN IPFS-pinned photo + video (welding / paint / road test)
- **G4**: Witness quorum ≥2 distinct robots per critical weld + final marriage
- **G7**: **R0/R1 transition: B100 biodiesel + diesel hybrid acceptable. R2+: LFP / H₂ / NH₃ / methanol fuel-cell only.** Pure-fossil phased out at R2.
- **G8**: Emissions ≤ Euro 7 + ポスト新長期 + Bharat Stage VI (R0-R1); R2+ zero tailpipe
- **G12**: GVWR ≤40 t / max speed ≤90 km/h / autonomous ≤ SAE J3016 Level 4 (Level 5 = non-goal)
- **G13**: Per-VIN DID `did:web:etzhayyim.com:sarutahiko:vehicle:<vin>`
- **G14**: EoL recyclability ≥90% by mass (closes loop with kanayama)

## Non-Goals (N1–N12)

**EXCLUDED from R0–R3 scope.**

- N1: Military trucks (4×4/6×6 troop, MRAP, armored)
- N2: Weapons + ammunition transport (ammo, missile, ICBM TEL)
- N3: Riot control / water-cannon / armored police
- N4: Mining haul trucks (coal/iron-ore/bauxite/oil-sand)
- N5: Fossil fuel tankers (bio-fuel + water + food tankers remain in scope)
- N6: Military surveillance (ELINT/SIGINT/military lidar)
- N7: Fully autonomous unmanned military platforms
- N8: Proprietary ECU NDA
- N9: Driver-suppression UX
- N10: Pure-fossil-only at R2+
- N11: Mobile billboard / LED advertising trucks
- N12: For-profit MaaS rental fleets (non-profit member delivery in scope)

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.sarutahiko`

**Records (9 types, R0 stubs)**:

1. `com.etzhayyim.sarutahiko.frameAttestation` — L1
2. `com.etzhayyim.sarutahiko.powertrainAttestation` — L2 (G7 fuel guard)
3. `com.etzhayyim.sarutahiko.cabBodyAttestation` — L3
4. `com.etzhayyim.sarutahiko.marriageAttestation` — L4 (≥2 robot witness)
5. `com.etzhayyim.sarutahiko.paintAttestation` — L5a
6. `com.etzhayyim.sarutahiko.electricalAttestation` — L5b (open-source firmware CID)
7. `com.etzhayyim.sarutahiko.roadTestRecord` — L5c
8. `com.etzhayyim.sarutahiko.emissionsAuditRecord` — cross-cutting
9. `com.etzhayyim.sarutahiko.silenVehicleReview` — Council 5-of-7 Safe

Terminal `vehicleManufactureRecord` (kotoba-datomic-anchored aggregate) emitted by `vin_attestation_binder` cell.

## Pregel Cells (Detailed)

### frame_fabrication (L1)
- **Murakumo node**: naphtali
- **Input**: `steelLot` (HSLA-590/780), `frameSpec` (rails + cross-members)
- **Output**: `frameAttestation`
- **Key constraints**: ladder-frame straightness <1 mm/m, robotic MIG/MAG multi-pass, no proprietary alloy (G6 + N8)

### powertrain_assembly (L2)
- **Murakumo node**: joseph
- **Input**: `engineLot`, `transmissionLot`, `axleLot`
- **Output**: `powertrainAttestation`
- **G7 fuel guard**: R0/R1 B100 biodiesel + diesel hybrid acceptable; R2+ LFP / H₂ / NH₃ / methanol fuel-cell only. Pure-fossil rejected post-R2.

### cab_body_forming (L3)
- **Murakumo node**: zebulun
- **Input**: kanayama Wave 1 Al coil + Wave 2 steel sheet (R2+); external commodity steel acceptable R0/R1
- **Output**: `cabBodyAttestation`
- **Process**: hot stamping → robotic spot welding → leak test

### final_marriage (L4)
- **Murakumo node**: simeon
- **Input**: frame + cab + powertrain attestations
- **Output**: `marriageAttestation`
- **G4 enforcement**: ≥2 robot witness on critical fastener torque (Otete-heavy + Mimi-precision)

### paint_finishing (L5a)
- **Murakumo node**: simeon
- **Input**: `marriageAttestation`
- **Output**: `paintAttestation`
- **G8 enforcement**: VOC <100 g/L (water-based KTL + base + clear, no solvent-based)

### electrical_integration (L5b)
- **Murakumo node**: levi
- **Input**: `paintAttestation` + harness + ECU
- **Output**: `electricalAttestation`
- **G1 enforcement**: ECU firmware CID open-source (Apache 2.0 + Charter Rider), no proprietary NDA (N8)

### quality_road_test (L5c)
- **Murakumo node**: levi
- **Input**: `electricalAttestation` + roller dyno + 50 km public-road test
- **Output**: `roadTestRecord`
- **G12 enforcement**: max speed ≤90 km/h civilian / autonomous ≤ Level 4

### emissions_audit (cross-cutting)
- **Murakumo node**: levi
- **Output**: `emissionsAuditRecord` (Euro 7 / 大気汚染防止法 / Bharat VI continuous compliance)

### vin_attestation_binder (terminal)
- **Murakumo node**: judah
- **Input**: all prior records + VIN
- **Output**: `vehicleManufactureRecord` (G2 + G13 kotoba-datomic anchor with per-VIN DID)

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No physical vehicle manufacturing. All cells raise `RuntimeError("sarutahiko R0 scaffold: activate via Council ADR-2605252515 post-ratification")` on `.solve()`.

**R1 activation trigger**:
1. ADR-2605252515 authored + Council Lv6+ vote
2. Certified automotive engineering SME onboarded (Council attestation gate)
3. B100 biodiesel feedstock Charter Rider §2(g) audit
4. Benchtop ≤2 t cargo van prototype demonstrated
5. Cell source replaces RuntimeError with LangGraph stub bodies

## Testing (R0)

Smoke test: verify all 9 cells import + `.solve()` raises:

```bash
cd 20-actors/sarutahiko
python3 -c "
from cells.frame_fabrication import FrameFabricationCell
from cells.powertrain_assembly import PowertrainAssemblyCell
from cells.cab_body_forming import CabBodyFormingCell
from cells.final_marriage import FinalMarriageCell
from cells.paint_finishing import PaintFinishingCell
from cells.electrical_integration import ElectricalIntegrationCell
from cells.quality_road_test import QualityRoadTestCell
from cells.emissions_audit import EmissionsAuditCell
from cells.vin_attestation_binder import VinAttestationBinderCell
"
```

All should pass import; `.solve()` raises `RuntimeError("sarutahiko R0 scaffold...")`.

**State-machine tests** (stdlib-only, langgraph-free; the R0 `.solve()` gate is
**preserved** — never calls `solve()`, so it does not bypass Council ADR-2605252515):

```bash
cd 20-actors/sarutahiko
python3 cells/test_state_machines.py   # 3 tests — all 9 cell state machines INIT→…→100% + G7 powertrain fuel-guard (accepts clean / rejects pure-fossil)
```

Coverage: all 9 `state_machine.py` at 100%. The `cell.py` Pregel wrappers stay
import-smoke-only (this env's langgraph is broken by a pydantic/pydantic-core
mismatch); they are R1-activated via ADR-2605252515.

## Related Files

- `/20-actors/sarutahiko/manifest.jsonld`
- `/90-docs/adr/2605252500-sarutahiko-heavy-truck-manufacturing-r0.md`
- `/20-actors/wadachi/README.md` — Operator-side counterpart
- `/20-actors/kanayama/README.md` — Upstream Al/steel/Cu supply + EoL loop
- `/CLAUDE.md` — Religious-corp status table row 52
