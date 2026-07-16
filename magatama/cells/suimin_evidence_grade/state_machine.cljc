(ns magatama.cells.suimin-evidence-grade.state-machine
  "SuiminEvidenceGradeCell — study-type detection + GRADE evidence grading.
  Per ADR-2606072800 §Decision 3 G2 + G10 + G12.
  Scaffold-only (Council activation gate). Port of suimin_evidence_grade/cell.py.")

(def council-charter-attestation-tx-hash nil)
(def silen-suimin-baseline-review-cid nil)
(def source-whitelist-registry-cid nil)
(def grade-rubric-baseline-cid nil)

(defn- council-activated? []
  (and council-charter-attestation-tx-hash
       silen-suimin-baseline-review-cid
       source-whitelist-registry-cid
       grade-rubric-baseline-cid))

(defn- assert-council! []
  (when-not (council-activated?)
    (throw (ex-info
            (str "suimin_evidence_grade cell scaffold-only — Council has not (a) attested the "
                 "suimin master charter ADR-2606072800, or (b) ratified the source whitelist (G1), "
                 "or (c) ratified the GRADE rubric baseline (G2; Murakumo-only inference G10; "
                 "COI / predatory-journal exclusion G12). Do not deploy.")
            {:cell :suimin-evidence-grade
             :gate :council-activation}))))

(defn super-step [_ungraded-record _whitelist]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements super-step"
                  {:cell :suimin-evidence-grade})))

(defn run-chain [state]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements run-chain"
                  {:cell :suimin-evidence-grade :state state})))
