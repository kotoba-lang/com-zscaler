# 20-actors/hodoki — CLAUDE.md

## Identity

- **Name**: hodoki (解き — continuative noun of 解く to untie/dissolve/unmake; Buddhist 解脱 release echo)
- **DID**: `did:web:etzhayyim.com:hodoki`
- **ADR**: ADR-2605261215 (R0 scaffold, 2026-05-26)
- **Status**: R0 scaffold — all cells import-time RuntimeError on `.solve()`
- **Parent actor**: etzhayyim religious-corp (ELV disassembly + materials recovery Tier-B)
- **Source methodology**: YouTube `whyjio70IUU` (Audi-class ELV facility); methodology adopted; OEM-monopoly right-to-repair-restricting retail layer rejected

## Architecture

9 Pregel cells implementing 5-layer ELV processing chain (L1 → L2 → L3 → L4 → L5):

```
elv_intake_audit ──> elv_depollution ──> elv_battery_handling
   (L1a, naphtali)     (L1b, joseph)        (L2, levi)
                              │                  │
                              ▼                  ▼
elv_parts_harvest <─ elv_catalyst_recovery <─ elv_seat_textile_recovery
   (L3a, simeon)        (L3b, levi)              (L3c, dan) ──> makura G13
                              │
                              ▼
                       elv_body_shred ──────────> kanayama + silicon
                       (L4, zebulun)
                              │
                              ▼
                       elv_emissions_audit (cross, levi)
                              │
                              ▼
                       elv_provenance_binder (terminal, judah)
```

## Robotics Fleet (R0 reservation only)

| Robot | Class | Status | Function |
|---|---|---|---|
| Hagasu (剥がす) | Paint / body-panel stripper | R1+ | Thermal + mechanical detachment |
| Nuku (抜く) | Fluid-drain manipulator | R1+ | Sealed-port fuel / oil / coolant / refrigerant drain (G6 ≥95% capture) |
| Tokike (解け) | Body-fastener releaser | R1+ | Bolt / clip / rivet unfastener with vision-guided targeting |
| Yokin (kanayama inheritance) | Molten-metal pour | R2+ reuse | PGM bench smelter operations |
| Otete | kuni-umi manipulator | reuse | Parts handling |
| Mimi | kuni-umi metrology | reuse | SoH + dimensional + condition grading |
| Quad | kuni-umi logistics | R2+ reuse | Vehicle intake + ASR drum logistics |

**G1**: All robot firmware open-source (Apache 2.0 + Charter Rider).

## Constitutional Gates (G1–G14)

**IMMUTABLE R0–R3.** Stored in `manifest.jsonld` under `hodoki:constitutionalGates`. Changes require Council Lv6+ supermajority + new ADR.

See `ADR-2605261215` §4 for definitions. Key enforcement + constitutional firsts:

- **G2**: Mass-balance ≥98% on kotoba-datomic (inherits kanayama pattern, novel ELV application)
- **G6**: F-gas (HFC/HFO) capture ≥95%; no atmospheric venting
- **G7**: Li-ion thermal-safety SOP — no puncture / short-circuit / thermal-runaway containment
- **G8** (**CONSTITUTIONAL FIRST**): MANDATORY ECU + infotainment + telematics data wipe BEFORE disassembly. Cryptographic destruction + physical chip-shred fallback. §2(c) anti-surveillance operationalized for vehicles (parallel to makura G14 + N9 for pillows).
- **G12** (**CONSTITUTIONAL FIRST**): Right-to-repair invariant — every harvested part has IPFS-pinned catalog entry with VIN provenance + part DID + condition + bilingual description; §2(e) anti-gatekeeping at vehicle scale
- **G13**: ≥95% material recovery; cross-actor circular feed mandatory (kanayama metals + makura seat foam + silicon ECU + hikari second-life batteries)
- **G14**: PGM (Pt/Pd/Rh) yield ≥95% from catalysts

## Non-Goals (N1–N10)

**EXCLUDED from R0–R3 scope.** Amendment requires Council Lv6+ supermajority + new ADR.

- N1: Military vehicles (§2(a))
- N2: Aerospace vehicles
- N3: Marine vessels (watatsumi-adjacent)
- N4: Rail rolling stock (kuni-umi-adjacent)
- N5: Buses / trucks ≥3.5t (Wave 2 ADR)
- N6: Proprietary anti-DRM circumvention without OEM cooperation (§2(b))
- N7: VIN-stolen / criminal-provenance (title invariant)
- N8: Re-VIN'ing / chop-shop (title invariant + §2(a))
- N9: Tesla-class proprietary cell harvest without OEM cooperation (Wave 2 ADR)
- N10: H2 fuel-cell vehicles (Wave 2 ADR + dedicated safety review)

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.hodoki`

**Records (8 types, R0 stubs)**:

1. `com.etzhayyim.hodoki.elvIntakeRecord` — L1a VIN + title + prior-owner consent + Charter scan
2. `com.etzhayyim.hodoki.depollutionAttestation` — L1b fluid + F-gas + airbag + battery disconnect
3. `com.etzhayyim.hodoki.batteryHandlingRecord` — L2 Li-ion SoH + routing
4. `com.etzhayyim.hodoki.partsHarvestCatalog` — L3a RIGHT-TO-REPAIR public parts catalog (G12)
5. `com.etzhayyim.hodoki.catalystRecoveryRecord` — L3b PGM brick + smelter handoff + yield audit
6. `com.etzhayyim.hodoki.shredOutputAttestation` — L4 ferrous + non-ferrous + Cu + ASR + cross-actor handoff
7. `com.etzhayyim.hodoki.dataWipeAttestation` — G8 ECU/infotainment cryptographic destruction
8. `com.etzhayyim.hodoki.silenDeconstructionReview` — Council 5-of-7 Safe attestation

**Deferred to R1+**: Full lexicon schema definitions. R0 ships stub JSON with `id` + `defs.main.type=record` only.

## Pregel Cells (Detailed)

### elv_intake_audit (L1a)
- **Murakumo node**: naphtali
- **Input**: `vehicleArrival` (VIN, title doc, prior-owner consent record, arrival timestamp)
- **Output**: `elvIntakeRecord` + initial `dataWipeAttestation` request
- **Key constraints**: G5 Charter §2(a-h) scan (no military / weapon-carrying); N7 title chain documented; N8 chop-shop refusal

### elv_depollution (L1b)
- **Murakumo node**: joseph
- **Input**: `elvIntakeRecord` + verified data-wipe attestation
- **Output**: `depollutionAttestation`
- **Key constraints**: G6 F-gas ≥95% closed-loop capture; G7 airbag pyrotechnic safe-disposal; G10 SBT-gated personnel; Nuku robot fluid drain

### elv_battery_handling (L2)
- **Murakumo node**: levi
- **Input**: depollution complete; battery removed safely
- **Output**: `batteryHandlingRecord` (routing decision)
- **Key constraints**: G7 thermal-safety; Li-ion SoH ≥70% → hikari second-life; SoH <70% → cell-recycle Wave 2; lead-acid → kanayama Wave 3

### elv_parts_harvest (L3a)
- **Murakumo node**: simeon
- **Input**: depollution + battery handled
- **Output**: `partsHarvestCatalog` (IPFS-pinned, public, bilingual, VIN-provenanced)
- **Key constraints**: **G12 RIGHT-TO-REPAIR INVARIANT**; condition grading (A/B/C/D); part DID issuance

### elv_catalyst_recovery (L3b)
- **Murakumo node**: levi
- **Input**: under-body access; exhaust system isolated
- **Output**: `catalystRecoveryRecord` (PGM yield audit)
- **Key constraints**: G14 ≥95% Pt/Pd/Rh yield; ICP-MS measurement; smelter receipt anchored

### elv_seat_textile_recovery (L3c)
- **Murakumo node**: dan
- **Input**: interior parts isolated
- **Output**: seat foam + textile streams sorted + bilingual labels
- **Key constraints**: **G13 cross-actor invariant closure — seat foam routed to makura** (closes makura G13 take-back ≥10% by R3)

### elv_body_shred (L4)
- **Murakumo node**: zebulun
- **Input**: stripped hulk (post-harvest)
- **Output**: `shredOutputAttestation` (ferrous + non-ferrous + Cu + ASR mass)
- **Key constraints**: G13 ≥95% material recovery; ASR <5% to landfill; magnetic + eddy-current + density sort; cross-actor handoff to kanayama + silicon

### elv_emissions_audit (cross-cutting)
- **Murakumo node**: levi
- **Input**: continuous telemetry from L1b–L4
- **Output**: per-vehicle compliance log
- **Key constraints**: G6 F-gas continuous; G13 ASR mass cap; G14 PGM yield audit

### elv_provenance_binder (terminal)
- **Murakumo node**: judah
- **Input**: all prior records
- **Output**: kotoba-datomic-anchored audit binder (input VIN → parts catalog + material lots + emissions + ASR mass)
- **Key constraints**: G2 mass-balance ≥98% closure attestation

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No physical disassembly. All cells raise `RuntimeError("hodoki R0 scaffold: activate via Council ADR-2605261230 post-ratification")` on `.solve()` call.

**R1 activation trigger**:

1. ADR-2605261230 authored + Council Lv6+ vote
2. Certified ELV-recycler SME onboarded (Council attestation gate)
3. Certified F-gas refrigerant technician SME onboarded (Council attestation gate)
4. Benchtop single-vehicle hand-disassembly + G8 data-wipe demonstrated
5. 5-vehicle trial cohort completed
6. Cell source replaces RuntimeError with LangGraph stub bodies

**Deployment** (R1+):

```bash
cd 20-actors/hodoki
e7m actor deploy .
```

Returns error in R0; waits for R1 ADR activation.

## Testing (R0)

**Smoke test**: Verify all 9 cells import without exception:

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama
python -c "from cells.elv_intake_audit import ElvIntakeAuditCell; assert ElvIntakeAuditCell"
python -c "from cells.elv_depollution import ElvDepollutionCell; assert ElvDepollutionCell"
python -c "from cells.elv_battery_handling import ElvBatteryHandlingCell; assert ElvBatteryHandlingCell"
python -c "from cells.elv_parts_harvest import ElvPartsHarvestCell; assert ElvPartsHarvestCell"
python -c "from cells.elv_catalyst_recovery import ElvCatalystRecoveryCell; assert ElvCatalystRecoveryCell"
python -c "from cells.elv_seat_textile_recovery import ElvSeatTextileRecoveryCell; assert ElvSeatTextileRecoveryCell"
python -c "from cells.elv_body_shred import ElvBodyShredCell; assert ElvBodyShredCell"
python -c "from cells.elv_emissions_audit import ElvEmissionsAuditCell; assert ElvEmissionsAuditCell"
python -c "from cells.elv_provenance_binder import ElvProvenanceBinderCell; assert ElvProvenanceBinderCell"
```

All should pass import; `.solve()` calls should raise `RuntimeError("hodoki R0 scaffold...")`.

## Related Files

- `/20-actors/hodoki/manifest.jsonld` — DID + cell registry + gates + non-goals
- `/90-docs/adr/2605261215-hodoki-elv-disassembly-tier-b-actor-r0.md` — Master ADR
- `/20-actors/kanayama/README.md` — downstream metals consumer + G2 pattern inheritance
- `/20-actors/makura/README.md` — downstream seat-foam consumer + G13 invariant closure
- `/20-actors/wadachi/README.md` — vehicle build-side sibling
- `/CLAUDE.md` — Religious-corp status table row 53
