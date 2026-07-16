# suimin_source_ingest — Pregel cell for whitelisted-source evidence ingest

Per **ADR-2606072800 §Decision 2 (G1 source-whitelist invariant)** + §Decision 5.

Paired actor: [suimin](../../suimin/). Sibling actor: [mitate](../../mitate/).

Murakumo node (proposed): **levi**.

Read-only ingest of treatment-evidence from **whitelisted sources only** — PubMed/MeSH indexed
literature (RCT / systematic review / cohort), Cochrane systematic reviews, AASM and national
sleep-society clinical practice guidelines, ICSD-3 / ICD-11 classification anchors. Emits an
**ungraded** `com.etzhayyim.suimin.evidenceRecord` with verifiable provenance, then hands off to
`suimin_evidence_grade`.

## I/O

- **In**: operator XRPC ingest request `(conditionSlug, treatmentSlug, sources[])`
- **Out**: `com.etzhayyim.suimin.evidenceRecord` (ungraded) → next cell `suimin_evidence_grade`

## Gate enforcement

- **G1**: every source must resolve to a `sourceClass` in the ratified `com.etzhayyim.suimin.sourceWhitelist`; every record must carry a verifiable provenance id (PMID / DOI / Cochrane CD-ID / guideline-ID). No whitelist + no provenance → not emittable.
- **G12**: preprints labeled as preprint (grade capped low downstream); vendor-marketing / COI sources excluded.

No PHI (R0/R1 corpus-level only).
