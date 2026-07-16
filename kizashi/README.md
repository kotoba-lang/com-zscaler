# kizashi (兆) — non-invasive multimodal body-scan / sign-sensing substrate

> **ADR-2605312700** · Tier-B · L4 Care Tier (sensing/instrument layer) · R0 scaffold (2026-05-31)
> DID `did:web:kizashi.etzhayyim.com` · Lexicons `com.etzhayyim.kizashi.*`

The religious-corp's answer to the "futuristic scan pod": step into a
non-invasive multimodal capture, AI fusion, and receive **early-sign
awareness + a self-referenced Wellbecoming trajectory** — *not* a
diagnosis.

**kizashi senses → mitate diagnoses → iyashi treats.** kizashi is the
instrument layer upstream of all clinical adjudication. It senses the
body's signs (兆候) of physical burden — 腰痛, 肩こり, 鼻詰まり,
アレルギー, 炎症 — fuses them, and emits **probabilistic, non-diagnostic
cause-contribution attributions** with calibrated uncertainty plus a
triage referral. A licensed clinician owns any diagnosis.

## Symptom → modality (feasibility-grounded)

| Burden | Non-invasive modality | Phase |
|---|---|---|
| 腰痛 / 肩こり (functional) | 3D optical posture/gait · shear-wave elastography (筋硬度) · thermography (血流) | R2 (optical/thermal) / R3 (ultrasound) |
| 鼻詰まり | acoustic rhinometry / rhinomanometry (構造・気流) | R3 |
| アレルギー | consented finger-stick microsampling (特異的IgE) — NOT imageable | R3 |
| 炎症 | thermography (surface) · breath-VOC / microsampling (CRP-class) | R2 (thermal) / R3 (biochem) |

## The load-bearing gates

- **G3 NON-DIAGNOSTIC** (医師法 §17) — 兆候 + probabilistic attribution + consult ONLY; never a diagnosis.
- **G4 medical-device boundary** (薬機法/SaMD) — R0..R2 software + simulation + non-ionizing non-regulated only; regulated/ionizing modalities R3-gated.
- **G2 encrypted envelope** — biometric scan = 要配慮 PII; mandatory.
- **G8 self-referenced Wellbecoming** — compare to your own prior trajectory, never a population ranking.
- **G10 anti-pseudoscience** — only evidence-graded modalities in the capability ledger; bio-resonance / aura / 波動 EXCLUDED.

## Status: R0 scaffold

Design + data-model + simulation only. **No hardware exists.** All 6
cells raise `RuntimeError` on import (prevents plaintext biometric
data flow before R1's Council-attested encrypted-record framework).

See [`CLAUDE.md`](./CLAUDE.md) for architecture, cross-actor wiring,
R1 activation triggers, and honest limits; the master ADR is
[`90-docs/adr/2605312700-...`](../../90-docs/adr/2605312700-kizashi-noninvasive-multimodal-bodyscan-tier-b-actor-r0.md).
