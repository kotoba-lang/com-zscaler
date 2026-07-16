# kokoro (心) — Non-profit Religious-Corp Mental Health Support Substrate

**DID**: `did:web:kokoro.etzhayyim.com`
**Namespace**: `com.etzhayyim.kokoro.*`
**ADR**: ADR-2605263700 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 5 Lexicon skeletons
**Cross-actor**: musubi (grief TIGHT) / iyashi (chronic + postnatal TIGHT) / mitate (acute crisis G5) / hagukumi (multi-gen + vulnerable pop) / chigiri (counselor L5 + UPL-equivalent referral) / kazaori (post-emergency; path-reserved cross-actor at R0) / kataribe (cross-doctrinal content) / yakushi (medication info read-only)

## Overview

Religious-corp mental health support substrate. Community + spiritual
+ relational framing (NOT Western clinical psychiatry framing).

- **Peer support circles** — community-discerned multi-gen
- **Grief support** — post-funeral (musubi TIGHT)
- **Chronic mental health continuity** — iyashi cross-actor TIGHT
- **Postnatal mood screening** — opt-in (iyashi+hagukumi triad)
- **Acute crisis escalation** — mitate G5 emergency keyword
- **Counseling referral** — external licensed counsel via Public Fund (chigiri UPL-equivalent pattern)

Etymology: 心 (kokoro) = heart/mind/spirit; cross-tradition (Reformed
soul + Shinto 心魂 + 仏 心). The kanji predates the Western
psychology/psychiatry distinction and accommodates cross-doctrinal
Wellbecoming priority (G12; musubi G9+N12 + kataribe G6 pattern
shared).

## Identity (CRITICAL — IMMUTABLE)

**CRITICAL boundary**: kokoro is **NOT a clinical psychiatric entity**.

- NOT state-licensed psych;
- Does NOT diagnose (mitate domain);
- Does NOT prescribe (yakushi domain);
- Counselors are L5 vocation-flow community-witnessed-competent
  (musubi G3 pattern shared); NOT clergy / NOT ordained / NOT
  state-licensed-psych;
- When clinical psychiatric care needed, kokoro REFERS to external
  licensed counsel via Public Fund (chigiri G14 UPL-equivalent
  pattern shared);
- AI therapy chatbots PROHIBITED.

## Constitutional Discipline

- **Encrypted envelope MANDATORY + NO video recording** (G4) —
  mirrors hagukumi G2 + iyashi G2/G3; mental health PHI is most
  sensitive observation class.
- **NO conversion therapy** (G5) — sexual orientation / gender
  identity / religious belief NEVER targets for modification
  (extends hagukumi G7).
- **Human-in-loop ALWAYS** (G6) — AI-assist requires synchronous
  counselor sign-off (mirrors iyashi G8).
- **NO commercial mental health software** (G7) — BetterHelp /
  Talkspace / Cerebral / Modern Health / Lyra / Calm-business /
  Headspace-Enterprise / Spring Health / Brightline / Octave / Two
  Chairs / Charlie Health **PROHIBITED** per Charter Rider §2(e)
  + §2(c).
- **NO commercial AI therapy chatbot** (G8) — Woebot / Wysa /
  Replika-as-therapy / character.ai-as-therapy / GPT-as-therapy /
  Anthropic-direct-therapy / Claude-as-therapy **PROHIBITED**.
- **NO mandatory mental health screening** (G9) — free conscience;
  opt-in only (musubi G4 pattern shared).
- **NO surveillance-based mood monitoring** (G10) — Charter §2(c);
  no smart-wearable mood tracking; no facial-emotion-recognition;
  no voice-affect-analysis.
- **Multi-generational peer support** (G11) — Charter §1.7.
- **Cross-doctrinal Wellbecoming priority** (G12) — Reformed soul-
  care + Shinto 心魂 + 仏 心 + nondenominational trauma-informed
  all accommodated.
- **Acute crisis → mitate G5 emergency keyword** (G13) — cross-actor
  existing pattern (hagukumi G11 + iyashi G10 + kokoro G13).
- **NO payroll for counselors** (G14) — vocation-flow L5 stewards.

## 6 Pregel Cells (R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `kokoro_peer_support_circle` | benjamin | session | circle + multi-gen + opt-in → peerSupportCircleAttestation (encrypted) |
| `kokoro_grief_support` | benjamin (musubi-paired) | session | musubi funeral + bereavement → griefSupportAttestation (encrypted) |
| `kokoro_chronic_mental_health_continuity` | benjamin (iyashi-paired) | longitudinal | iyashi chronic + community+spiritual+relational → chronic continuity (encrypted) |
| `kokoro_postnatal_mood_screening` | benjamin (iyashi+hagukumi-paired) | event (opt-in) | maternity + opt-in screening + Murakumo support → screening (encrypted) |
| `kokoro_acute_crisis_escalation` | benjamin (mitate-paired) | event (urgent) | acute detection → mitate G5 + iyashi surge + chigiri.disputeMediation if needed |
| `kokoro_counseling_referral` | benjamin (chigiri-paired) | event | external counselor need → Public Fund Council Lv6+ ≥4/7 + referral record |

## 5 Lexicons under `com.etzhayyim.kokoro.*`

| Lexicon | Purpose |
|---|---|
| `peerSupportCircleAttestation` | G4+G9+G10+G11 structural; encryptedPayloadCid REQUIRED + optInOnly const true + surveillanceBasedMonitoring const false + multi-gen cohort mix |
| `griefSupportAttestation` | G4 STRUCTURAL: encryptedPayloadCid REQUIRED; musubi funeral cross-link |
| `counselorAttestation` | G3+G14 STRUCTURAL: counselorClass const "community-witnessed-competent" (clergy/ordained/state-licensed-psych DELIBERATELY excluded); lLevel const L5 + employmentRelation const vocation-flow; witnessingCounselorAttestations minLength 3 |
| `acuteCrisisEscalationLog` | G13 STRUCTURAL: mitateG5EmergencyKeywordTriggeredCid REQUIRED; severity enum |
| `silenKokoroReview` | G3/G4/G5/G6/G7/G8/G9/G10/G14 const-field structural enforcement (10+ const-field structurals) |

See `/00-contracts/lexicons/com/etzhayyim/kokoro/README.md`.

## Constitutional Gates (G1–G14)

See ADR-2605263700 §5.

## Non-Goals (N1–N12)

See ADR-2605263700 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Council + ≥3 counselor baseline + encrypted-record production + chigiri R1 | 2 core cells (peer_support + counseling_referral) |
| **R2** | post-R1 + 30-day public + 5 site attestations + musubi R2 + iyashi R2 | +3 cells (grief musubi-pair + chronic iyashi-pair + postnatal iyashi+hagukumi triad) |
| **R3** | post-R2 + Council Lv7+ + kazaori R3 + quarterly review cycle | +1 cell (acute_crisis_escalation mitate-pair) + multi-site community-scale |

## Related Files

- `/20-actors/kokoro/manifest.jsonld`
- `/20-actors/kokoro/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/kokoro/` (5 Lexicons + README)
- `/90-docs/adr/2605263700-kokoro-mental-health-tier-b-actor-r0.md`
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md` — grief TIGHT pair
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — chronic + postnatal TIGHT pair
- `/90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md` — acute crisis G5 cross-actor
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — post-emergency cross-actor (path-reserved kokoro at R0)
- `/CHARTER-RIDER.md` §2(e) + §2(c) — G7 + G8 + G10 sources
- `/CLAUDE.md` — Status table row 76
