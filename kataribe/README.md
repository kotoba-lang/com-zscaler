# kataribe (語部) — Non-profit Religious-Corp Press + Publishing + Translation Substrate

**DID**: `did:web:kataribe.etzhayyim.com`
**Namespace**: `com.etzhayyim.kataribe.*`
**ADR**: ADR-2605263600 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 5 Lexicon skeletons
**Cross-actor**: chigiri.ipLicenseClaim (whistleblower) / musubi (cross-doctrinal) / toritate (Public Fund grant) / kazaori (post-emergency chronicle) / manabi (future translated curriculum) / iyashi (clinical-grade review)

## Overview

Religious-corp press + publishing + translation substrate.

- **Community chronicle** — per-community-site quarterly history
- **Doctrine commentary publishing** — cross-doctrinal Wellbecoming
- **Translation** — multilingual access (community-need-based)
- **External press relations** — state press / commercial media interface (consent-capability)
- **Whistleblower channel** — Charter Rider violation reports (encrypted)
- **Annual history compendium** — year-end multi-gen chronicle

Etymology: 語部 (kataribe) = classical Japan oral historian /
storyteller / chronicler class; pre-literate-era keepers of imperial
chronicles + clan genealogy + ritual recitation. Honors 万人祭司 (every
member is a kataribe in principle) + Sola Scriptura (scripture access
through translation) + Charter §1.7 多世代 + §1.15 non-eschatological.

## Identity (CRITICAL — IMMUTABLE)

- **NO ad-supported revenue** (G3 + N2) — Charter §1.13 anti-
  addictive UX + §1.15 non-eschatological; ad-supported creates
  engagement-optimization incentive incompatible with both.
- **NO clickbait / apocalyptic framing** (G4 + N3) — Charter §1.15
  non-eschatological invariant; `communityChronicleAttestation.toneAttestation`
  enum DELIBERATELY excludes apocalyptic/clickbait/engagement-
  optimized; `nonEschatologicalAttested` const true.
- **NO commercial publishing platform** (G5 + N4) — Substack /
  Medium / News Corp / The Atlantic / NYTimes-as-vendor / WordPress-
  Pro / Ghost-Pro / Mailchimp / ConvertKit / Beehiiv PROHIBITED
  per Charter Rider §2(e) + §2(c).
- **NO single-doctrinal monopoly** (G6 + N5) — cross-doctrinal
  Wellbecoming priority per musubi G9 + N12; `doctrineCommentaryPublishing.doctrinalMonopolyAttested`
  const false.
- **Translation community-need-based** (G7 + N6) — NOT commercial-
  market-driven; member-population + Wellbecoming need governs.
- **NO surveillance investigative journalism** (G8 + N7) — Charter
  §2(c) covert-ops avoidance extends to journalism.
- **All publications Apache 2.0 + Charter Rider** (G9) — open-source;
  NO mandatory paywall (N12).
- **Whistleblower channel encrypted** (G10) — ADR-2605181100 envelope
  MANDATORY; chigiri.ipLicenseClaim cross-link.
- **NO payroll for kataribe** (G11) — vocation-flow L5 stewards.
- **Murakumo-only translation** (G12) — DeepL Pro / Google Translate
  API / Grammarly / Anthropic-direct-translation PROHIBITED;
  `translationAttestation.translationProvider` const "murakumo-only".

## 6 Pregel Cells (R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `kataribe_community_chronicle` | issachar | quarterly | events + tone attestation → communityChronicleAttestation |
| `kataribe_doctrine_commentary` | issachar (musubi-paired) | event | draft + cross-doctrinal review → doctrineCommentaryPublishing |
| `kataribe_translation` | issachar | continuous (need-driven) | source + community-need + Murakumo → translationAttestation |
| `kataribe_external_press_relations` | issachar | event | external request + consent-capability + Council ≥3 → response coordination |
| `kataribe_whistleblower_channel` | issachar (chigiri-paired) | event (encrypted) | encrypted report → whistleblowerReport + chigiri.ipLicenseClaim cross-emit |
| `kataribe_annual_history_compendium` | issachar | annual (event) | aggregate chronicles → compendium IPFS pin + Council ≥3 |

## 5 Lexicons under `com.etzhayyim.kataribe.*`

| Lexicon | Purpose |
|---|---|
| `communityChronicleAttestation` | G4 STRUCTURAL: toneAttestation enum excludes apocalyptic/clickbait/engagement-optimized; nonEschatologicalAttested const true |
| `doctrineCommentaryPublishing` | G6 STRUCTURAL: doctrinalMonopolyAttested const false |
| `translationAttestation` | G7+G12 STRUCTURAL: translationProvider const "murakumo-only"; commercialAiTranslationToolUsed const false |
| `whistleblowerReport` | G10 STRUCTURAL: encryptedPayloadCid REQUIRED; chigiri.ipLicenseClaim cross-link |
| `silenKataribeReview` | G3/G4/G5/G6/G7/G8/G11/G12 const-field structural enforcement |

See `/00-contracts/lexicons/com/etzhayyim/kataribe/README.md`.

## Constitutional Gates (G1–G13)

See ADR-2605263600 §5. Key:

- **G3** NO ad-supported revenue
- **G4** NO clickbait / apocalyptic (Charter §1.15)
- **G5** NO commercial publishing platform
- **G6** NO single-doctrinal monopoly
- **G7** Community-need-based translation
- **G10** Whistleblower channel encrypted
- **G12** Murakumo-only translation
- **G13** NO mandatory paywall

## Non-Goals (N1–N12)

See ADR-2605263600 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Council + ≥3 chronicler baseline + cross-doctrinal advisor | 2 core cells + quarterly chronicle ≤5 sites + ≥3 languages |
| **R2** | post-R1 + 30-day public + 5 site attestations | +3 cells (commentary + whistleblower + external press) |
| **R3** | post-R2 + Council Lv7+ + annual cycle | +1 cell (annual_history) + ≥10 languages + cross-religious-corp federation potential |

## Related Files

- `/20-actors/kataribe/manifest.jsonld`
- `/20-actors/kataribe/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/kataribe/` (5 Lexicons + README)
- `/90-docs/adr/2605263600-kataribe-press-publishing-translation-tier-b-actor-r0.md`
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — whistleblower cross-actor
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md` — cross-doctrinal pattern shared
- `/CHARTER-RIDER.md` §2(e) + §2(c) — G5 + G12 sources
- `/CLAUDE.md` — Status table row 75
