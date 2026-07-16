(ns manabi.cells.cert-prep.self-assessment
  "SelfAssessmentCell — manabi.cert_prep R0 scaffold per ADR-2605264400.

  Self-paced demonstration cell. G10 STRUCTURAL: timer is opt-in only;
  default sessions are untimed. No proctoring. No ID-verification gating.
  No high-stakes scoring.

  Outputs domainMasteryAttestation when the adherent self-elects to record
  a demonstration. credentialClaimedAttested const false (G7 + N13).

  Faithful 1:1 cljc port of cells/cert_prep/self_assessment.py.")

(defn solve
  "Self-paced demonstration cell.
  R0 scaffold — raises until ADR-2605264400 Council ratify."
  [_state]
  (throw (ex-info
           (str "manabi.cert_prep R0 scaffold: self_assessment cell not activated. "
                "Requires ADR-2605264400 Council ratify + practice_question cell "
                "active (R1) + domainMasteryAttestation lexicon production-deployed.")
           {:cell :self-assessment
            :status :r0-scaffold})))
