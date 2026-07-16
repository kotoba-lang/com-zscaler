# suimin_evidence_grade — Pregel cell for study-type detection + GRADE grading

Per **ADR-2606072800 §Decision 3 G2** (evidence-grade mandatory) + **G10** (Murakumo-only) +
**G12** (source-integrity) + §Decision 5.

Paired actor: [suimin](../../suimin/). Murakumo node (proposed): **levi**.

Assigns explicit GRADE (high / moderate / low / very-low) + `studyType` to each ungraded
`evidenceRecord`, bounded by the `sourceClass.maxDefaultGrade` (preprint → low). Inference via
Murakumo fleet only.

## I/O

- **In**: ungraded `com.etzhayyim.suimin.evidenceRecord` (from `suimin_source_ingest`)
- **Out**: graded `com.etzhayyim.suimin.evidenceRecord` → next cell `suimin_treatment_synthesize`

## Gate enforcement

- **G2**: no record leaves without an explicit `evidenceGrade` + `studyType`.
- **G10**: classification/grading inference is Murakumo-only (no RunPod / Vertex / OpenAI / Anthropic direct).
- **G12**: COI flagged; predatory-journal / vendor-marketing sources excluded.
