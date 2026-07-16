(ns magatama.cells.suimin-treatment-synthesize.cell
  "SuiminTreatmentSynthesizeCell — population-level treatment-evidence landscape.
  Per ADR-2606072800 §Decision 3 G2 (grade-mandatory) + G4 (referral-not-treatment) +
  G10 (Murakumo-only) + §Decision 5.

  Aggregates graded evidenceRecords into a POPULATION-LEVEL landscape — never an individual
  recommendation (N1-N5, G4). Output handed to suimin_disclaimer_gate (G3 invariant) before
  any patient-facing surfacing. R0 scaffold — .solve() raises until the Council activation gate
  is satisfied (1:1 port of suimin_treatment_synthesize/cell.py import-time RuntimeError).")

(defn solve
  [_input-state]
  (throw (ex-info
          (str "suimin_treatment_synthesize cell scaffold-only — Council has not (a) attested the "
               "suimin master charter ADR-2606072800, or (b) ratified the source whitelist (G1), "
               "or (c) ratified the per-treatment synthesis baseline (G2 overall-grade-mandatory + "
               "G4 referral-not-treatment: NO individual diagnosis / AHI / device setting / surgical "
               "indication / prescription; G9 witness N>=2 incl. R2+ licensed sleep MD co-sign). "
               "Output MUST pass through suimin_disclaimer_gate (G3). Do not deploy.")
          {:scaffold true :cell :suimin-treatment-synthesize})))
