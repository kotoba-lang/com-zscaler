(ns magatama.cells.suimin-referral-router.state-machine
  "SuiminReferralRouterCell — referral routing to LOCAL sleep-medicine care.
  Per ADR-2606072800 §Decision 3 G4.
  Scaffold-only (Council activation gate). Port of suimin_referral_router/cell.py.")

(def council-charter-attestation-tx-hash nil)
(def silen-suimin-baseline-review-cid nil)
(def referral-directory-registry-cid nil)

(defn- council-activated? []
  (and council-charter-attestation-tx-hash
       silen-suimin-baseline-review-cid
       referral-directory-registry-cid))

(defn- assert-council! []
  (when-not (council-activated?)
    (throw (ex-info
            (str "suimin_referral_router cell scaffold-only — Council has not (a) attested the "
                 "suimin master charter ADR-2606072800, or (b) ratified the referral directory "
                 "registry (G4 referral-only — present which KIND of local sleep-medicine facility "
                 "to consult; NO appointment booking / telehealth scheduling / device sales). "
                 "Do not deploy.")
            {:cell :suimin-referral-router
             :gate :council-activation}))))

(defn super-step [_gated-output _directory]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements super-step"
                  {:cell :suimin-referral-router})))

(defn run-chain [state]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements run-chain"
                  {:cell :suimin-referral-router :state state})))
