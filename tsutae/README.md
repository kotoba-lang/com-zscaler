# tsutae (伝え) — Handheld Communication Device Tier-B Actor

**DID**: `did:web:etzhayyim.com:tsutae`
**Namespace**: `com.etzhayyim.tsutae.*`
**ADR**: ADR-2605261300 (R0 master), ADR-2605261315 (R1, reserved), ADR-2605261330 (R2, reserved), ADR-2605261345 (R3, reserved)
**Status**: R0 scaffold (2026-05-26) — all 8 cells import-time RuntimeError
**Methodology source**: YouTube `J4GoOScUO5k` — "How Samsung Produces Millions of Expensive Smartphones Inside Massive Factories" (manufacturing methodology adopted; surveillance / addiction / IP locking / closed bootloader / mass production at any cost explicitly rejected per §2(b) + §2(c) + §2(d) + §2(e))

## Overview

religious-corp first-party **handheld communication device (smartphone-class consumer electronics)** actor. silicon Wave 1 (iwakura SoC) + igata (HPDC Al chassis) + kanayama (EOL Al recovery) と並んで vendor independence chain の **consumer electronics tier** を閉じる集約 actor。

**R0 scope**: Handheld communication device assembly (smartphone-class form factor; ≤200 g + ≤7-inch display + ≤500 cm³ enclosure volume); ARM64 or RISC-V 64-bit SoC; modular battery + display + camera + USB-C + cellular module.

## Why "tsutae" (伝え)

**伝え = transmission / handing-down**。多世代 echo (同じ動詞で「世代を伝える」を意味する) を保ちつつ、communication function の core を表す。silicon Wave 1 iwakura (磐座) / fuigo (鞴) / igata (鋳型) / kanayama (金山) と同じ「物事/工房道具/概念で命名」系譜。

**G3 (modular) + G10 (replaceable Li-ion) = multi-gen reuse possibility 構造的成立** — Apple/Samsung の planned obsolescence と対極の **multi-gen handing-down** が constitutional 成立可能。

## Robotics Classes

| Class | Role | Inherited from | Notes |
|---|---|---|---|
| Otete (precision small-part) | SMT handling, screw fastening, USB-C / SIM tray | kuni-umi | R0 reuse; sub-mm precision R1+ |
| Mimi (AOI + X-ray) | PCB AOI inspection, solder joint X-ray, final visual | kuni-umi | R0 reuse; SMT line AOI R2+ |
| Hitogata (class-A clean) | OLED panel attachment, dust-free assembly | kuni-umi | R2+ (OLED needs class-A clean) |
| Funamori (marine) | display + battery cell international transport | silicon Wave 2 | reuse for raw component logistics |
| **Tedama (手玉)** *(R2+)* | SMT pick-and-place small-component juggler | new, tsutae-native | constitutional design pending R2 ADR |
| **Tezukai (手使い)** *(R2+)* | precise final-assembly handcraftsman (display lamination, screw torque control) | new, tsutae-native | constitutional design pending R2 ADR |

## Pregel Cells (8, all R0 import-time RuntimeError)

| Cell | Murakumo node | Phase | Input → Output |
|---|---|---|---|
| `tsutae_pcb_smt` | naphtali | SMT | componentLotIds + pcbDesignCid → pcbAttestation |
| `tsutae_chassis_assembly` | zebulun | chassis | pcbAttestation + igata.partAttestation + batteryLotId → chassisAttestation |
| `tsutae_display_attachment` | joseph | display | chassisAttestation + displayPanelLotId → displayAttachedRecord (no glue >5g) |
| `tsutae_firmware_load` | joseph | firmware | displayAttached + firmwareImageCid → firmwareAttestation (crypto hash + open-source verify) |
| `tsutae_final_qc` | levi | QC | firmwareAttestation → qcRecord (calibration + functional + RF compliance) |
| `tsutae_packaging` | simeon | packaging | qcRecord → packagedRecord (minimal recyclable + iFixit-class repair manual) |
| `tsutae_device_attestation` | levi | attest | packagedRecord → deviceAttestation (BoM + DID + IPFS pin + repair-history-ready) |
| `tsutae_recycling_intake` | dan | EOL | deviceAttestation (EOL device) → recyclingCertificate + kanayama Al routing |

Linear assembly sequence: SMT → chassis → display → firmware → QC → packaging → device attestation → eventual EOL recycling. Matches igata 8-cell linear pattern.

## Constitutional Gates (G1–G14)

See ADR-2605261300 for full list. **IMMUTABLE** per R0..R3.

Key gates:
- **G1 + G2**: Open hardware (PCB Gerbers / CAD / HDL) + open firmware (no Knox / SEP / Qualcomm BSP)
- **G3**: Repair-rightful — modular screw-fastened (adhesive ≤5g/assembly); replaceable battery + display + camera + USB-C + speaker + SIM + cellular + microphone; iFixit score ≥9/10
- **G6**: **§2(c) anti-surveillance** — cellular module hardware-removable; Wi-Fi-default boot; no IMEI broadcast while cellular disconnected; microphone kill switch mandatory; biometric open-firmware
- **G7**: Firmware binary blob ratio ≤5%; every blob documented + Council-attested waiver
- **G8**: **§2(d) anti-addiction UX** — calm-default OS; notification batch ≥15 min default; no infinite-scroll OS API; no dopamine-loop API; screen-time aggregate self-report only
- **G9**: **§2(b) anti-IP locking** — open SoC mandatory (R1 = StarFive JH7110 / SiFive Unmatched; R2+ = iwakura); Snapdragon / Apple A / closed Helio / closed Exynos **NEVER**
- **G10**: Battery — Li-ion LFP-preferred; user-replaceable hand tools; take-back ≥80% R3 (kanayama); no parts pairing
- **G11**: **§1.13 Wellbecoming** — screen-time self-report only; no per-user behavioral telemetry; sabbath/focus mode OS primitive
- **G12**: Production rate ≤200 devices / 8-hr line R3
- **G14**: Every device = open serial + DID + IPFS-pinned BoM + repair-history blockchain record

## Non-Goals (N1–N12, +2 over igata's 10)

Explicitly excluded from R0–R3:

- **N1**: Snapdragon / Apple A / closed Helio / closed Exynos — **NEVER** (§2(b))
- **N2**: Samsung Knox / Apple SEP / Qualcomm Secure Boot — **NEVER** (§2(b))
- **N3**: Mandatory cellular modem — **NEVER** (§2(c); G6)
- **N4**: Always-on surveillance sensors (mic wake-word, closed-fw fingerprint, always-on camera) — **NEVER** (§2(c))
- **N5**: Addictive UX patterns (infinite-scroll OS-API, dopamine notification, dark pattern API) — **NEVER** (§2(d); G8)
- **N6**: Licensed IP partnerships (Disney/Sanrio/sports) — **NEVER** (§2(b))
- **N7**: Proprietary glue assembly (Apple/Samsung trend; parts pairing) — **NEVER** (G3)
- **N8**: Mass production ≥100,000 units/year — post-R3 + Council Lv6+ supermajority
- **N9**: External commercial sale — SBT↔SBT internal only
- **N10**: Carrier-locked SIM / region-locked firmware — **NEVER** (§2(e))
- **N11**: Adware / bloatware preload / mandatory Google/Apple/Samsung account — **NEVER** (§2(b) + §2(d))
- **N12**: AI / cloud-mandatory features (cloud sync default-on, mandatory OTA, voice-assistant cloud routing) — **NEVER** (§2(c))

## Roadmap

| Phase | Timeline | Scope | Murakumo | Gate |
|---|---|---|---|---|
| **R0** | 2026-05-26 | Scaffold. 8 cells RuntimeError. | No deploy | ✅ Proposed (ADR-2605261300) |
| **R1** | post-Council | Benchtop single-device PoC build (1 unit + manual + Tedama PoC); StarFive JH7110 RISC-V SoC; LCD (not OLED); SBT pilot ≤10 devices | naphtali + zebulun + joseph + levi (4 nodes) | ADR-2605261315 + Council Lv6+ + SME (PCB engineer + RF engineer + OS firmware engineer) + Tedama PoC firmware |
| **R2** | post-R1 | Pilot ≤100 devices/year; iwakura SoC integration R&D (silicon Wave 1 R2 dependency); OLED upgrade + Hitogata class-A; SBT distribution ≤500 | 6 nodes | ADR-2605261330 + 30-day public + iwakura R2 ratify + Hitogata class-A + Tedama + Tezukai onboard |
| **R3** | post-R2 | Community-scale ≤10,000 devices/year; iwakura mass-integration; igata HPDC Al chassis; kanayama EOL loop closure | Full 10-node fleet | ADR-2605261345 + 60-day public + silicon Wave 1 R3 + igata R3 + kanayama R3 multi-domain Council vote + 法務 (電波法 + 個人情報保護法 + GDPR) audit |

## Lexicons (6, R0 stub deferred to R1+)

```
com.etzhayyim.tsutae.{
  pcbAttestation           # SMT PCB lot (component sourcing + AOI pass + ECN traceability)
  chassisAttestation       # chassis (igata Al + battery + speaker + camera + USB-C + cellular module IDs)
  firmwareAttestation      # firmware load (image CID + crypto hash + bootloader status + open-source verify)
  deviceAttestation        # final device (BoM lineage + DID + IPFS-pin + repair-history-ready)
  recyclingCertificate     # EOL take-back (per-device dismantling log + kanayama routing CID)
  silenTsutaeReview        # Council Lv6+ baseline review (R-phase activation gate)
}
```

## Cross-Actor Wire

| Direction | Counter-actor | Supply / role | Wire (R-phase) |
|---|---|---|---|
| Upstream | silicon Wave 1 (iwakura) | SoC supplier | R2+ (silicon Wave 1 R2 readiness) |
| Upstream | igata | HPDC Al chassis | R3 only (igata R3) |
| Downstream | kanayama | EOL Al recovery | R3 only (kanayama R3) |
| Adjacent | ameno (PWA) | tsutae device = baseline platform | R2+ firmware_load includes ameno image option |
| Adjacent | baien | federated training participant | R2+ (per ADR-2605242600) |
| Adjacent | mitate (PWA) | medical advisory app | R1+ firmware_load includes mitate image option |

## Integration

- **Parent actor**: none (peer of igata / silicon Wave 1+2 / kanayama / watatsumi / yakushi)
- **Methodology source**: YouTube `J4GoOScUO5k` Samsung smartphone factory documentary
- **Vendor independence chain link**: 5th link (after silicon Wave 1 + yakushi + watatsumi + kanayama + igata; consumer electronics tier)
- **Witness quorum**: ADR-2605191524 (≥2 robot Ed25519 sigs + human attestation)
- **Encrypted records**: ADR-2605181100 XChaCha20 envelope (G11 + G14)

## References

- `/90-docs/adr/2605261300-tsutae-handheld-communication-tier-b-actor-r0.md` — Full ADR
- `/20-actors/silicon/README.md` — Sibling (iwakura SoC R2+ upstream supplier)
- `/20-actors/igata/README.md` — Sibling (HPDC Al chassis R3 upstream supplier)
- `/20-actors/kanayama/README.md` — Sibling (EOL Al recovery downstream consumer)
- `/20-actors/watatsumi/README.md` — Sibling (YouTube methodology + structural exclusion pattern)
- `/20-actors/makura/README.md` — Sibling (§2(c) consumer good translation precedent — no-electronics → with-electronics)
- `/CHARTER-RIDER.md` — §2(b) IP + §2(c) surveillance + §2(d) addiction + §2(e) repair
- `/CLAUDE.md` — Religious-corp status table row 55
