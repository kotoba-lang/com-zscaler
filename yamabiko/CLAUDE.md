# 20-actors/yamabiko — CLAUDE.md

## Identity

- **Name**: yamabiko (山彦 / やまびこ — Shinto 山の精霊 / こだま伝承; JR 東日本 E2/E5 系新幹線愛称)
- **DID**: `did:web:etzhayyim.com:yamabiko`
- **ADR**: ADR-2605252600 (R0 scaffold, 2026-05-25)
- **Status**: R0 scaffold — all cells import-time RuntimeError on `.solve()`
- **Parent actor**: etzhayyim religious-corp (high-speed rail manufacturing Tier-B)
- **Land-mobility sibling**: sarutahiko (road manufacturer, ADR-2605252500), wadachi (road operator, ADR-2605242000)
- **Wave 1 reference**: civilian Shinkansen-class trainset (~250-320 km/h, 8-16 cars)

## Architecture

9 Pregel cells implementing 5-layer assembly (L1 → L2 → L3 → L4 → L5) + 2 cross-cutting:

```
carbody_fabrication ──┐
                      ├─→ final_assembly → dynamic_test → homologation_binder
bogie_assembly ───────┤      (L5a)           (L5b)           (L5c)
                      │     simeon            dan             judah
interior_hvac ────────┤
       (L3, zebulun)  │
                      │
traction_electrical ──┘
       (L4, levi)
                                            ↓
                  emissions_acoustic_audit (cross, levi)
                                            ↓
                  silenRailReview (governance, judah)
```

## Robotics Fleet (R0 reservation only)

| Robot | Class | Status | Function |
|---|---|---|---|
| Tsugite (継手) | FSW manipulator | R1+ reservation | Al 6N01/A6005C carbody seam welding |
| Wadasa (輪佐) | Wheel set + bogie installer | R1+ reservation | ≥2 t payload bogie marriage |
| Toritsuke (取付) | Interior fitter | R2+ reservation | Seating + HVAC + PIS |
| Pantagora | Pantograph + HV harness | R2+ reservation | 25 kV AC / 1500 V DC routing |
| Otete-heavy | sarutahiko reuse | R1+ reservation | Heavy manipulator |
| Mimi-precision | sarutahiko reuse | R1+ reservation | μm-level alignment |
| Akari | sarutahiko reuse | R2+ reservation | ECU flash + diagnostics |

**G1 + N5**: All firmware open-source (ATP/ATO/traction). No proprietary signalling NDA.

## Constitutional Gates (G1–G14)

**IMMUTABLE R0–R3.** Key enforcement:

- **G1 + N5**: ATP/ATO/traction firmware Apache 2.0 + Charter Rider, no proprietary signalling NDA
- **G2**: Per-trainset kotoba-datomic anchor + open trainset registry
- **G4**: ≥2 robot witness per FSW + bogie marriage
- **G7**: R0/R1 BEMU + H₂ acceptable; **R2+ full electrification + H₂/NH₃ only (diesel phased out)**
- **G8**: ISO 3095 wayside noise + 騒音規制法 + IEC 62236 EMC
- **G12**: ≤320 km/h Wave 1 / ≤450 m trainset / **autonomous ≤ GoA 3** (GoA 4 = N7)
- **G13**: Per-trainset DID `did:web:etzhayyim.com:yamabiko:trainset:<serial>`
- **G14**: EoL recyclability ≥90% (closes loop with kanayama)

## Non-Goals (N1–N12)

- N1: Military trains
- N2: Border / police / riot control rail
- N3: NBC transport
- N4: R2+ diesel locomotives
- N5: Proprietary signalling firmware NDA
- N6: Third-party advertising wraps
- N7: GoA 4 fully autonomous unmanned operation Wave 1
- N8: Mass-surveillance trains
- N9: Passenger surveillance UX
- N10: Luxury first-class-only trains
- N11: National prestige vanity projects
- N12: Proprietary coupling / bogie / gauge

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.yamabiko`

9 records: `carbodyAttestation`, `bogieAttestation`, `interiorAttestation`, `tractionElectricalAttestation`, `finalAssemblyAttestation`, `dynamicTestRecord`, `acousticEmissionsAuditRecord`, `homologationRecord`, `silenRailReview`.

Terminal `trainsetManufactureRecord` (kotoba-datomic-anchored aggregate) emitted by `homologation_binder` cell.

## Testing (R0)

yamabiko is fully ported py→clj (ADR-2605252600 / 2606160842): the canonical impl is
clojure-on-babashka — the 9 cell `state_machine.cljc`, the agent handlers
(`methods/agent.cljc`), and the constitutional-gate suite (`methods/test_charter_gates.cljc`).
The legacy Python agent twin (`py/agent.py` + `py/test_agent.py`) was pruned once the clj
port reached parity.

```bash
cd 20-actors/yamabiko
./run_tests.sh           # all cljc suites green (9 cell state machines + agent + charter-gates)
```

## Related Files

- `/20-actors/yamabiko/manifest.jsonld`
- `/90-docs/adr/2605252600-yamabiko-high-speed-rail-manufacturing-r0.md`
- `/20-actors/sarutahiko/README.md` — Road sibling
- `/20-actors/wadachi/README.md` — Road operator
- `/CLAUDE.md` — Religious-corp status table row 56
