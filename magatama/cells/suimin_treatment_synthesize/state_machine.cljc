(ns magatama.cells.suimin-treatment-synthesize.state-machine
  "SuiminTreatmentSynthesizeCell — population-level treatment-evidence landscape.
  Per ADR-2606072800 §Decision 3 G2 + G4 + G10.
  Scaffold-only (Council activation gate). Port of suimin_treatment_synthesize/cell.py.")

(def council-charter-attestation-tx-hash nil)
(def silen-suimin-baseline-review-cid nil)
(def source-whitelist-registry-cid nil)
(def per-treatment-synthesis-baseline-cid nil)

(defn- council-activated? []
  (and council-charter-attestation-tx-hash
       silen-suimin-baseline-review-cid
       source-whitelist-registry-cid
       per-treatment-synthesis-baseline-cid))

(defn- assert-council! []
  (when-not (council-activated?)
    (throw (ex-info
            (str "suimin_treatment_synthesize cell scaffold-only — Council has not (a) attested the "
                 "suimin master charter ADR-2606072800, or (b) ratified the source whitelist (G1), "
                 "or (c) ratified the per-treatment synthesis baseline (G2 overall-grade-mandatory + "
                 "G4 referral-not-treatment: NO individual diagnosis / AHI / device setting / surgical "
                 "indication / prescription; G9 witness N>=2 incl. R2+ licensed sleep MD co-sign). "
                 "Output MUST pass through suimin_disclaimer_gate (G3). Do not deploy.")
            {:cell :suimin-treatment-synthesize
             :gate :council-activation}))))

(defn super-step [_graded-records _baseline]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements super-step"
                  {:cell :suimin-treatment-synthesize})))

(defn run-chain [state]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements run-chain"
                  {:cell :suimin-treatment-synthesize :state state})))
