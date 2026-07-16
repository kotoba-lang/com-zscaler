# 20-actors/kokoro — CLAUDE.md

## Identity

- **Name**: kokoro (心 — heart/mind/spirit; cross-tradition Reformed soul + Shinto 心魂 + 仏 心)
- **DID**: `did:web:kokoro.etzhayyim.com`
- **ADR**: ADR-2605263700 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605192100 (Mission Charter — §1.7 多世代 + 反個人主義; §1.13 Wellbecoming + anti-addictive; §2(c) covert-ops avoidance)
- **Status**: R0 scaffold — 6 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal mental health support substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格; NOT state-licensed psych entity)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

kokoro is **community + spiritual + relational mental health support
substrate**, NOT clinical psychiatry and NOT state-licensed psych.
The 9 most-critical discipline boundaries are structural:

1. **NOT clinical psychiatric entity (G3+N9)** — `counselorAttestation.counselorClass`
   const "community-witnessed-competent"; clergy/ordained/state-
   licensed-psych/clinical-psychiatrist DELIBERATELY excluded.
2. **Encrypted envelope MANDATORY + NO video (G4)** — mirrors
   hagukumi G2 + iyashi G2/G3.
3. **NO conversion therapy (G5)** — sexual orientation / gender
   identity / religious belief NEVER targets.
4. **Human-in-loop ALWAYS (G6)** — no AI-only mental health
   intervention.
5. **NO commercial mental health software (G7+N6)** — BetterHelp /
   Talkspace / Cerebral / Modern Health / Lyra / Calm-business /
   Headspace-Enterprise / Spring Health / Brightline / Octave / Two
   Chairs / Charlie Health PROHIBITED.
6. **NO commercial AI therapy chatbot (G8+N7)** — Woebot / Wysa /
   Replika-as-therapy / character.ai / GPT-as-therapy / Claude-as-
   therapy PROHIBITED.
7. **NO mandatory screening (G9)** — opt-in only.
8. **NO surveillance-based mood monitoring (G10)** — Charter §2(c);
   no smart-wearable mood tracking; no facial-emotion-recognition;
   no voice-affect-analysis.
9. **Acute crisis escalation to mitate G5 emergency keyword (G13)**
   — cross-actor existing pattern.

## Architecture (all benjamin node)

6 Pregel cells, all benjamin:

```
peer_support_circle ──────────┐
grief_support (musubi-pair) ───┤
chronic_mental_health_continuity (iyashi-pair) ─┤── benjamin (session + longitudinal + event)
postnatal_mood_screening (iyashi+hagukumi triad) ┤
acute_crisis_escalation (mitate-pair) ─────────┤
counseling_referral (chigiri-pair) ─────────────┘
```

All cell modules at R0 are import-time `RuntimeError`. R1 activation
requires ≥3 counselor baseline attestations + encrypted-record
framework production-deployed + chigiri R1 active.

## NOT-Clinical-Psychiatric-Entity Boundary (G3) — Most Critical Discipline

The kokoro G3 boundary is the strictest discipline boundary across
all Tier-B actors. Mental health is the domain where the temptation
to drift into clinical psychiatry is highest (insurance reimbursement
incentive, professional credentialing prestige, AI-therapy commercial
hype, etc.). G3 structural enforcement prevents drift:

- `counselorAttestation.counselorClass` const "community-witnessed-
  competent";
- Enum DELIBERATELY excludes:
  - "clergy" / "ordained" (musubi G3 inheritance);
  - "state-licensed-psych" / "clinical-psychiatrist";
  - "ordained-pastoral-counselor" (pastoral counseling that imports
    clergy-class assumptions);
- ≥3 prior counselor attestations witness chain (musubi G3 pattern
  shared);
- L5 vocation-flow + employmentRelation const "vocation-flow" (G14;
  cross-actor chigiri.stewardLaborAttestation);
- When clinical psychiatric care is needed: `counseling_referral`
  cell + Public Fund Safe Council Lv6+ ≥4/7 external licensed
  counsel engagement (chigiri G14 UPL-equivalent pattern shared).

## Cultural Lineage — 心 (kokoro) Cross-Tradition

The 心 kanji deliberately avoids Western clinical psychiatry framing:

- **Reformed soul-care** (cure of souls) — pastoral but not clerical;
  community + spiritual; biblical reference per Sola Scriptura;
- **Shinto 心魂** (mind-spirit) — relational; family + ancestral
  context;
- **仏 心** (Buddha-mind / awakened heart) — community sangha
  pattern; meditation + presence;
- **Nondenominational trauma-informed care** — modern integrative
  pattern accommodating across traditions.

G12 cross-doctrinal Wellbecoming priority operationalizes this
inheritance: members from multiple traditions accommodated within
Charter §1.7 + §1.13 boundaries; no single-tradition monopoly
(extends musubi G9+N12 + kataribe G6).

## NO Conversion Therapy (G5) — Constitutional Non-Negotiable

Sexual orientation / gender identity / religious belief are NEVER
targets for "modification" within kokoro. This is constitutional,
not policy:

- `silenKokoroReview.conversionTherapyEventsCount` const 0 structural;
- Conversion therapy / reparative therapy / sexual orientation
  change efforts (SOCE) PROHIBITED;
- Identity-affirming care for sexual orientation / gender identity;
- Religious belief explored / questioned / preserved per member
  conscience (G9 free conscience invariant); never "modified"
  through coercive psychological technique.

This is theologically opinionated — kokoro takes a constitutional
position even when some member traditions might expect otherwise.
Charter Rider §2(f) multi-generational harm + §2(h) Wellbecoming
violation grounds the prohibition.

## Acute Crisis Escalation (G13) — Cross-Actor Pattern Reuse

`acute_crisis_escalation` cell reuses the existing `com.etzhayyim.mitate.emergencyKeyword`
shared lexicon pattern from hagukumi G11 + iyashi G10:

- Suicidal ideation / self-harm imminent threat → mitate G5
  emergency keyword trigger;
- mitate routes to iyashi clinical surge;
- chigiri.disputeMediation cell if Council-level escalation needed
  (rare; only with member consent OR clinical-judgment-via-mitate
  licensed clinician);
- Cross-actor pattern is stable and existing-tested.

## Peer Support Circle Multi-Gen Invariant (G11)

Charter §1.7 反個人主義 + 多世代 inheritance into mental health:

- Peer circles span generations (children + adults + elders
  represented where appropriate);
- Individual-therapy-as-sole-modality framing rejected;
- Community-discerned facilitation (NOT therapist-led only);
- Opt-in (G9) — circles never mandatory.

## Counseling Referral (Counsel via Public Fund) — chigiri G14 UPL-
   Equivalent Pattern Shared

The chigiri G14 UPL-equivalent pattern is inherited:

- chigiri G14 (legal): chigiri does NOT provide legal advice;
  human counsel via Public Fund Council Lv6+ ≥4/7;
- iyashi N9 (clinical): iyashi is NOT a state-licensed clinical
  entity; external licensed clinicians via Public Fund;
- kokoro G3 (mental health): kokoro is NOT a state-licensed
  clinical psych entity; external licensed counselors via Public
  Fund Council Lv6+ ≥4/7;
- Common discipline: religious-corp provides community + procedural
  substrate; state-licensed professional service comes through
  Public Fund external engagement.

## R1 Activation Triggers

1. ADR-2605263700 Council Lv6+ ≥3 ratify;
2. ≥3 counselor baseline attestations on file (community-witnessed-
   competence; NOT clergy ordination; NOT state-licensed-psych
   credentialing per G3);
3. ADR-2605181100 encrypted-record framework production-deployed
   (same R1 gating as hagukumi + iyashi);
4. chigiri R1 active (cross-actor counseling_referral + stewardLaborAttestation
   L5 verification);
5. ≥1 mental-health-experienced advisor on Council (cross-doctrinal
   per G12; Bootstrap Council Seat 2-5 RFP).

## R1 Cell Activation Order

1. `kokoro_peer_support_circle` (foundational; multi-gen circle
   pattern; ≤5 circles at R1);
2. `kokoro_counseling_referral` (chigiri UPL-equivalent pattern;
   bridges to external licensed care via Public Fund).

R2 adds grief_support (musubi-pair) + chronic_mental_health_continuity
(iyashi-pair) + postnatal_mood_screening (iyashi+hagukumi triad).

R3 adds acute_crisis_escalation (mitate-pair) + silenKokoroReview
cycle.

## Build & Deploy

**R0 status**: Scaffold only. R0 cells RuntimeError on import.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.kokoro_peer_support_circle import _r0_marker" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/kokoro/manifest.jsonld`
- `/20-actors/kokoro/README.md`
- `/00-contracts/lexicons/com/etzhayyim/kokoro/` (5 Lexicons + README)
- `/90-docs/adr/2605263700-kokoro-mental-health-tier-b-actor-r0.md`
- `/90-docs/adr/2605181100-mst-encrypted-records-signal-keywrap.md` — G4 envelope
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md` — grief TIGHT pair + G3 musubi pattern shared
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — chronic + postnatal TIGHT pair + G2/G3/G8 patterns shared
- `/90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md` — acute crisis G5 cross-actor
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — post-emergency cross-actor (path-reserved kokoro at R0)
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — G14 UPL-equivalent pattern shared
- `/CHARTER-RIDER.md` §2(e) + §2(c) + §2(f) + §2(h) — G7 + G8 + G10 + G5 sources
- `/CLAUDE.md` — Status table row 76
