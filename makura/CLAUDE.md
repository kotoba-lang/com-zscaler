# 20-actors/makura — CLAUDE.md

## Identity

- **Name**: makura (枕 — basic domestic comfort article; Wellbecoming sleep/rest foundation per §1.13)
- **DID**: `did:web:etzhayyim.com:makura`
- **ADR**: ADR-2605261115 (R0 scaffold, 2026-05-25)
- **Status**: R0 scaffold — all cells import-time RuntimeError on `.solve()`
- **Parent actor**: etzhayyim religious-corp (foam pillow manufacturing Tier-B)
- **Source methodology**: YouTube `rsnSpo1O9fI` (foam pillow factory tour); methodology adopted, licensed-IP / surveillance / addictive-design retail layer rejected

## Architecture

9 Pregel cells implementing 5-layer foam-pillow assembly (L1 → L2 → L3 → L4 → L5):

```
pillow_polyol_attestation ──┐
                            ├─> pillow_foam_blowing ──> pillow_foam_shredding
pillow_isocyanate_dispensing ┘      (L2, zebulun)         (L3, joseph)
   (L1a/L1b, naphtali)                                          │
                                                                ▼
pillow_fabric_attestation ──> pillow_shell_sewing ──> pillow_filling_close
       (L4a, simeon)             (L4b, dan)              (L5a, dan)
                                                                │
                                                                ▼
                                          pillow_qc ────> pillow_packaging
                                          (L5b, levi)    (L5c, levi)
```

## Robotics Fleet (R0 reservation only)

| Robot | Class | Status | Function |
|---|---|---|---|
| Watari (綿) | Cotton / fabric handler | R1+ reservation | 3-axis gantry + needle-vision for shell sewing + filling witness |
| Awa (泡) | Foam slab / crumb handler | R1+ reservation | Blowing line + shredder + pneumatic crumb conveyance |
| Otete | kuni-umi manipulator | reuse | Chemical dispense + general handling |
| Mimi | kuni-umi metrology | reuse | ILD + dimensional + weight QC + visual defect |
| Hitogata | kuni-umi humanoid | R2+ reuse | Sewing finesse + label attach + clean ops |
| Quad | kuni-umi logistics | R2+ reuse | Palletizing + carton + take-back logistics |

**G1**: All robot firmware open-source (Apache 2.0 + Charter Rider).

## Constitutional Gates (G1–G14)

**IMMUTABLE R0–R3.** Stored in `manifest.jsonld` under `makura:constitutionalGates`. Changes require Council Lv6+ supermajority + new ADR.

See `ADR-2605261115` §4 for definitions. Key enforcement:

- **G1**: All foam chemistry recipes (polyol formulation, catalyst, surfactant) + shell-pattern CAD + fill-line firmware open-source
- **G3**: Witness quorum ≥2 distinct robots per pillow lot (Otete fill + Mimi QC canonical)
- **G6**: Closed-loop isocyanate handling; worker exposure ≤ 5 ppb MDI / ≤ 2 ppb TDI 8h TWA; MDI preferred over TDI
- **G7**: VOC emission ≤ CertiPUR-US / OEKO-TEX Standard 100 equivalent
- **G8**: No PBDE / TDCPP / TCEP / Sb₂O₃ FR chemistry; baseline FR-free
- **G11**: KPI caps — max foam mass 2.0 kg / max dims 80 × 50 × 25 cm / max combined 4.0 kg
- **G12**: Full BoM disclosure on every pillow tag
- **G13**: IPFS-pinned take-back QR; ≥10% recycled crumb mass by R3
- **G14**: No embedded sensors / RFID / NFC / smart-IoT (anti-surveillance applied to consumer goods)

## Non-Goals (N1–N10)

**EXCLUDED from R0–R3 scope.** Amendment requires Council Lv6+ supermajority + new ADR.

- N1: Mattresses + futons (G11 enforces)
- N2: Medical-device-class claims
- N3: CPAP / sleep-apnea / oxygen integration
- N4: Mechanical adjustable / motorized / heated / cooled / smart-IoT
- N5: Brominated / chlorinated organophosphate FR chemistry
- N6: Down / feather / wool / silk animal-derived fill
- N7: Aromatherapy / fragrance / essential-oil-impregnated foam
- N8: Licensed character / IP co-branding
- N9: Embedded electronics / sensors / RFID / NFC / PFAS gels
- N10: Memory-foam viscoelastic (deferred R2+ ADR + Council Lv6+)

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.makura`

**Records (8 types, R0 stubs)**:

1. `com.etzhayyim.makura.foamBatchAttestation` — L2 chemistry + density + cell-structure + VOC test
2. `com.etzhayyim.makura.fabricAttestation` — L4a material disclosure + Charter scan + dye safety
3. `com.etzhayyim.makura.pillowLotAttestation` — L5a fill weight + close + label + lot DID
4. `com.etzhayyim.makura.qcRecord` — L5b dimensional + ILD + visual + reject %
5. `com.etzhayyim.makura.packagingRecord` — L5c vacuum compression + carton + pallet + take-back QR
6. `com.etzhayyim.makura.recyclingCertificate` — G13 take-back chain + recycled crumb % attestation
7. `com.etzhayyim.makura.workerExposureRecord` — G6/G10 isocyanate / VOC / dust exposure log
8. `com.etzhayyim.makura.silenComfortReview` — Council 5-of-7 Safe attestation, all new product classes

**Deferred to R1+**: Full lexicon schema definitions. R0 ships stub JSON with `id` + `defs.main.type=record` only.

## Pregel Cells (Detailed)

### pillow_polyol_attestation (L1a)
- **Murakumo node**: naphtali
- **Input**: `polyolLot` (vendor cert, biocontent%, OH number, viscosity), `catalystLot`, `surfactantLot`
- **Output**: `foamBatchAttestation` (precursor section)
- **Key constraints**: bio-content disclosed (no greenwashing); no Hg / Sn-based catalysts

### pillow_isocyanate_dispensing (L1b)
- **Murakumo node**: naphtali
- **Input**: `isocyanateLot` (MDI preferred; TDI only with Council attestation), `dispenseRequest`
- **Output**: `workerExposureRecord` + `foamBatchAttestation` (isocyanate section)
- **Key constraints**: G6 closed-loop dispensing; worker exposure ≤ 5 ppb MDI / ≤ 2 ppb TDI 8h TWA; G10 SBT-gated personnel

### pillow_foam_blowing (L2)
- **Murakumo node**: zebulun
- **Input**: dispensed polyol + isocyanate streams, blowing-agent (water + CO₂; HFC requires Council attestation)
- **Output**: `foamBatchAttestation` (final)
- **Key constraints**: density 40-60 kg/m³; cell structure 30-80 ppi; VOC ≤ CertiPUR-US / OEKO-TEX (G7); IPFS-pinned batch photo + density measurement (G2)

### pillow_foam_shredding (L3)
- **Murakumo node**: joseph
- **Input**: cured foam slab (≥24 h aging post-blow)
- **Output**: crumb stream (8-15 mm particle distribution attested)
- **Key constraints**: dust collection + worker PPE; recycled crumb ≥10% blend by R3 (G13); no fragrance impregnation (N7)

### pillow_fabric_attestation (L4a)
- **Murakumo node**: simeon
- **Input**: `fabricRoll` (organic cotton / recycled poly preferred), `printArtwork`
- **Output**: `fabricAttestation`
- **Key constraints**: Charter Rider §2(a-h) scan on every print artwork (G5); no licensed IP (N8); dye safety attestation

### pillow_shell_sewing (L4b)
- **Murakumo node**: dan
- **Input**: `fabricAttestation`, shell pattern CAD
- **Output**: empty shell with one open seam
- **Key constraints**: three-side serge stitch density witness; Watari (R1+) robot witness; G3 ≥2 distinct robots if hot-stitch operation

### pillow_filling_close (L5a)
- **Murakumo node**: dan
- **Input**: empty shell + crumb stream
- **Output**: `pillowLotAttestation`
- **Key constraints**: pneumatic fill ±2% of target weight; final stitch + bilingual care label (G4); full BoM disclosure tag (G12); take-back QR (G13)

### pillow_qc (L5b)
- **Murakumo node**: levi
- **Input**: `pillowLotAttestation`
- **Output**: `qcRecord`
- **Key constraints**: dimensional ±5 mm; weight ±2%; ILD spec; visual defect <0.5% reject; Otete + Mimi witness quorum (G3)

### pillow_packaging (L5c)
- **Murakumo node**: levi
- **Input**: `qcRecord` PASS lot
- **Output**: `packagingRecord` + IPFS-pinned take-back chain entry
- **Key constraints**: vacuum compression ≤40% original volume; carton + pallet; take-back QR confirms G13 chain entry

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No physical fabrication. All cells raise `RuntimeError("makura R0 scaffold: activate via Council ADR post-ratification")` on `.solve()` call.

**R1 activation trigger**:

1. ADR-2605261130 authored + Council Lv6+ vote
2. Certified PU foam chemist SME onboarded (Council attestation gate)
3. Certified industrial hygienist SME onboarded (Council attestation gate)
4. Benchtop ≤1 kg foam batch + 10-pillow lot demonstrated
5. Cell source replaces RuntimeError with LangGraph stub bodies

**Deployment** (R1+):

```bash
cd 20-actors/makura
e7m actor deploy .
```

Returns error in R0; waits for R1 ADR activation.

## Testing (R0)

**Smoke test**: Verify all 9 cells import without exception:

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama
python -c "from cells.pillow_polyol_attestation import PillowPolyolAttestationCell; assert PillowPolyolAttestationCell"
python -c "from cells.pillow_isocyanate_dispensing import PillowIsocyanateDispensingCell; assert PillowIsocyanateDispensingCell"
python -c "from cells.pillow_foam_blowing import PillowFoamBlowingCell; assert PillowFoamBlowingCell"
python -c "from cells.pillow_foam_shredding import PillowFoamShreddingCell; assert PillowFoamShreddingCell"
python -c "from cells.pillow_fabric_attestation import PillowFabricAttestationCell; assert PillowFabricAttestationCell"
python -c "from cells.pillow_shell_sewing import PillowShellSewingCell; assert PillowShellSewingCell"
python -c "from cells.pillow_filling_close import PillowFillingCloseCell; assert PillowFillingCloseCell"
python -c "from cells.pillow_qc import PillowQcCell; assert PillowQcCell"
python -c "from cells.pillow_packaging import PillowPackagingCell; assert PillowPackagingCell"
```

All should pass import; `.solve()` calls should raise `RuntimeError("makura R0 scaffold...")`.

## Related Files

- `/20-actors/makura/manifest.jsonld` — DID + cell registry + gates + non-goals
- `/90-docs/adr/2605261115-makura-foam-pillow-tier-b-actor-r0.md` — Master ADR
- `/20-actors/tatekata/README.md` — R2 yard-sharing partner
- `/20-actors/kuni-umi/README.md` — robotics class inheritance
- `/CLAUDE.md` — Religious-corp status table
