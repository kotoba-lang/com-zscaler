(ns magatama.cells.suimin-disclaimer-gate.state-machine
  "SuiminDisclaimerGateCell — non-diagnostic disclaimer + red-flag screen.
  Per ADR-2606072800 §Decision 3 G3 + G5.

  COUNCIL ACTIVATION GATE: scaffold-only until Council has attested the suimin master
  charter ADR-2606072800, ratified the disclaimer baseline (G3), and ratified the
  red-flag escalation protocol (G5). This cell is a bypass-forbidden architectural invariant.

  Port of suimin_disclaimer_gate/cell.py — honest stub matching the Python Council gate.
  The ns is loadable; activation raises (matching Python import-time RuntimeError) so that
  tests can assert the gate fires.")

;; ── Council gate constants (nil = not yet attested) ─────────────────────────────
(def council-charter-attestation-tx-hash nil)
(def silen-suimin-baseline-review-cid nil)
(def disclaimer-baseline-cid nil)
(def red-flag-escalation-protocol-cid nil)

(defn- council-activated? []
  (and council-charter-attestation-tx-hash
       silen-suimin-baseline-review-cid
       disclaimer-baseline-cid
       red-flag-escalation-protocol-cid))

(defn- assert-council! []
  (when-not (council-activated?)
    (throw (ex-info
            (str "suimin_disclaimer_gate cell scaffold-only — Council has not (a) attested the "
                 "suimin master charter ADR-2606072800, or (b) ratified the disclaimer baseline "
                 "(G3), or (c) ratified the red-flag escalation protocol (G5). "
                 "This cell is a bypass-forbidden architectural invariant. Do not deploy.")
            {:cell :suimin-disclaimer-gate
             :gate :council-activation}))))

;; Pregel super-step skeleton (only reached after Council gate is removed)
(defn super-step [output-candidate disclaimer red-flag-protocol]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements super-step"
                  {:cell :suimin-disclaimer-gate})))

(defn run-chain [state]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements run-chain"
                  {:cell :suimin-disclaimer-gate :state state})))
