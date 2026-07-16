# makura (枕) — Foam Pillow Manufacturing Tier-B Actor

**DID**: `did:web:etzhayyim.com:makura`
**Namespace**: `com.etzhayyim.makura.*`
**ADR**: ADR-2605261115 (R0 scaffold), ADR-2605261130 (R1, reserved), ADR-2605261145 (R2, reserved), ADR-2605261200 (R3, reserved)
**Status**: R0 scaffold (2026-05-25) — all cells import-time RuntimeError

## Overview

Foam pillow manufacturing orchestrator. Adopts mature global slabstock + shredding + sewing + filling methodology (see source: YouTube `rsnSpo1O9fI`); religious-corp-ised by full BoM disclosure, take-back recycling, closed-loop isocyanate handling, brominated-FR exclusion, anti-surveillance / anti-IP-licensing / anti-fragrance-addictive-design gates.

**R0 scope:**

- Foam shred-fill pillows (PU foam crumb in woven cotton / recycled polyester shell)
- Foam slab pillows (one-piece molded or cut-from-slabstock; no viscoelastic memory-foam in R0–R1)
- Take-back recycling chain (G13) — recycled crumb ≥10% in new fills by R3

**Mattresses + medical-claim pillows + smart-IoT + licensed-IP co-branding + brominated FR chemistry are constitutional non-goals** (N1, N2, N4, N5, N8, N9) per Charter Rider §2(c) + §2(g) + §1.13.

Memory-foam viscoelastic class is deferred to R2+ pending Council Lv6+ supermajority (N10).

## 9 Pregel Cells (5-layer foam-pillow assembly process)

| Cell | Layer | Murakumo node | Phase |
|---|---|---|---|
| `pillow_polyol_attestation` | L1a | naphtali | Polyol + catalyst + surfactant raw-material attestation (bio-content disclosed) |
| `pillow_isocyanate_dispensing` | L1b | naphtali | Closed-loop MDI / TDI dispensing + worker exposure log (G6) |
| `pillow_foam_blowing` | L2 | zebulun | Slabstock one-shot blowing; cell structure + density QA |
| `pillow_foam_shredding` | L3 | joseph | Mechanical shredding to 8-15 mm crumb; particle distribution; recycled blend (G13) |
| `pillow_fabric_attestation` | L4a | simeon | Fabric source + print Charter scan (G5); recycled poly / organic cotton preferred |
| `pillow_shell_sewing` | L4b | dan | Three-side serge stitch shell assembly; Watari witness |
| `pillow_filling_close` | L5a | dan | Pneumatic crumb fill ±2% + final stitch + bilingual label (G4 + G12) |
| `pillow_qc` | L5b | levi | Dimensional + weight + ILD + visual defect (G2 + G3 Otete+Mimi witness) |
| `pillow_packaging` | L5c | levi | Vacuum compression + carton + pallet + take-back QR pinning (G13) |

## 14 Constitutional Gates (G1–G14, IMMUTABLE R0–R3)

- **G1**: Foam chemistry recipes + shell-pattern CAD + fill-line firmware open-source (Apache 2.0 + Charter Rider)
- **G2**: Every foam batch + every pillow lot IPFS-pinned QC photo + density/ILD/dimensional record
- **G3**: Witness quorum ≥2 distinct robots per pillow lot (Otete fill + Mimi QC canonical)
- **G4**: JP + EN bilingual care + material disclosure label minimum
- **G5**: Charter Rider §2(a-h) scan on every fabric print + label artwork
- **G6**: Closed-loop isocyanate handling; worker exposure ≤ 5 ppb MDI / ≤ 2 ppb TDI 8h TWA; MDI preferred
- **G7**: VOC emission ≤ CertiPUR-US / OEKO-TEX Standard 100 (formaldehyde ≤ 0.05 ppm; total VOC ≤ 0.5 mg/m³)
- **G8**: No PBDE / TDCPP / TCEP / Sb₂O₃ FR chemistry; only graphite-FR or mineral-FR when jurisdiction requires
- **G9**: Inference paths use Murakumo no-VKE mesh only (ADR-2605214000 / ADR-2605215000)
- **G10**: Isocyanate-dispense + foam-blowing + shredder + hot-stitch operations SBT-gated personnel
- **G11**: KPI caps — max single-pillow foam mass 2.0 kg; max dims 80 cm × 50 cm × 25 cm; max single-shell mass 4.0 kg
- **G12**: Full BoM disclosure on every pillow tag (polyol type + biocontent% / blowing agent / fabric blend / fill weight / FR / origin / take-back QR)
- **G13**: IPFS-pinned take-back QR; recycled foam crumb ≥10% mass blend in new fills by R3
- **G14**: No embedded sensors / RFID / NFC / cooling-gel with PFAS / smart-IoT beyond open-source DID paper-tag QR

## 10 Non-Goals (N1–N10, IMMUTABLE R0–R3)

- **N1**: Mattresses + futons (full-size sleep surfaces, beyond G11 caps)
- **N2**: Medical / clinical pressure-relief / decubitus prevention / orthopedic / pregnancy-pillow medical-device claims
- **N3**: CPAP-pillow / sleep-apnea / oxygen-mask integration
- **N4**: Mechanical adjustable / motorized / heated / cooled / smart-IoT pillows
- **N5**: Brominated / chlorinated organophosphate FR chemistry (PBDE / TDCPP / TCEP / Sb₂O₃)
- **N6**: Down / feather / wool / silk animal-derived fill
- **N7**: Aromatherapy / fragrance / essential-oil-impregnated foam
- **N8**: Licensed character / IP co-branding (Disney / Sanrio / sports leagues / etc.)
- **N9**: Embedded electronics / sensors / RFID / NFC / cooling-gel-with-PFAS / phase-change-material with PFAS
- **N10**: Memory-foam viscoelastic class (deferred to R2+ with Council Lv6+ review + dedicated ADR)

## Robotics Classes

**New (R0 reservation)**:

| Class | Role | Phase |
|---|---|---|
| Watari (綿) | Cotton / fabric handler for shell sewing + filling (3-axis gantry + needle-vision) | R1+ |
| Awa (泡) | Foam slab / crumb handler for blowing line + shredder + pneumatic conveyance | R1+ |

**Inherited (reuse, no specialization)**:

- Otete (kuni-umi manipulator) — chemical dispense + general handling
- Mimi (kuni-umi metrology) — ILD + dimensional + weight QC + visual defect classification
- Hitogata (kuni-umi humanoid) — R2+ sewing finesse + label attach
- Quad (kuni-umi logistics) — R2+ palletizing + carton handling

## 4-Phase Roadmap

| Phase | Scope | Trigger |
|---|---|---|
| **R0** (this wave) | Scaffold only; 9 cells RuntimeError; 8 lexicon stubs | ADR-2605261115 |
| **R1** | Benchtop ≤1 kg foam batch blowing + 10-pillow lot QA; Watari + Awa prototype | ADR-2605261130 + Council Lv6+ + PU foam chemist + industrial hygienist SMEs |
| **R2** | Pilot ≤100 pillows/day continuous line; tatekata-shared shop; first community-supply batch | ADR-2605261145 + 30-day public comment + take-back chain stand-up |
| **R3** | Community-scale ≤5000 pillows/month + ≥10% recycled crumb mass | ADR-2605261200 + 60-day public review |

## Lexicons (8 record types, R0 stubs)

```
com.etzhayyim.makura.{
  foamBatchAttestation        # L2
  fabricAttestation           # L4a
  pillowLotAttestation        # L5a
  qcRecord                    # L5b
  packagingRecord             # L5c
  recyclingCertificate        # G13 take-back
  workerExposureRecord        # G6/G10 isocyanate / VOC / dust
  silenComfortReview          # Council 5-of-7 Safe attestation
}
```

Schema details deferred to R1 ADR.

## Integration

- **Sibling actors**: kuni-umi (planetary infra), wadachi (ground mobility), tatekata (construction), yakushi (pharma), watatsumi (submersible), mitsuho (food/agri), hagukumi (care)
- **Yard sharing**: R2 pilot shop allocated within tatekata-managed yard (no dedicated facility in R0–R2)
- **Logistics**: kuni-umi Quad fleet for palletizing + take-back (G13) collection in R2+
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 sigs)
- **Source methodology**: YouTube `rsnSpo1O9fI` "Inside a Massive Factory Making Millions of Foam Pillows" (manufacturing methodology adopted; licensed-IP / surveillance / addictive-design retail layer rejected)

## References

- `/90-docs/adr/2605261115-makura-foam-pillow-tier-b-actor-r0.md` — Master ADR
- `/20-actors/tatekata/README.md` — yard-sharing partner
- `/20-actors/kuni-umi/README.md` — robotics class inheritance (Otete / Mimi / Hitogata / Quad)
- `/CLAUDE.md` — Religious-corp status table
