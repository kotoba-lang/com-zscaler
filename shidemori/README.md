# shidemori (死出守) — Non-profit Religious-Corp Memorial + Cemetery Substrate (FINAL gap-closure)

**DID**: `did:web:shidemori.etzhayyim.com`
**Namespace**: `com.etzhayyim.shidemori.*`
**ADR**: ADR-2605263800 (R0 scaffold; **FINAL gap-closure** of 10-actor 30min-loop wave)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 5 Lexicon skeletons
**Cross-actor**: musubi.funeral_ceremony (TIGHT memorial NFT mint pair) / chigiri.inheritanceChain (TIGHT succession handoff pair) / Land Registry (waqf-equivalent cemetery; mizuho G11 pattern shared) / toritate (cemetery maintenance + external mortuary Public Fund) / kokoro.grief_support (post-funeral grief continuity) / kazaori (mass-fatality memorial; path-reserved shidemori at R0) / kataribe (memorial publication + cross-doctrinal grief literature)

## Overview

Religious-corp memorial + cemetery substrate. FINAL gap-closure
actor; completes the 10-actor 30min-loop wave (audit list:
chigiri/toritate/iyashi/mizuho/kazaori/musubi/wakai/kataribe/kokoro/shidemori).

- **Memorial NFT mint** — per-deceased upon musubi.funeral_ceremony
- **Cemetery Land Registry** — waqf-equivalent inalienability
- **鎮魂 annual remembrance** — Charter §1.7 多世代 cycle
- **Inheritance handoff** — chigiri.inheritanceChain TIGHT pair
- **External mortuary engagement** — state-licensed legal compliance via Public Fund (UPL-equivalent pattern)

Etymology: 死出守 = guardian of the death-journey (死出 = 死出の旅
classical 万葉集 imagery + 守 = keeper). Cross-tradition (Reformed
memorial + Shinto 鎮魂 + 仏 49日 + secular humanist).

## Identity (CRITICAL — IMMUTABLE)

- **Charter §1.15 non-eschatological** (G3) — NO apocalyptic /
  millennial / specific-heaven-hell-mapping; `memorialNftAttestation.afterlifeDoctrineImposed`
  const false (extends kataribe G4 to memorial domain).
- **Cross-doctrinal Wellbecoming priority** (G4) — Christian +
  Buddhist 49日 + Shinto 鎮魂 + nondenominational + secular
  accommodated (musubi G9+N12 + kataribe G6 + kokoro G12 + shidemori
  G4 = **4-actor maturity** in memorial domain).
- **NOT state-licensed mortuary services** (G5) — community-witnessed-
  competent guardians (musubi G3 pattern shared); external licensed
  mortuary via Public Fund Council Lv6+ ≥4/7 (chigiri G14 + iyashi
  N9 + kokoro G3 + shidemori G5 = **4-actor UPL-equivalent pattern
  maturity**).
- **NO commercial memorial software** (G6) — Frazer Consultants /
  Tribute Center / FuneralOne / ASD / SRS Computing / Wilbert /
  Aldor / Adobe Cemetery Mgmt / Cremation Society Cremation Mgmt
  PROHIBITED per Charter Rider §2(e)+§2(c) (extends musubi G6).
- **NO embalming chemicals** (G7) — Charter Rider §2(d) toxic
  chemistry; green burial + cremation only; biodegradable shroud /
  pine casket; NO formaldehyde / methanol / phenol injection.
- **NO surveillance-based mortuary** (G8) — Charter §2(c).
- **Member burial-or-cremation directive** (G9) — free conscience;
  opt-in at onboarding/rededication.
- **Land Trust waqf-equivalent cemetery rights** (G10) — per
  ADR-2605192245; `cemeteryLandAttestation.landRegistryCid` REQUIRED
  + `waqfInalienabilityAttested` const true (mizuho G11 pattern
  shared; cemetery + water = **2-actor waqf-equivalent pattern**).
- **NO payroll for guardians** (G11) — vocation-flow L5.
- **Murakumo-only inference** (G12).
- **chigiri.inheritanceChain emit MANDATORY** (G13) — when deceased
  had Adherent SBT.

## 6 Pregel Cells (R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `shidemori_memorial_nft_mint` | zebulun (musubi-TIGHT) | event | musubi funeral complete → memorialNftAttestation + on-chain mint |
| `shidemori_cemetery_land_registry` | zebulun (Land Registry) | event | new cemetery site → cemeteryLandAttestation (waqf-inalienability) |
| `shidemori_chinkon_annual_remembrance` | zebulun (musubi seasonal-pair) | annual | annual cycle → chinkonRemembranceAttestation |
| `shidemori_inheritance_handoff` | zebulun (chigiri-TIGHT) | event | funeral + SBT burn → chigiri.inheritanceChain cross-emit (G13 MANDATORY) |
| `shidemori_external_mortuary_engagement` | zebulun (toritate-UPL-equivalent) | event | state-compliance need → Public Fund Council Lv6+ ≥4/7 |
| `shidemori_silen_shidemori_review` | zebulun | quarterly | Council Wellbecoming + multi-doctrinal + Land Trust + Charter §1.15 + §2(d) audit |

## 5 Lexicons under `com.etzhayyim.shidemori.*`

| Lexicon | Purpose |
|---|---|
| `memorialNftAttestation` | G3 STRUCTURAL: afterlifeDoctrineImposed const false; musubi funeral + chigiri inheritance cross-link |
| `cemeteryLandAttestation` | G10 STRUCTURAL: landRegistryCid REQUIRED + waqfInalienabilityAttested const true (mizuho G11 pattern shared) |
| `chinkonRemembranceAttestation` | G4 cross-doctrinal accommodation tracking + Charter §1.7 multi-gen cohort mix |
| `externalMortuaryEngagement` | UPL-equivalent pattern (chigiri G14 + iyashi N9 + kokoro G3 + shidemori G5); Public Fund Safe + Council Lv6+ ≥4 |
| `silenShidemoriReview` | G3/G4/G5/G6/G7/G8/G9/G10/G11/G12 const-field structural enforcement |

See `/00-contracts/lexicons/com/etzhayyim/shidemori/README.md`.

## Constitutional Gates (G1–G13)

See ADR-2605263800 §5.

## Non-Goals (N1–N12)

See ADR-2605263800 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 (FINAL gap-closure) | Scaffold (this commit) |
| **R1** | post-Council + ≥3 guardian baseline + ≥1 cemetery + musubi R2 + chigiri R2 | 3 core cells (memorial_nft_mint musubi-TIGHT + cemetery_land_registry + inheritance_handoff chigiri-TIGHT) |
| **R2** | post-R1 + 30-day public + cross-doctrinal advisor + kokoro R2 | +2 cells (chinkon_annual + external_mortuary_engagement) |
| **R3** | post-R2 + Council Lv7+ + annual chinkon cycle | +1 cell silen_shidemori_review + multi-site community-scale + cross-religious-corp federation potential |

## Related Files

- `/20-actors/shidemori/manifest.jsonld`
- `/20-actors/shidemori/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/shidemori/` (5 Lexicons + README)
- `/90-docs/adr/2605263800-shidemori-memorial-cemetery-tier-b-actor-r0.md`
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md` — TIGHT pair
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — TIGHT pair
- `/90-docs/adr/2605192245-etzhayyim-global-land-sovereignty.md` — G10 waqf source
- `/90-docs/adr/2605263100-mizuho-water-sanitation-tier-b-actor-r0.md` — G11 waqf-equivalent sibling
- `/CHARTER-RIDER.md` §2(d) + §2(e) + §2(c) — gate sources
- `/CLAUDE.md` — Status table row 77
