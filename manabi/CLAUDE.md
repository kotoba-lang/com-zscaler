# 20-actors/manabi — CLAUDE.md

## Identity

- **Name**: manabi (学び — continuative noun of 学ぶ "to learn"; etymologically rooted in 真似ぶ "to imitate/emulate"; multi-generational lifelong-learning echo)
- **DID**: `did:web:etzhayyim.com:manabi`
- **ADR**: ADR-2605261045 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605261000 (Liberation Ladder — L4 literacy/civics + L5 advanced + vocational gates)
- **Status**: R0 scaffold — all cells import-time RuntimeError

## Architecture

5 Pregel cells, content-distribution + human-tutor coordination focused:

```
literacy ──────────┐
numeracy ──────────┤
civics_charter ────┤── learningSessionAttestation (aggregate for minors G6)
vocational ────────┤── skillAttestation (L5+ productive contribution input)
lifelong_inquiry ──┘── charterUnderstandingAttestation (SBT informed-consent)
```

Each cell = 1 Pregel graph. Lowest compute footprint of any Tier-B actor (content distribution heavy, compute light).

## Constitutional Invariants (constitutional, not adjustable)

### Anti-credentialism (G7 + §2(e))
manabi issues **no degrees, no transcripts, no GPA-equivalent**. Only `skillAttestation` (subject-specific replayable demonstration). L5+ adherent productive contribution gating uses skillAttestation; never credential.

### Anti-addiction UX (G3 + §2(d))
Forbidden in all manabi UX:
- streaks
- daily-login rewards
- variable-reward schedules
- FOMO triggers
- leaderboards
- badges-as-status

Permitted:
- progress meters (linear, no rewards)
- self-pacing controls
- bookmarks
- learner-initiated session resumption

### Minor privacy (G6)
For under-18 learner sessions:
- learner identity → 30-day rotating pseudonym DID (ADR-2605181200)
- session attestation → **aggregate-only fields**: sessionDuration, moduleId, completionFraction
- **forbidden**: per-learner performance data, error logs, behavioral signals, time-on-task per question, etc.

Schema validation (`learningSessionAttestation`) enforces this structurally via `learnerAgeBucket: 'minor' | 'adult'` discriminator.

### Comparative religion (G14)
Any religion module — including etzhayyim's own charter — teaches comparative approach. No "etzhayyim is the only truth" framing. Per ADR-2605192100 §1.15 non-eschatology + §1.3 multi-gen cultural humility.

## Curriculum Review Process (G4)

Every curriculum module requires Council Lv6+ ≥3 attestation for:
1. Multi-generational priority alignment
2. Anti-individualism alignment
3. Wellbecoming alignment (§2(d))
4. Non-eschatology (no millenarian content)
5. Charter Rider §2 compliance scan (automated)

Review queue is parallel; bottleneck mitigated by per-Council-member review allocation.

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.manabi`

4 records:

1. `curriculumAttestation` — module declaration: open-source content CID + reviewer attestations + Charter Rider scan result
2. `learningSessionAttestation` — per-session; aggregate-only for minors (G6 structural)
3. `charterUnderstandingAttestation` — civics_charter cell output; SBT informed-consent baseline (ADR-2605261000 §2)
4. `silenEducationReview` — Council attestation scope (pedagogy + accessibility + anti-addiction + charter alignment)

## Pregel Cells (R0 stub bodies)

All R0 cells raise `RuntimeError("manabi R0 scaffold: activate via Council ADR + curriculum library Council-reviewed")` on import.

### R1 activation triggers
1. ADR-2605261045 Council Lv6+ ratify
2. ≥1 inquiry-pedagogy practitioner on Council education advisory
3. Initial curriculum library (literacy + civics_charter + numeracy starter) Council-reviewed for G4
4. IPFS pinning operational for curriculum CID
5. Minor-privacy 30-day rotating pseudonym DID framework production

## Build & Deploy

**R0 status**: Scaffold only. All cells RuntimeError on import.

**Smoke test**:
```bash
cd 20-actors/manabi
python -c "import manabi.cells.literacy" 2>&1 | grep "R0 scaffold"
python -c "import manabi.cells.numeracy" 2>&1 | grep "R0 scaffold"
python -c "import manabi.cells.civics_charter" 2>&1 | grep "R0 scaffold"
python -c "import manabi.cells.vocational" 2>&1 | grep "R0 scaffold"
python -c "import manabi.cells.lifelong_inquiry" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/manabi/manifest.jsonld`
- `/90-docs/adr/2605261045-manabi-education-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — L4 + L5 gates
- `/90-docs/adr/2605192200-etzhayyim-ip-free-release-charter-rider.md` — Pedagogy + license
- `/CLAUDE.md` — Religious-corp status table
