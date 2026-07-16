# kizashi — Verification Workflow (G10 modality ledger + cross-actor handoff)

Per ADR-2605312700 §2.F + §5. Two things must be *verified*, not asserted:
(1) every modality in `modalities.seed.json` ships `verificationStatus =
unverified-seed` and **no modality may emit a `modalityObservation` until its
evidence grade + regulatory class are Council-attested** (G10); (2) the
cross-actor handoff to mitate / iyashi / kokoro must actually land in a lexicon
those actors ingest (else "kizashi senses → mitate diagnoses" is aspirational).

> **R0 status**: this is the *process spec + a verified handoff snapshot*. No
> modality is Council-verified yet; all 14 seed entries remain `unverified-seed`
> (3 are permanently grade-X EXCLUDED). Verification execution begins at R1
> (Council ratification + `kizashi_modality_registry/cell.py` activation).

## A. Modality ledger tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | best-effort evidence grade / regulatory class; not attested | (initial) | ledger design only — **no observation may emit** |
| `maintainer-verified` | a maintainer re-checked evidenceGrade + regulatoryClass + ionizing + can/cannotDetect against the cited source within the freshness window | modality-evidence maintainer DID | R1 design / simulation use |
| `council-verified` | Council-reviewed; `councilAttestationCid` set | Council Lv6+ (regulated/ionizing modalities additionally need Council Lv7+ + 薬機法 pathway per G4/G9) | **observation emission** (R1 software / R3 regulated) |
| `excluded` (grade X) | pseudoscience; recorded ONLY to document exclusion | (initial, permanent) | **never emits** — terminal |

`freshnessWindowDays` (currently **365**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for emission.

## B. Per-modality verification checklist (unverified-seed → maintainer-verified)

For each entry, a maintainer confirms against the cited `provenance`:

1. **`evidenceGrade`** — defensible against peer-reviewed evidence:
   `A-validated-clinical` only for established clinical use; `B`/`C` for
   emerging/screening; **`X-excluded-pseudoscience`** for any modality with no
   validated mechanism (bio-resonance / aura / 波動 / "quantum"). Fail-closed: if
   the grade cannot be defended, it stays `unverified-seed`.
2. **`regulatoryClass`** — correct per jurisdiction (`non-regulated-wellness` /
   `samd-software` / `regulated-medical-device` / `ionizing-licensed-facility-only`).
3. **`ionizing`** — true ⇒ G9: never a routine-pod modality; referral only.
4. **`phaseGate` consistency** — `regulated-medical-device` / ionizing ⇒ `R3`
   (G4); only `non-regulated-wellness` + `ionizing=false` may be `R1`/`R2`.
5. **`canDetect`** — honest and bounded (no over-claim).
6. **`cannotDetect`** — explicitly states the key limit. MANDATORY for the
   honesty contract, e.g. imaging entries MUST state they cannot identify an
   allergen (N3); thermography MUST state it is not a standalone diagnostic.
7. **`provenance`** — resolves and supports the grade/class (literature or a
   regulator; for grade-X, documents *why* excluded).
8. **`councilAttestationCid`** — set only at council-verified (regulated/ionizing
   ⇒ Lv7+ + 薬機法 pathway evidence).

Only when **all 8** pass (and `councilAttestationCid` is set) may a modality
emit. Grade-X entries skip this — they are terminal-excluded.

## C. Cross-actor handoff — VERIFIED snapshot (2026-05-31)

The `triageReferral.targetActor` enum is `{mitate, iyashi, kokoro}`. Verified
that each target exposes a lexicon that can ingest a kizashi referral CID
(pull model — the target references the kizashi record, kizashi does not push):

| Path | Target lexicon | Ingest field (verified present) | Verdict |
|---|---|---|---|
| emergency → mitate | `com.etzhayyim.mitate.emergencyEscalation` | `intakeUri` (required) + `redFlagCategory` + `urgency` | ✅ aligned — kizashi emergency `triageReferral` CID is the `intakeUri` source |
| emergency → kokoro | `com.etzhayyim.kokoro.acuteCrisisEscalationLog` | `detectionSourceCid` (required) + `mitateG5EmergencyKeywordTriggeredCid` (required) | ✅ aligned — kizashi referral CID is `detectionSourceCid`; the canonical G5 keyword trigger lives in mitate (kokoro references it) |
| clinical → iyashi | `com.etzhayyim.iyashi.clinicalEncounterAttestation` | `consentRecordCid` + encrypted payload (encounter created by provider) | ✅ aligned (pull) — a provider opens the encounter; kizashi referral is consented provenance, not a clinical order |
| non-emergency → mitate diagnosis | `com.etzhayyim.mitate.diagnosticOrder` | **NO source/referral field**; `physicianAttestorDid` required | ✅ **correct by design** — kizashi is non-diagnostic (G3); it MUST NOT create a diagnostic order. A physician creates it after reading the referral |

**Honest gap (tracked, not a blocker)**: mitate has no *generic* "referral
intake" lexicon for the non-emergency, pre-physician case — `rhinitisIntake` is
domain-specific. At R1, non-emergency `triageReferral` is consumed by mitate
reading the kizashi record (pull); a generic `mitate.referralIntake` lexicon may
be proposed if volume warrants. Documented so the handoff is not overstated.

**G5 emergency-keyword source**: the canonical red-flag keyword set is
`mitate.emergencyEscalation.redFlagCategory` (10 values, e.g.
`anaphylaxis-airway-circulatory`, `meningitis-…`, `vision-loss-acute`) +
`urgency` (`emergency-call-119-ambulance` … `urgent-24hr-md`). kizashi
`triageReferral.consultRecommendation=emergency` + `emergencyFlag=true` maps to
this set; `kizashi_triage_referral/cell.py` is gated on
`MITATE_EMERGENCY_KEYWORD_LEXICON_REF` (mitate R1) precisely so the set is
shared, not re-invented.

## D. R0 verification state (reproducible)

```bash
# 6 cell stubs import-raise (R0 ceiling — no plaintext biometric flow):
for c in kizashi_modality_registry kizashi_signal_fusion kizashi_attribution \
         kizashi_scan_session kizashi_wellbecoming_track kizashi_triage_referral; do
  python3 -c "import importlib.util as u; s=u.spec_from_file_location('$c','40-engine/kotoba/crates/kotoba-kotodama/cells/$c/cell.py'); m=u.module_from_spec(s);
try: s.loader.exec_module(m); print('FAIL $c')
except RuntimeError as e: print('ok $c', 'R0 scaffold' in str(e))"
done
# 6 lexicons valid + non-diagnostic schema check:
python3 70-tools/scripts/validate-lexicons.py --root 00-contracts/lexicons/com/etzhayyim/kizashi/
# attributionReport MUST NOT permit a diagnosis field (G3):
python3 -c "import json; p=json.load(open('00-contracts/lexicons/com/etzhayyim/kizashi/attributionReport.json'))['defs']['main']['record']['properties']; assert 'diagnosis' not in p and 'prescription' not in p, 'G3 VIOLATION'; print('ok G3: no diagnosis/prescription field')"
# docs registry sidecars in sync:
python3 70-tools/scripts/docs/regen-registry.py --check
```

**Last verified**: 2026-05-31 (R0). All checks green at commit time.
