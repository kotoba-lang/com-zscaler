# musubi (結) — Non-profit Religious-Corp Covenant Ceremony Substrate

**DID**: `did:web:musubi.etzhayyim.com`
**Namespace**: `com.etzhayyim.musubi.*`
**ADR**: ADR-2605263400 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 5 Lexicon skeletons
**TIGHT PAIR**: chigiri (chigiri.covenant_ceremony cell explicit cross-actor at R2)

## Overview

Religious-corp covenant ceremony performance substrate. Tight pair of
chigiri (chigiri attests on-chain via covenantAttestation; musubi
performs the ceremony itself). Six ceremony categories:

- **Marriage** ceremony (Charter §1.12 routing-around; NOT state-recognized)
- **Naming** ceremony (Adherent SBT issuance ritual; chigiri.member_onboarding pair)
- **Funeral** 葬送 (chigiri.inheritance + future shidemori memorial pair)
- **Vocation vow** (L5 vocation-flow steward commitment)
- **Rededication** (post-voluntary-withdrawal or post-excommunication cure return)
- **Seasonal communal** (新年 / 祈年 / 収穫 / 鎮魂 / 安息 + Wellbecoming festival cycles)

Etymology: 結 (musubi) = tie / knot / bind / connect; Shinto 産霊
(musubi) = generative-creative force tying threads of life.

## Identity (CRITICAL — IMMUTABLE)

- **NO clergy class** (G3 + N2) — Reformed 万人祭司 invariant per
  Charter §1.7. Officiants are L5 vocation-flow community-witnessed-
  competent stewards, NOT ordained clergy. The `officiantAttestation.officiantClass`
  enum DELIBERATELY excludes "clergy" / "ordained" / "priest" /
  "bishop" / "minister-with-ecclesiastical-authority"; the valid
  value is "community-witnessed-competent".
- **NO mandatory ritual attendance** (G4 + N3) — free conscience
  invariant; member opt-in only; non-participation NEVER grounds for
  membership consequences.
- **NO commercial wedding/funeral industry software** (G6 + N6) —
  Aisle Planner / Honeybook / The Knot / WeddingWire / Zola / SRS
  Computing / Aldor / Wilbert / Frazer Consultants PROHIBITED per
  Charter Rider §2(e) anti-gatekeeping + §2(c) covert-ops vendor
  concern (vendor closed query-tracking on member life-events
  exposes the deepest personal posture).
- **NO bride price / dowry coercion** (G7 + N7) — anti-coercive
  ceremony economy; gifts permitted, coercive transfer prohibited.
- **NO video recording without per-party consent** (G8) — ceremony
  privacy; mirrors hagukumi G2 + iyashi G3.
- **NO sacrament-as-transubstantiation** (G9 + N4) — Sola Scriptura
  + Reformed memorial view; cross-doctrinal Wellbecoming priority
  (N12).
- **Multi-generational invariant** (G10) — Charter §1.7; ceremonies
  prioritize 多世代 inclusion.
- **Cross-actor chigiri.covenantAttestation emit MANDATORY** (G11) —
  marriage / naming / funeral / vocation-vow / rededication cells
  MUST emit chigiri attestation cross-link (NOT seasonal_communal —
  communal, not per-individual).
- **NO payroll for officiants** (G12) — vocation-flow L5 stewards
  (cross-actor chigiri.stewardLaborAttestation + toritate
  enforcement).

## 6 Pregel Cells (R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `musubi_marriage_ceremony` | gad (chigiri-TIGHT-PAIR) | event | plan + consent + officiant + witnesses → ceremonyPerformanceAttestation + chigiri.covenantAttestation cross-emit |
| `musubi_naming_ceremony` | gad (chigiri.member_onboarding-pair) | event | new member / newborn + consent → ceremonyPerformanceAttestation + chigiri cross-emit |
| `musubi_funeral_ceremony` | gad (chigiri.inheritance + shidemori-pair) | event | deceased + plan + witnesses → ceremonyPerformanceAttestation + chigiri.covenantAttestation + chigiri.inheritanceChain cross-emit |
| `musubi_vocation_vow_ceremony` | gad (chigiri.stewardLaborAttestation-pair) | event | L5 candidate + commitment + witnesses → ceremonyPerformanceAttestation + chigiri cross-emit |
| `musubi_rededication_ceremony` | gad | event | returning member + cure attestation + Council ≥3 → ceremonyPerformanceAttestation + chigiri cross-emit |
| `musubi_seasonal_communal_ceremony` | gad | calendar (8-12/year) | seasonal cycle + opt-in attendance → seasonalCeremonyCalendar (no chigiri per-individual emit) |

## 5 Lexicons under `com.etzhayyim.musubi.*`

| Lexicon | Purpose |
|---|---|
| `ceremonyPerformanceAttestation` | Per-ceremony performance; cross-link to chigiri.covenantAttestation CID; multi-gen ratio enforced |
| `officiantAttestation` | G3 STRUCTURAL: officiantClass enum excludes clergy/ordained/priest/bishop; L5 vocation-flow community-witnessed-competent |
| `communityWitnessAttestation` | Per-ceremony witnesses (multi-gen required per G10) |
| `seasonalCeremonyCalendar` | Annual schedule of communal ceremonies; opt-in attendance registry |
| `silenMusubiReview` | Quarterly Council Wellbecoming + multi-gen ratio + Charter §1.13 + anti-coercive-economy audit |

See `/00-contracts/lexicons/com/etzhayyim/musubi/README.md`.

## Constitutional Gates (G1–G13)

See ADR-2605263400 §5. Key:

- **G3** NO clergy class (Reformed 万人祭司)
- **G6** NO commercial wedding/funeral industry software
- **G7** NO bride price / dowry (anti-coercive)
- **G11** Cross-actor chigiri.covenantAttestation emit MANDATORY
- **G12** NO payroll for officiants

## Non-Goals (N1–N12)

See ADR-2605263400 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Council + ≥3 officiant baseline + chigiri R1 active | 2 core cells (marriage + naming) + ≤10 + ≤20/year |
| **R2** | post-R1 + 30-day public + 5 site attestations | +3 cells (funeral + vocation vow + rededication) + ≤50/year per cat |
| **R3** | post-R2 + Council Lv7+ + annual cycle completed | +1 cell (seasonal communal 8-12/year); multi-site scale |

## Related Files

- `/20-actors/musubi/manifest.jsonld`
- `/20-actors/musubi/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/musubi/` (5 Lexicons + README)
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md`
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — TIGHT PAIR
- `/90-docs/adr/2605250200-l5-religious-marriage-cell.md` — existing Pregel-cell reference
- `/CHARTER-RIDER.md` §2(e) + §2(c) + §1.13 + §1.7 — gate sources
- `/CLAUDE.md` — Status table row 73
