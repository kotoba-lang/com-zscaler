# suimin 睡眠 — Maturity

**Stage: R0** (scaffold) — ADR-2606072800. Sleep-disorder treatment-EVIDENCE research +
synthesis (does NOT diagnose / treat / book / sell).

| Dimension | State |
|---|---|
| Lexicons | ✅ 7 under `com.etzhayyim.suimin.*` (sourceWhitelist / evidenceRecord / treatmentSynthesis / conditionProfile / referralPathway / silenSuiminReview / disclaimerText) |
| Manifest | ✅ `manifest.jsonld` |
| Tests | ✅ `methods/test_charter_gates.py` — **9 tests, green** (added 2026-06-16; previously ZERO tests anywhere) — pins G1/G2/G3/G4 schema invariants; `./run_tests.sh` |
| Cells | ⛔ none yet (R1 — hazard/evidence ingest + synthesis cells, Murakumo-only) |
| Methods | ⛔ no engine yet (R1 — evidence ingest + GRADE synthesis + disclaimer/referral gates) |

## Charter gates pinned by the test (lexicons/com/etzhayyim/suimin/README.md §Invariants)

- **G1 source-whitelist + provenance** — `evidenceRecord` requires `sourceClass` +
  `provenanceId` + `provenanceIdKind`; provenance kinds ⊆ {pmid, doi, cochrane-cd-id,
  guideline-id, icsd3-code, icd11-code}; each `sourceWhitelist` class declares
  `maxDefaultGrade` + `provenanceIdKind`.
- **G2 evidence-grade mandatory** — `evidenceRecord` requires `evidenceGrade` + `studyType`;
  grade vocabulary covers GRADE {high, moderate, low, very-low}; `treatmentSynthesis`
  requires `overallEvidenceGrade`.
- **G3 mandatory disclaimer** — `conditionProfile` / `referralPathway` / `treatmentSynthesis`
  each require `disclaimerTextUri`.
- **G4 referral-not-treatment** — `referralPathway` lists `recommendedFacilityKinds`; NO
  lexicon declares a `booking` / `reservation` / `appointment` / `purchase` / `diagnosis` /
  `prescription` / `deviceSale` **field** (descriptions affirming "NO booking" are the gate
  working, and are correctly not flagged — field-name check, not substring scan).

## R0 → R1 gate

Council Lv6+ ≥3 baseline (silenSuiminReview, witness ≥3) + Murakumo-only evidence ingest +
the disclaimer gate wired into every patient-facing output path.
