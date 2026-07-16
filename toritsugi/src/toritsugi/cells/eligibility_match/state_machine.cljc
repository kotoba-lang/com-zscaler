(ns toritsugi.cells.eligibility-match.state-machine
  "Phase state machine for the 取次 (toritsugi) eligibility_match cell — the
  proactive 制度/給付 案内 (the LINE-like 'you may be eligible for X' notify).
  Driven by the consenting member's OWN life-event/profile, it produces a
  benefitMatch wayfinding note ONLY if:

    G3(toritsugi) — there is an active consent-ref bound to the member; this is
                member-initiated, never a third party's data.
    G4(toritsugi) — the member is named (member-did) — the wayfinding note names
                the member, never an indeterminate / sockpuppet subject.

  The note is non-adjudicating: toritsugi signals potential eligibility; the
  所管庁 decides (G5 — never a determination, never advice).

  Pure: (state) -> {\"cell_state\" {…}}. Stdlib only. Self-contained."
  (:require [clojure.string :as str]))

(def phase-init "init")
(def phase-matched "matched")
(def phase-refused "refused")

(def state-defaults
  {"phase"       phase-init
   "member_did"  ""
   "consent_ref" ""
   "benefit"     ""
   "procedure_id" ""
   "refusal"     ""})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))

(defn transition
  "Produce a benefitMatch wayfinding note for one consenting member, or refuse.
  Pure: (state) -> {\"cell_state\" {…}}.

  Expected state keys: member_did, consent_ref, benefit, procedure_id."
  [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0
                   "member_did"  (str/trim (str (get state "member_did" (get cs0 "member_did"))))
                   "consent_ref" (str/trim (str (get state "consent_ref" (get cs0 "consent_ref"))))
                   "benefit"     (str/trim (str (get state "benefit" (get cs0 "benefit"))))
                   "procedure_id" (get state "procedure_id" (get cs0 "procedure_id")))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (str/blank? (get cs "member_did"))
      (refuse "G4: member-did が未特定 — 案内の対象(申請者本人)が不明")

      (str/blank? (get cs "consent_ref"))
      (refuse "G3: consent-ref が必須 — 同意ベース・本人手続き限定")

      :else
      (let [payload {":benefit/member"      (get cs "member_did")
                     ":benefit/consent-ref" (get cs "consent_ref")
                     ":benefit/label"       (get cs "benefit")
                     ":benefit/procedure"   (get cs "procedure_id")
                     ":benefit/non-adjudicating-notice" true}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-matched)}))))
