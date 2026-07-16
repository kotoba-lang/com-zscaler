(ns magatama.cells.suimin-referral-router.cell
  "SuiminReferralRouterCell — referral routing to LOCAL sleep-medicine care.
  Per ADR-2606072800 §Decision 3 G4 (referral-not-treatment) + §Decision 5.

  Surfaces WHAT KIND of facility to consult + nearby facilities from a Council-ratified
  directory. Presentation only — NO booking / telehealth scheduling / device sales (N6/N7).
  R0 scaffold — .solve() raises until the Council activation gate is satisfied
  (1:1 port of suimin_referral_router/cell.py import-time RuntimeError).")

(defn solve
  [_input-state]
  (throw (ex-info
          (str "suimin_referral_router cell scaffold-only — Council has not (a) attested the "
               "suimin master charter ADR-2606072800, or (b) ratified the referral directory "
               "registry (G4 referral-only — present which KIND of local sleep-medicine facility to "
               "consult + nearby facilities; NO appointment booking / telehealth scheduling / device "
               "sales). Do not deploy.")
          {:scaffold true :cell :suimin-referral-router})))
