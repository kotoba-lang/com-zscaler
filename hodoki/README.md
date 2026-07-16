# hodoki (解き) — End-of-Life Vehicle Disassembly + Materials Recovery Tier-B Actor

**DID**: `did:web:etzhayyim.com:hodoki`
**Namespace**: `com.etzhayyim.hodoki.*`
**ADR**: ADR-2605261215 (R0 scaffold), ADR-2605261230 (R1, reserved), ADR-2605261245 (R2, reserved), ADR-2605261300 (R3, reserved)
**Status**: R0 scaffold (2026-05-26) — all cells import-time RuntimeError

## Overview

End-of-life vehicle (ELV) disassembly + materials recovery orchestrator. Adopts mature OEM-run ELV facility methodology (depollution → parts harvest → shred → sort); religious-corp-ised by mandatory data-wipe, right-to-repair parts catalog, ≥95% material recovery, ≥95% F-gas capture, ≥95% PGM yield, and cross-actor circular feed.

**Source video**: YouTube `whyjio70IUU` — methodology adopted; OEM-monopoly right-to-repair-restricting retail layer rejected per §2(e).

**R0 scope:**

- M1 passenger vehicles (≤2.5t curb weight, ≤8 seats)
- Powertrains: gasoline ICE / diesel ICE / HEV / PHEV / BEV (NMC / LFP / NCA cell-recovery deferred to Wave 2)
- Full chain: intake → depollution → battery handling → parts harvest → catalyst recovery → seat/textile recovery → shred → cross-actor feed

**Military vehicles + aerospace + marine + rail + buses + trucks ≥3.5t + Tesla-class proprietary cell harvest + H2 FCV are constitutional non-goals** (N1–N5, N9, N10).

## 9 Pregel Cells (5-layer ELV processing chain)

| Cell | Layer | Murakumo node | Phase |
|---|---|---|---|
| `elv_intake_audit` | L1a | naphtali | VIN title + prior-owner consent + Charter §2(a-h) scan + initial data-wipe attestation request |
| `elv_depollution` | L1b | joseph | Fluid drain + MAC refrigerant capture + airbag pyrotechnic neutralization + battery disconnect |
| `elv_battery_handling` | L2 | levi | Li-ion SoH classification (≥70% second-life / <70% cell-recycle); lead-acid → kanayama Wave 3 |
| `elv_parts_harvest` | L3a | simeon | Reusable parts identification + condition grading + IPFS-pinned catalog (G12 right-to-repair) |
| `elv_catalyst_recovery` | L3b | levi | Catalytic converter brick removal + PGM smelter handoff + yield audit (G14 ≥95%) |
| `elv_seat_textile_recovery` | L3c | dan | Seat foam + textile recovery; cross-actor feed to **makura** (G13 invariant closure) |
| `elv_body_shred` | L4 | zebulun | Hulk shredder + magnetic + eddy-current + density sort; ferrous + Al + Cu + ASR streams |
| `elv_emissions_audit` | cross-cutting | levi | F-gas continuous + ASR mass + composition + PGM yield audit |
| `elv_provenance_binder` | terminal | judah | Full chain DID anchoring on kotoba-datomic (VIN → parts catalog + material lots + emissions) |

## 14 Constitutional Gates (G1–G14, IMMUTABLE R0–R3)

- **G1**: All depollution + dismantling + shredder firmware + fixture CAD open-source (Apache 2.0 + Charter Rider)
- **G2**: Mass-balance audit ≥98% closure on kotoba-datomic (input curb-weight = parts + material lots + emissions + ASR)
- **G3**: Witness quorum ≥2 distinct robots per critical step (refrigerant capture, battery disconnect, airbag neutralization, PGM removal)
- **G4**: JP + EN bilingual parts catalog + recovery reports + take-back records
- **G5**: Charter Rider §2(a-h) scan per intake (no military / weapon-carrying / covertly-modified)
- **G6**: **F-gas (HFC/HFO refrigerant) capture ≥95% by mass**; no atmospheric venting
- **G7**: Li-ion battery thermal-safety SOP — no puncture, no short-circuit, thermal-runaway-containment enclosure
- **G8**: **MANDATORY ECU + infotainment + telematics data wipe BEFORE disassembly** (cryptographic destruction + physical chip-shred fallback); ≥2 robot witness signatures — **§2(c) anti-surveillance applied to vehicles**
- **G9**: Inference paths use Murakumo no-VKE mesh only
- **G10**: Refrigerant + airbag + Li-ion + PGM + ECU-shred SBT-gated personnel
- **G11**: KPI caps R0–R1 — M1 only, ≤2.5t curb, ≤8 seats; N2/N3/N4 = Wave 2 ADR; H2 FCV = Wave 2 ADR; Tesla cell-recovery = Wave 2 ADR
- **G12**: **RIGHT-TO-REPAIR INVARIANT** — every harvested part has IPFS-pinned catalog entry with VIN provenance + part DID + condition + bilingual description; no proprietary lock-in; no anti-DRM tolerated
- **G13**: ≥95% material recovery by mass (target ≥97% R3); ASR <5% to landfill; cross-actor circular feed mandatory to kanayama + makura + silicon
- **G14**: PGM (Pt/Pd/Rh) yield ≥95% from catalysts; no PGM leak to ASR

## 10 Non-Goals (N1–N10, IMMUTABLE R0–R3)

- **N1**: Military vehicles (tanks, MRAPs, AFVs, military trucks/trailers/motorcycles)
- **N2**: Aerospace vehicles (aircraft, helicopters, drones ≥25 kg MTOW, gliders)
- **N3**: Marine vessels (ships, boats, yachts, jet-skis)
- **N4**: Rail rolling stock (locomotives, freight, passenger coaches, light-rail)
- **N5**: Buses / trucks ≥3.5t (N2/N3 class commercial)
- **N6**: Vehicles with proprietary anti-disassembly DRM without OEM cooperation
- **N7**: VIN-stolen / criminal-provenance vehicles (title invariant)
- **N8**: Part re-sale for re-VIN'ing (chop shop)
- **N9**: Tesla-class proprietary battery cell harvest without OEM cooperation
- **N10**: Hydrogen fuel-cell vehicles (H2 tank disassembly safety class)

## Robotics Classes

**New (R0 reservation)**:

| Class | Role | Phase |
|---|---|---|
| Hagasu (剥がす) | Paint / body-panel stripper (thermal + mechanical detachment) | R1+ |
| Nuku (抜く) | Fluid-drain manipulator with sealed ports for fuel / oil / coolant / refrigerant (G6 ≥95% capture) | R1+ |
| Tokike (解け) | Body-fastener releaser (bolt / clip / rivet unfastener; vision-guided targeting) | R1+ |

**Inherited**:

- kanayama Yokin (molten-metal pour) — R2+ for PGM bench smelter
- kuni-umi Otete — parts handling
- kuni-umi Mimi — SoH measurement + dimensional + condition grading
- kuni-umi Quad — vehicle intake + ASR drum logistics

## 4-Phase Roadmap

| Phase | Scope | Trigger |
|---|---|---|
| **R0** (this wave) | Scaffold only; 9 cells RuntimeError; 8 lexicon stubs | ADR-2605261215 |
| **R1** | Benchtop single-vehicle hand-disassembly + data-wipe demo + Hagasu/Nuku/Tokike prototypes; 5-vehicle trial | ADR-2605261230 + Council Lv6+ + ELV-recycler SME + F-gas refrigerant technician SME |
| **R2** | Pilot ≤10 vehicles/day + cross-actor feed (kanayama + makura + silicon); tatekata-shared yard; parts catalog live | ADR-2605261245 + 30-day public comment |
| **R3** | Community-scale ≤500 vehicles/month + ≥95% material recovery + ≥95% F-gas + ≥95% PGM | ADR-2605261300 + 60-day public review |

## Lexicons (8 record types, R0 stubs)

```
com.etzhayyim.hodoki.{
  elvIntakeRecord                # L1a
  depollutionAttestation         # L1b
  batteryHandlingRecord          # L2
  partsHarvestCatalog            # L3a — RIGHT-TO-REPAIR public catalog (G12)
  catalystRecoveryRecord         # L3b
  shredOutputAttestation         # L4
  dataWipeAttestation            # G8 — ECU/infotainment cryptographic destruction
  silenDeconstructionReview      # Council 5-of-7 Safe attestation
}
```

Schema details deferred to R1 ADR.

## Integration

- **Sibling actors**: kuni-umi (robotics + logistics), wadachi (vehicle build side), kanayama (downstream metals), makura (downstream seat foam circular), silicon (downstream ECU electronics), tatekata (R2 yard share), yakushi, watatsumi, mitsuho, hagukumi
- **Cross-actor circular feed (G13 mandatory)**:
  - Ferrous + non-ferrous Al + Cu wire → kanayama
  - Seat foam + textile → makura (G13 invariant closure)
  - ECU PCB + connectors → silicon Wave 2
  - Li-ion second-life batteries → hikari R2+ stationary storage
  - Lead-acid → kanayama Wave 3
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 sigs)
- **Data wipe**: ADR-2605181100 XChaCha20 envelope for consent records

## References

- `/90-docs/adr/2605261215-hodoki-elv-disassembly-tier-b-actor-r0.md` — Master ADR
- `/20-actors/kanayama/README.md` — downstream metals consumer + G2 pattern inheritance
- `/20-actors/makura/README.md` — downstream seat-foam consumer + G13 invariant closure
- `/20-actors/wadachi/README.md` — vehicle build-side sibling
- `/CLAUDE.md` — Religious-corp status table row 53
