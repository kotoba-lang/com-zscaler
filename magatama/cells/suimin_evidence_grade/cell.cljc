(ns magatama.cells.suimin-evidence-grade.cell
  "SuiminEvidenceGradeCell — study-type detection + GRADE evidence grading.
  Per ADR-2606072800 §Decision 3 G2 (evidence-grade mandatory) + G10 (Murakumo-only) +
  G12 (source-integrity) + §Decision 5.

  Assigns an explicit GRADE + studyType to each ungraded evidenceRecord, bounded by the
  sourceClass maxDefaultGrade. R0 scaffold — .solve() raises until the Council activation gate
  is satisfied (1:1 port of suimin_evidence_grade/cell.py import-time RuntimeError).")

(defn solve
  [_input-state]
  (throw (ex-info
          (str "suimin_evidence_grade cell scaffold-only — Council has not (a) attested the "
               "suimin master charter ADR-2606072800, or (b) ratified the source whitelist (G1), "
               "or (c) ratified the GRADE rubric baseline (G2 — every record needs an explicit "
               "evidence grade + studyType, bounded by sourceClass; Murakumo-only inference G10; "
               "COI / predatory-journal exclusion G12). Do not deploy.")
          {:scaffold true :cell :suimin-evidence-grade})))
