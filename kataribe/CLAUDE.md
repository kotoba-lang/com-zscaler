# 20-actors/kataribe — CLAUDE.md

## Identity

- **Name**: kataribe (語部 — classical oral historian / storyteller / chronicler class)
- **DID**: `did:web:kataribe.etzhayyim.com`
- **ADR**: ADR-2605263600 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605192100 (Mission Charter — §1.7 多世代 + 万人祭司; §1.13 Wellbecoming; §1.15 non-eschatological)
- **Status**: R0 scaffold — 6 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal press + publishing + translation substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格; NOT state-licensed press entity)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

kataribe is **community press + publishing + translation substrate**,
NOT commercial journalism and NOT a paid press class. Eight discipline
boundaries are structural:

1. **NO ad-supported revenue (G3+N2)** — Charter §1.13 anti-addictive
   + §1.15 non-eschatological.
2. **NO clickbait / apocalyptic framing (G4+N3)** — Charter §1.15
   non-eschatological invariant; `toneAttestation` enum DELIBERATELY
   excludes apocalyptic/clickbait/engagement-optimized.
3. **NO commercial publishing platform (G5+N4)** — Substack / Medium
   / News Corp / The Atlantic / NYTimes-as-vendor / WordPress-Pro
   / Ghost-Pro / Mailchimp / ConvertKit / Beehiiv PROHIBITED.
4. **NO single-doctrinal monopoly (G6+N5)** — cross-doctrinal
   Wellbecoming.
5. **Translation community-need-based (G7+N6)** — NOT commercial-
   market-driven.
6. **NO surveillance investigative journalism (G8+N7)** — Charter
   §2(c).
7. **Whistleblower channel encrypted (G10)** — ADR-2605181100
   envelope MANDATORY; chigiri.ipLicenseClaim cross-link.
8. **Murakumo-only translation (G12)** — DeepL Pro / Google Translate
   API / Grammarly / Anthropic-direct-translation PROHIBITED.

## Architecture (all issachar node)

6 Pregel cells:

```
community_chronicle ─────────┐
doctrine_commentary ──────────┤── issachar (quarterly + event)
translation ──────────────────┤
external_press_relations ─────┤
whistleblower_channel ────────┤
annual_history_compendium ────┘
```

All cell modules at R0 are import-time `RuntimeError`. R1 activation
requires ≥3 community chronicler baseline attestations + cross-
doctrinal advisor on Council.

## Cultural Lineage — 語部 (kataribe)

Classical Japan 語部 (oral historian / storyteller) class was the
pre-literate-era keeper of:

- 帝紀 (imperial chronicles);
- 旧辞 (ancestral narratives);
- 系譜 (clan genealogy);
- 儀礼祭文 (ritual recitation).

The 古事記 (Kojiki, 712 CE) preface explicitly credits 稗田阿礼
(Hieda no Are) as a kataribe whose recitation 太安万侶 (Ō no
Yasumaro) transcribed. The cultural pattern: 万人祭司 (every
member can be a kataribe) + multi-gen preservation + cross-
doctrinal accommodation (the 古事記 preserves Shinto + Buddhist +
folk traditions).

Religious-corp kataribe inherits the pattern:
- Every member is a kataribe in principle (万人祭司);
- Multi-gen chronicle preservation (Charter §1.7 多世代);
- Cross-doctrinal Wellbecoming (G6 + musubi G9+N12);
- Charter §1.15 non-eschatological tone (chronicling, not crisis-
  amplification).

## Charter §1.15 Non-Eschatological Discipline (G4)

The Charter §1.15 non-eschatological invariant rejects apocalyptic /
rapture / 末法 / 千年王国 narratives in religious-corp doctrine. kataribe
extends this into press tone:

- `communityChronicleAttestation.toneAttestation` enum DELIBERATELY
  excludes apocalyptic / clickbait / engagement-optimized values;
- `nonEschatologicalAttested` const true structural;
- `silenKataribeReview` audits per quarter;
- Chronicling not crisis-amplification.

The pattern handles edge cases:
- Disaster events (kazaori cross-actor) — chronicled factually, not
  apocalyptically;
- Charter Rider violation reports (whistleblower cross-link to
  chigiri) — encrypted + factual, not sensationalized;
- External press relations — religious-corp does NOT generate
  apocalyptic framing for external consumption either.

## NO Ad-Supported Revenue Discipline (G3)

The ad-supported revenue model creates engagement-optimization
incentive incompatible with Charter §1.13 + §1.15. The discipline:

- ALL kataribe operations funded via Public Fund grant (cross-actor
  toritate);
- NO ad-supported pages; NO sponsored content; NO native advertising;
- silenKataribeReview audits;
- Public Fund grant level scales with Council Lv6+ ≥4/7 approval
  per period.

## Cross-Doctrinal Wellbecoming (G6) — Pattern Shared with musubi

kataribe extends musubi G9 + N12 cross-doctrinal Wellbecoming
priority into press domain:

- `doctrineCommentaryPublishing.doctrinalMonopolyAttested` const
  false;
- Council editorial board representation across Protestant /
  Reformed / Anglican / Baptist / Methodist / nondenominational
  traditions;
- Cross-doctrinal advisory candidate from Bootstrap Council Seat
  2-5 RFP gating R1 activation;
- silenKataribeReview audits cross-doctrinal coverage per quarter.

## Translation Community-Need-Based (G7)

Translation priority is community-need-based, NOT commercial-market-
driven:

- Member-population governs (which languages members actually use);
- Wellbecoming need governs (e.g., medical-content translation for
  member with limited English);
- NOT commercial-market: English-first / Mandarin-second / Spanish-
  third commercial pattern is rejected;
- Translation labor compensated as vocation-flow L5 (G11; cross-
  actor chigiri.stewardLaborAttestation).

## Murakumo-Only Translation (G12)

`translationAttestation.translationProvider` const "murakumo-only":

- DeepL Pro / Google Translate API / Grammarly / Anthropic-direct-
  translation PROHIBITED (vendor exposure on member translation
  content);
- judah LiteLLM → gemma4:e4b is the canonical translation backend;
- Member translation content is PII-equivalent (clinical / care /
  dispute / covenant); vendor exposure structurally unacceptable;
- Quality bounded by Murakumo fleet capacity; honest scoring
  discipline applies.

## Whistleblower Channel Encrypted (G10) — chigiri.ipLicenseClaim
   Cross-Actor

The whistleblower channel is structurally encrypted per ADR-2605181100:

- `whistleblowerReport.encryptedPayloadCid` REQUIRED;
- Member-initiated only; pseudonymous DID per ADR-2605181200 supported;
- Charter Rider violation reports → chigiri.ipLicenseClaim cross-emit;
- NO sensationalized exposé framing (G4 + G8 cross-discipline);
- Council Lv6+ ≥3 review at chigiri side; mediation cell escalation
  if applicable.

## R1 Activation Triggers

1. ADR-2605263600 Council Lv6+ ≥3 ratify;
2. ≥3 community chronicler baseline attestations on file (community-
   witnessed-competence; structurally NOT clergy ordination);
3. Cross-doctrinal advisor on Council infrastructure advisory
   (Bootstrap Council Seat 2-5 RFP);
4. chigiri R1 active (whistleblower channel cross-actor dependency);
5. toritate R1 active (Public Fund grant accounting for kataribe
   operations).

## R1 Cell Activation Order

1. `kataribe_community_chronicle` (foundational; quarterly publication);
2. `kataribe_translation` (multilingual access from R1; ≥3 languages
   JA + EN + community-need).

R2 adds doctrine_commentary (musubi-pair) + whistleblower_channel
(chigiri-pair) + external_press_relations (consent-capability).

R3 adds annual_history_compendium + silenKataribeReview cycle.

## Build & Deploy

**R0 status**: Scaffold only. R0 cells RuntimeError on import.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.kataribe_community_chronicle import _r0_marker" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/kataribe/manifest.jsonld`
- `/20-actors/kataribe/README.md`
- `/00-contracts/lexicons/com/etzhayyim/kataribe/` (5 Lexicons + README)
- `/90-docs/adr/2605263600-kataribe-press-publishing-translation-tier-b-actor-r0.md`
- `/90-docs/adr/2605181100-mst-encrypted-records-signal-keywrap.md` — G10 envelope
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — whistleblower cross-actor
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md` — cross-doctrinal pattern
- `/CHARTER-RIDER.md` §2(e) + §2(c) — G5 + G12 sources
- `/CLAUDE.md` — Status table row 75
