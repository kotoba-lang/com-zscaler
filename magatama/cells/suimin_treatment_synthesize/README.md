# suimin_treatment_synthesize — Pregel cell for population-level treatment-evidence landscape

Per **ADR-2606072800 §Decision 3 G2** (grade-mandatory) + **G4** (referral-not-treatment) +
**G10** (Murakumo-only) + **G9** (witness) + §Decision 5.

Paired actor: [suimin](../../suimin/). Murakumo node (proposed): **levi**.

Aggregates graded `evidenceRecord`s for one `(conditionSlug, treatmentSlug)` into a
**population-level** `treatmentSynthesis` landscape (e.g., CPAP / oral appliance / positional
therapy / weight loss / upper-airway surgery / hypoglossal nerve stimulation for OSA). Never an
individual recommendation. Output **must** pass through `suimin_disclaimer_gate` before any
patient-facing surfacing.

## I/O

- **In**: graded `com.etzhayyim.suimin.evidenceRecord` (from `suimin_evidence_grade`)
- **Out**: `com.etzhayyim.suimin.treatmentSynthesis` → next cell `suimin_disclaimer_gate` (mandatory)

## Gate enforcement

- **G2**: explicit `overallEvidenceGrade`; ≥1 constituent `evidenceRecordUris` (no claim without evidence, G1).
- **G4 / N1-N5**: no individual diagnosis, AHI judgment, device setting, surgical indication, or prescription.
- **G9**: synthesis baseline witness N≥2 — automated output DID + (R2+) licensed sleep MD co-sign.
- **G12**: brand-neutral (device class / INN only).
