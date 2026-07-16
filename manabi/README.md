# manabi (学び) — Education Tier-B Actor

**DID**: `did:web:etzhayyim.com:manabi`
**Namespace**: `com.etzhayyim.manabi.*`
**ADR**: ADR-2605261045 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — all cells import-time RuntimeError
**Parent ADR**: ADR-2605261000 (Liberation Ladder — L4 + L5 gates)

## Overview

Education actor — literacy + numeracy + civics/charter + vocational + lifelong inquiry. Open-curriculum, anti-credentialism, anti-addiction UX, multi-generational lifelong delivery.

**R0 scope** excludes formal accreditation, degree issuance, examination-as-gatekeeper, behavioral conditioning, for-profit textbook publishing, proprietary LMS, surveillance edtech, family/cultural-transmission replacement, advertising, political-party content.

## Anti-Credentialism Invariant (§2(e) constitutional)

manabi delivers **knowledge**, not credentials. No degrees. No transcripts. No GPA. Only `skillAttestation` (subject-specific replayable demonstration) used by L5+ adherent productive contribution.

## Anti-Addiction UX Invariant (§2(d) constitutional)

No streaks. No daily-login rewards. No variable-reward schedules. No leaderboards. No badges-as-status. UX is calm-state-default. (Mirrors hagukumi G13 + mitate G11.)

## Robotics Classes

R0–R2: none (content + human tutors).
R3+: Yutori telepresence inheritance from hagukumi. Optional Hitogata humanoid for vocational physical-skill demonstration. Any new manabi-specific robot class requires separate mech-design ADR (hanami ADR-2605260230 precedent).

## Pregel Cells (5, R0)

| Cell | Murakumo node | Phase | Input → Output |
|---|---|---|---|
| `literacy` | levi | reading + writing | learnerDid, languageTarget → learningSessionAttestation |
| `numeracy` | levi | counting → calculus | learnerDid, currentLevel → learningSessionAttestation |
| `civics_charter` | levi (charter-paired) | etzhayyim charter + comparative governance/religion | learnerDid, charterSection → learningSessionAttestation + charterUnderstandingAttestation |
| `vocational` | levi | practical-skill (mitsuho/tatekata/hikari/yakushi aligned) | learnerDid, skillTarget → learningSessionAttestation + skillAttestation |
| `lifelong_inquiry` | levi | open-ended questioning | learnerDid, inquiryTopic → learningSessionAttestation |

## Constitutional Gates (G1–G14)

See ADR-2605261045. **IMMUTABLE** per R0.

Key gates:
- **G1**: All curriculum open-source on IPFS (Apache 2.0 + Charter Rider)
- **G3**: No algorithmic addiction (no streaks/rewards/FOMO/leaderboards/badges-as-status)
- **G4**: Council Lv6+ ≥3 charter alignment review per module
- **G6**: Zero data collection on minors (aggregate-only attestations; 30-day rotating pseudonym DID)
- **G7**: Anti-credentialism (no degrees/transcripts/GPA; only skillAttestation)
- **G10**: No examination-as-coercion (self-paced demonstration)
- **G11**: Lifelong (no age-out, no age-floor except minor privacy)
- **G13**: Inquiry-based pedagogy (≥30% learner-led)
- **G14**: Comparative-religion mandated; manabi never teaches etzhayyim as exclusive truth

## Non-Goals (N1–N10)

- N1: No formal accreditation
- N2: No degree issuance
- N3: No examination-as-gatekeeper
- N4: No behavioral conditioning
- N5: No for-profit textbook publishing
- N6: No proprietary LMS
- N7: No surveillance edtech
- N8: No replacement of family/cultural transmission
- N9: No advertising
- N10: No political-party content

## Roadmap

| Phase | Timeline | Scope | L-gate |
|---|---|---|---|
| **R0** | 2026-05-26 | Scaffold | — |
| **R1** | post-Council | IPFS-pinned curriculum + ≤100 learners (advisory consumption only) | future ADR |
| **R2** | post-R1 | Live human tutors + vocational scaffolding + ≤25,000 learners | **L4 eligibility** |
| **R3** | post-R2 | Yutori telepresence + ≤100,000 learners + lifelong inquiry mesh | **L4→L5 + L5 vocational** |

## Lexicons (4, deferred to R1+)

```
com.etzhayyim.manabi.{
  curriculumAttestation
  learningSessionAttestation       # aggregate-only for minors (G6)
  charterUnderstandingAttestation  # informed-consent baseline for SBT
  silenEducationReview
}
```

## Integration

- **Charter pair**: civics_charter cell delivers informed-consent baseline for Adherent SBT issuance (ADR-2605261000 §2)
- **Vocational pair**: skillAttestation feeds L5+ productive contribution
- **Cross-actor**: mitate (self-care PWA precedent) + hagukumi (Yutori inheritance R3+)

## References

- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — L4 + L5 gates
- `/90-docs/adr/2605261045-manabi-education-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605192200-etzhayyim-ip-free-release-charter-rider.md` — Pedagogy + license
- `/CLAUDE.md` — Religious-corp status table
