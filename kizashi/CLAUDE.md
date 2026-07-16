# 20-actors/kizashi — CLAUDE.md

## Identity

- **Name**: kizashi (兆 — 兆し = sign / early indication; the body's signs read before a clinician's diagnosis)
- **DID**: `did:web:kizashi.etzhayyim.com`
- **ADR**: ADR-2605312700 (R0 scaffold, 2026-05-31)
- **Parent ADR**: ADR-2605261000 (Liberation Ladder — L4 Care Tier gate)
- **L4 Care Tier siblings**: mitate (diagnosis ADR-2605260100) / iyashi (clinical care ADR-2605263000) / kokoro (mental health ADR-2605263700) / yakushi (pharma) / hagukumi (daily-living)
- **Role in tier**: **sensing/instrument layer — UPSTREAM of all clinical adjudication.** kizashi senses → mitate diagnoses → iyashi treats.
- **Status**: R0 scaffold — 6 cells path-reserved (import-time RuntimeError per G2 privacy invariant) + 6 Lexicon skeletons + modalityCapability ledger seed
- **Form**: 任意団体 internal non-invasive sensing/screening substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## What this is (the "scan pod", honestly bounded)

kizashi is the religious-corp's answer to the "futuristic scan pod"
desire: step in, multimodal non-invasive capture, AI fusion, and you
get **early-sign awareness + a self-referenced trajectory**. It is NOT
a diagnostic machine. The crux gates exist precisely because the
desire ("identify THE cause") exceeds what any non-invasive scan can
honestly deliver.

Symptom → modality map (feasibility-grounded):

- **腰痛 / 肩こり (functional)** → 3D optical posture/gait + shear-wave elastography (筋硬度) + thermography (血流). Strongest signal kizashi can give.
- **鼻詰まり** → acoustic rhinometry / rhinomanometry (構造・気流; non-ionizing).
- **アレルギー** → NOT imageable. Consented finger-stick microsampling (特異的IgE). R3-gated.
- **炎症** → thermography (surface, local) + breath-VOC / microsampling (CRP-class, systemic). Biochem part R3-gated.

## Constitutional Discipline (CRITICAL — IMMUTABLE)

Five structural boundaries — the first two are the whole reason this is
a separate actor and not folded into mitate:

1. **NON-DIAGNOSTIC (G3, 医師法 §17)** — kizashi outputs 兆候 +
   probabilistic cause-contribution weights + a consult recommendation.
   It NEVER names a disease, prescribes, or adjudicates. `attributionReport`
   schema **forbids** `diagnosis`/`prescription` fields. A licensed
   clinician (mitate/iyashi) owns any diagnosis.
2. **Medical-device regulatory boundary (G4, 薬機法/PMDA/SaMD)** —
   R0..R2 are software + simulation + non-ionizing non-regulated
   sensing ONLY (optical posture / thermography / bioimpedance).
   Any energy-emitting modality (ultrasound / MRI / X-ray) or
   microsampling biochem is R3-gated behind a licensed-medical-device
   pathway + qualified operator + Council Lv7+.
3. **Encrypted envelope MANDATORY (G2)** — biometric scan data is
   要配慮個人情報 (APPI special-care). `scanSessionAttestation` +
   `modalityObservation` + `wellbecomingTrajectory` MUST carry
   `encryptedPayloadCid`; plaintext rejected at schema layer (R1
   `additionalProperties: false`). Same discipline as iyashi.
4. **Verified-modality-only / anti-pseudoscience (G10)** — only
   modalities in the `modalityCapability` ledger with a declared
   evidence grade may emit. Bio-resonance / aura / 全身波動 / "quantum"
   scanners are EXCLUDED (N8). This protects members from pseudo-medical
   harm and the corp from §2 Charter exposure.
5. **Murakumo-only inference (G14)** — fusion + attribution via
   LiteLLM 127.0.0.1:4000 → gemma4:e4b only; vendor medical-AI
   PROHIBITED.

## Architecture

6 Pregel cells, all privacy-first, structurally encrypted except the
public capability ledger:

```
scan_session ──── naphtali (session; consent → orchestrate capture)
       │
modalityObservation (encrypted) ──┐
                                  ▼
signal_fusion ──── gad (session; fuse → transient features, no plaintext persist)
                                  │
attribution ───── gad (session; → attributionReport: probabilistic, NON-DIAGNOSTIC, G3+G7)
                                  │
   ┌──────────────────────────────┼──────────────────────────────┐
   ▼                              ▼                                ▼
wellbecoming_track            triage_referral ──── naphtali (event)
(gad; longitudinal,           routes → mitate / iyashi / kokoro
 self-referenced delta, G8)   G5 red-flag → 救急

modality_registry ──── asher (event/annual; PUBLIC modalityCapability ledger, G10)
```

Each cell = 1 Pregel graph. Cells communicate via lexicon records on
MST (`com.etzhayyim.kizashi.*`). All cell modules at R0 are import-time
`RuntimeError`.

## Privacy Invariant (CRITICAL)

Biometric scan data is 要配慮 PII — the most sensitive observation
class alongside clinical PHI. Structural enforcement (not policy):

1. `scanSessionAttestation` + `modalityObservation` +
   `wellbecomingTrajectory` schemas **require** `encryptedPayloadCid`
   and **reject** plaintext content fields (R1 additionalProperties=false).
2. R0 cells raise `RuntimeError` on import to **prevent accidental
   plaintext biometric data flow** before R1's encrypted-record
   framework is Council-attested production-ready.
3. Member identity uses 30-day rotating pseudonym DID per
   ADR-2605181200 — scan features are NEVER a stable biometric
   identifier (N10; not an identity database).
4. `signal_fusion` holds fused feature vectors transient-only; never
   writes plaintext features to disk.

## Non-diagnostic invariant (G3 + G7) — the load-bearing gate

- `attributionReport` MUST carry `confidence` (calibrated) +
  `disclaimer` ("所見 ≠ 確定原因。受診を推奨") + `consultRecommendation`;
  it MUST NOT carry `diagnosis` / `prescription` fields (schema-forbidden).
- Over-trust in the attribution is the primary member-harm risk; the
  structural disclaimer is the mitigation.
- "kizashi senses, mitate diagnoses" is not a slogan — it is the
  enforced data-flow boundary. kizashi can only emit a `triageReferral`
  with `targetActor ∈ {mitate, iyashi, kokoro}`; it cannot close a
  clinical loop itself.

## Operator Classification (G13) — Cross-actor with chigiri + toritate

Pod operators are vocation-flow L5 stewards per Liberation Ladder:

- (future) `operatorAttestation` links to
  `chigiri.stewardLaborAttestation` with `lLevel = "L5"` +
  `employmentRelation = "vocation-flow"`;
- `toritate.ledgerEntry.category` enum excludes
  `payroll`/`wage`/`salary`/`bonus`/`commission`;
- funded via Public Fund grant, never fee-for-scan (G13 + N6).

## R1 Activation Triggers

1. ADR-2605312700 Council Lv6+ ≥3 ratify;
2. ≥1 licensed-MD on Council medical advisory (shared with iyashi/mitate);
3. ADR-2605181100 encrypted-record framework production-deployed in CI;
4. `modalityCapability` ledger Council-ratified (evidence grades +
   regulatory classes per modality);
5. mitate R1 active (cross-actor sign → diagnosis handoff + G5
   emergency-keyword lexicon production-deployed);
6. chigiri R1 active (consent + stewardLaborAttestation read dependency).

## R1 Cell Activation Order

1. `kizashi_modality_registry` (must precede any observation — no
   modality can emit before the capability ledger gates it, G10);
2. `kizashi_signal_fusion` + `kizashi_attribution` (on SIMULATED +
   consented research data only).

R2 adds `scan_session` + `wellbecoming_track` + `triage_referral`
(non-ionizing non-regulated modalities, ≤20 research participants).

R3 adds regulated modalities via licensed-medical-device pathway.

## Cross-actor Relationships

### L4 Care Tier (sense → diagnose → treat)

- **mitate** (diagnosis routing) — TIGHT pair; kizashi senses signs →
  mitate (licensed clinician) reads/diagnoses; G5 emergency keyword
  shared;
- **iyashi** (clinical care) — referral for clinical encounter;
- **kokoro** (mental health) — psychosocial referral (pain is
  multifactorial; biopsychosocial routing);
- **yakushi** (pharma) — medication supply via iyashi only;
- **hagukumi** (daily-living) — minor/elder/incapacitated consent.

### Funding + procedural peers

- **chigiri** (read) — scan `consentRecord` + operator
  `stewardLaborAttestation`;
- **toritate** (read) — Public Fund grant accounting (no fee-for-scan);
- **manabi** — operator training + capability-ledger evidence-grade
  literacy.

## Build & Deploy

**R0 status**: Scaffold only. All 6 cell stubs exist under
`40-engine/kotoba/crates/kotoba-kotodama/cells/kizashi_*/cell.py` and raise `RuntimeError`
on import (intentional — prevents plaintext biometric data flow).

**Smoke test** (import-only; R0 cells deliberately fail import):
```bash
cd 20-actors/etzhayyim-root  # repo root
for c in kizashi_modality_registry kizashi_signal_fusion kizashi_attribution \
         kizashi_scan_session kizashi_wellbecoming_track kizashi_triage_referral; do
  python3 -c "
import importlib.util
s = importlib.util.spec_from_file_location('$c.cell', '40-engine/kotoba/crates/kotoba-kotodama/cells/$c/cell.py')
m = importlib.util.module_from_spec(s)
try: s.loader.exec_module(m); print('✘ $c did NOT raise')
except RuntimeError as e: print('✓ $c', 'R0 scaffold' in str(e))
"
done
```

Expected: all 6 raise `RuntimeError` with an "R0 scaffold" message.

## Honest limits (read before extending)

- **R0 = design + data-model + simulation. No hardware exists.**
- The highest-value cause-finding modalities (MRI, ultrasound,
  microsampling biochem) are exactly the **regulated** ones → R3-gated.
  R1/R2 deliver posture/thermal/bioimpedance only: useful for
  腰痛/肩こり *functional* signs, weak for アレルギー/炎症 biochem.
- Even at R3, kizashi outputs **probabilistic contributions, never a
  confirmed cause** (imaging finding ≠ symptom cause).
- 薬機法 clearance for energy-emitting modalities is multi-year /
  jurisdiction-specific. R3 may never be reached; R1/R2 stand alone as
  a non-regulated wellness-screening + trajectory tool.

## Modality capability ledger seed (G10 honesty registry)

`registry/modalities.seed.json` — 14 entries binding each modality to
`evidenceGrade` + `regulatoryClass` + `canDetect`/`cannotDetect` +
`ionizing` + `phaseGate`:

- **R2-usable** (non-ionizing, non-regulated, evidence B/C):
  optical-posture-3d, markerless-gait, infrared-thermography (C —
  regulators warn against thermography-alone), bioimpedance (C);
- **R3-gated** (regulated/IVD, evidence A/B): shear-wave-elastography
  (the best 筋硬度 modality, but ultrasound = regulated),
  acoustic-rhinometry, rhinomanometry, breath-voc, microsampling-ige
  (アレルギー — biochem only, N3), microsampling-crp;
- **referral marker** (G9 ALARA): imaging-referral-mri-ct — never
  in-pod; referral to licensed facility;
- **EXCLUDED grade-X** (G10 + N8, may never emit): bio-resonance,
  aura-imaging, quantum/全身波動.

All `verificationStatus = unverified-seed`: Council must ratify each
`evidenceGrade` + `regulatoryClass` (`councilAttestationCid`) before
any R1+ emission. The honest punchline the seed encodes: the strongest
cause-finding modalities are exactly the R3-gated regulated ones.

## Related Files

- `/20-actors/kizashi/manifest.jsonld`
- `/20-actors/kizashi/registry/modalities.seed.json` — modality capability ledger seed (G10)
- `/20-actors/kizashi/README.md`
- `/00-contracts/lexicons/com/etzhayyim/kizashi/` (6 Lexicon JSONs + README)
- `/90-docs/adr/2605312700-kizashi-noninvasive-multimodal-bodyscan-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605181100-mst-encrypted-records-signal-keywrap.md` — privacy envelope (G2)
- `/90-docs/adr/2605181200-mst-encrypted-metadata-leak-reduction.md` — rotating pseudonym DID (N10)
- `/90-docs/adr/2605192100-etzhayyim-mission-charter.md` — Wellbecoming ontology (G8)
- `/90-docs/adr/2605215000-etzhayyim-inference-murakumo-only-no-runpod.md` — G14 inference
- `/90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md` — TIGHT cross-actor (diagnosis)
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — sibling + G2 pattern
- `/90-docs/adr/2605263700-kokoro-mental-health-tier-b-actor-r0.md` — psychosocial referral
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — L4 gate
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — consent + steward
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — Public Fund grant
- `/CHARTER-RIDER.md` — G12 commercial-imaging-cloud prohibition source
- `/CLAUDE.md` — Religious-corp status table
