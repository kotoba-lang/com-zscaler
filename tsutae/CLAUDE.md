# 20-actors/tsutae — CLAUDE.md

## Identity

- **Name**: tsutae (伝え — transmission / handing-down)
- **DID**: `did:web:etzhayyim.com:tsutae`
- **ADR**: ADR-2605261300 (R0 master, 2026-05-26)
- **Status**: R0 scaffold — all 8 cells import-time RuntimeError
- **Sibling Tier-B actors**: igata, silicon Wave 1+2, kanayama, watatsumi, yakushi, makura, wadachi, tatekata (no parent; peer)
- **Methodology source**: YouTube `J4GoOScUO5k` (Samsung smartphone factory documentary). Manufacturing methodology adopted; **surveillance / addiction / IP locking / closed bootloader / mass production at any cost** explicitly rejected per Charter Rider §2(b) + §2(c) + §2(d) + §2(e).

## Architecture

8 Pregel cells in linear assembly sequence (matches physical smartphone production workflow):

```
pcb_smt → chassis_assembly → display_attachment → firmware_load
(naphtali)    (zebulun)           (joseph)            (joseph)
                                                          |
                                                          v
final_qc → packaging → device_attestation → recycling_intake
 (levi)    (simeon)         (levi)              (dan, EOL)
```

Each cell = 1 Pregel graph with super-step semantics (4–5 LangGraph nodes per cell). Cells communicate via lexicon records on MST (`com.etzhayyim.tsutae.*` record types).

## Robotics Fleet

**R0 uses inherited kuni-umi + silicon Wave 2 classes** (no tsutae-specific hardware in R0):

| Robot | Class | Function | Firmware |
|---|---|---|---|
| Otete (precision small-part) | small-component arm | SMT handling, screw fastening, USB-C/SIM tray | `kuni-umi.otete.firmware` (open Rust) |
| Mimi (AOI + X-ray) | optical metrology | PCB AOI, solder X-ray, final visual | `kuni-umi.mimi.firmware` (open) |
| Hitogata (R2+) | class-A clean humanoid | OLED panel attachment, dust-free assembly | deferred to R2 ADR |
| Funamori (marine, R2+) | bulk cargo marine | display + battery cell intl transport | `silicon-supply.funamori.firmware` |
| Tedama (R2+, new) | SMT pick-and-place juggler | tsutae-native | new firmware (R2 ADR designs) |
| Tezukai (R2+, new) | precise final-assembly handcraftsman | tsutae-native | new firmware (R2 ADR designs) |

**CRITICAL**: All firmware open-source (Apache 2.0 + Charter Rider) per G2. No proprietary BSP, no Samsung Knox, no Apple SEP, no Qualcomm closed bootloader.

## Constitutional Gates (G1–G14)

**IMMUTABLE in R0..R3.** Stored in `manifest.jsonld` under `tsutae:constitutionalGates` array. Changes require Council Lv6+ supermajority + new ADR.

See `ADR-2605261300` for full definitions. Key enforcement:

- **G1**: All hardware open-source — PCB Gerbers + chassis CAD (FreeCAD `.fcstd`) + HDL public Apache 2.0 + Charter Rider
- **G2**: All firmware open-source — bootloader (U-Boot/coreboot) + OS kernel (Linux mainline) + userspace (AOSP-derivative or GrapheneOS class); **bootloader unlock = default state**
- **G3**: Repair-rightful — modular screw-fastened; adhesive ≤5g/assembly (target 0g); replaceable battery + display + camera + USB-C + speaker + SIM + cellular + microphone; iFixit score ≥9/10
- **G4**: Witness quorum — `deviceAttestation` signed by ≥2 robot DIDs (Mimi AOI + Otete handling) Ed25519
- **G6**: **§2(c) anti-surveillance** — cellular module hardware-removable (physical screw-mount); Wi-Fi-default boot; IMEI never broadcast while cellular disconnected; **microphone hardware kill switch mandatory**; biometric open-firmware (no SEP/Knox enclave)
- **G7**: Firmware binary blob ratio ≤5% by total firmware mass; every blob = vendor + reason + replacement-effort estimate + Council waiver
- **G8**: **§2(d) anti-addiction UX** — calm-default OS; notification batching ≥15 min default; no infinite-scroll OS primitive; no dopamine-loop API exposed to apps; screen-time aggregate self-report only; no auto-play media on lock screen
- **G9**: **§2(b) anti-IP locking — open SoC mandatory**: R1 = third-party open RISC-V SoC (StarFive JH7110 / SiFive HiFive Unmatched / Allwinner D1); R2+ = iwakura SoC (silicon Wave 1 R2 readiness); **Snapdragon/Apple A/closed Helio/closed Exynos NEVER**
- **G10**: Battery — Li-ion LFP-preferred (safety + recyclability + cycle life); user-replaceable with hand tools; take-back recycling ≥80% by R3 (kanayama integration); **no battery serial-locking (no parts pairing)**
- **G11**: **§1.13 Wellbecoming** — screen-time aggregate self-report only; no per-user behavioral telemetry; OS provides focus/sabbath mode primitive
- **G12**: Production rate ≤200 devices / 8-hr line R3 (anti-mass-production; no Samsung-class scale)
- **G13**: Murakumo mesh 30-day prior notice + 1 km community feedback (RF + supply chain visibility)
- **G14**: Every device = open serial + DID + IPFS-pinned BoM + repair-history-ready blockchain record (per-device DID accepts `repairEvent` records throughout lifecycle)

## Non-Goals (N1–N12, +2 over igata's 10)

**EXCLUDED from R0–R3 scope**:

- N1: Proprietary SoC (Snapdragon / Apple A / closed Helio / closed Exynos) — **NEVER** §2(b)
- N2: Closed bootloader (Samsung Knox / Apple SEP / Qualcomm Secure Boot) — **NEVER** §2(b)
- N3: Mandatory cellular modem — **NEVER** §2(c) (G6 hardware-removable)
- N4: Always-on surveillance sensors (mic wake-word, closed-fw fingerprint, always-on camera) — **NEVER** §2(c)
- N5: Addictive UX patterns (infinite-scroll OS-API, dopamine notification, dark pattern API) — **NEVER** §2(d) (G8)
- N6: Licensed IP partnerships (Disney/Sanrio/sports) — **NEVER** §2(b) (makura N8 parity)
- N7: Proprietary glue assembly (Apple/Samsung trend; pentalobe; parts pairing) — **NEVER** (G3)
- N8: Mass production ≥100,000 units/year — post-R3 + Council Lv6+ supermajority (igata N1 giga press parity)
- N9: External commercial sale — SBT↔SBT internal carve-out only
- N10: Carrier-locked SIM / region-locked firmware — **NEVER** §2(e)
- N11: Adware / bloatware preload / mandatory Google/Apple/Samsung account — **NEVER** §2(b) + §2(d)
- N12: AI / cloud-mandatory features (cloud sync default-on, mandatory OTA, voice-assistant cloud routing) — **NEVER** §2(c)

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.tsutae`

**Records** (6 types):

1. **`com.etzhayyim.tsutae.pcbAttestation`** — SMT PCB lot (component sourcing + AOI pass + ECN traceability + solder profile)
2. **`com.etzhayyim.tsutae.chassisAttestation`** — Chassis assembly with igata Al + battery + speaker + camera module(s) + USB-C + cellular module IDs (per-component DID chain)
3. **`com.etzhayyim.tsutae.firmwareAttestation`** — Firmware load with image CID + crypto hash (SHA-256) + bootloader unlock status + open-source verification chain
4. **`com.etzhayyim.tsutae.deviceAttestation`** — Final device with full BoM lineage CID array + DID + serial + IPFS-pinned photo + repair-history-ready
5. **`com.etzhayyim.tsutae.recyclingCertificate`** — EOL take-back per-device dismantling log + kanayama routing CID + per-material mass balance
6. **`com.etzhayyim.tsutae.silenTsutaeReview`** — Council Lv6+ baseline review (R-phase activation gate; parallel to yakushi `silenPharmaReview` + silicon `silenForceReview` + igata `silenIgataReview`)

**Deferred to R1+**: Full lexicon schema definitions. R0 uses stub placeholders.

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No real device assembly. All 8 cells raise `RuntimeError("tsutae R0 scaffold: activate via Council ADR-2605261315 post-ratification")` on import.

**R1 activation trigger**:
1. ADR-2605261315 authored + Council Lv6+ vote
2. SME registration: PCB engineer DID + RF engineer DID + OS firmware engineer DID
3. Tedama PoC firmware tested in benchtop
4. StarFive JH7110 (or equivalent open RISC-V SoC) BSP open-source review Council-attested
5. Cell source replaces RuntimeError with LangGraph stub bodies

**Deployment**:
```bash
cd 20-actors/tsutae
e7m actor deploy .
```

(Returns error in R0; waits for R1 ADR activation.)

## Testing (R0)

**Smoke test**: Verify that all 8 cells fail import with `RuntimeError("tsutae R0 scaffold-only ...")`:

```bash
cd /tmp
for cell in tsutae_pcb_smt tsutae_chassis_assembly tsutae_display_attachment tsutae_firmware_load tsutae_final_qc tsutae_packaging tsutae_device_attestation tsutae_recycling_intake; do
  PYTHONPATH=/path/to/etzhayyim-root/40-engine/kotoba/crates/kotoba-kotodama/cells python3 -c "import ${cell}.cell" 2>&1 | tail -1
done
```

All 8 should raise `RuntimeError` per Council activation gate (igata + yakushi + silicon Wave 1 parity).

## Cross-Actor Wire (R-phase activation)

| Direction | Counter-actor | Wire | R-phase |
|---|---|---|---|
| Upstream | silicon Wave 1 iwakura | `pcb_smt` ← `chip_manufacturing` (iwakura SoC) | R2+ |
| Upstream | igata | `chassis_assembly` ← `part_attestation` (HPDC Al chassis) | R3 |
| Downstream | kanayama | `recycling_intake` → kanayama `intake_qa` (EOL Al routing) | R3 |
| Adjacent | ameno PWA | `firmware_load` includes ameno baseline image option | R2+ |
| Adjacent | baien federated | tsutae device = baien edge participant | R2+ |
| Adjacent | mitate PWA | `firmware_load` includes mitate baseline image option | R1+ |

R0 = declaration only. Actual lexicon record flow activates at consumer's R-phase gate.

## Related Files

- `/20-actors/tsutae/manifest.jsonld` — DID + cell registry + constitutional gates
- `/90-docs/adr/2605261300-tsutae-handheld-communication-tier-b-actor-r0.md` — Full R0 master ADR
- `/20-actors/silicon/README.md` — Sibling (iwakura SoC R2+ upstream)
- `/20-actors/igata/README.md` — Sibling (HPDC Al chassis R3 upstream)
- `/20-actors/kanayama/README.md` — Sibling (EOL Al recovery downstream)
- `/20-actors/makura/README.md` — Sibling (§2(c) consumer good translation precedent)
- `/CLAUDE.md` — Status table row 55
- `/CHARTER-RIDER.md` — §2(b) IP + §2(c) surveillance + §2(d) addiction + §2(e) repair
