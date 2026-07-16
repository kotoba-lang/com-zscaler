(ns toritsugi.cells.intake.state-machine
  "Phase state machine for the 取次 (toritsugi) intake cell — opens a
  procedureGuide session that binds a consenting member to ONE coded procedure.
  The session is opened ONLY if:

    G3(toritsugi) — member-initiated with an active consent-ref; the procedure
                is the member's OWN; never a non-consenting person; never a
                third party's procedure or data.
    G4(toritsugi) — the member is the named 申請者本人 (member-did present); no
                impersonation (§2(c)); toritsugi never poses as an official
                自治体 channel.

  Pure: (state) -> {\"cell_state\" {…}}. Stdlib only. Self-contained — the
  session id is assigned by the caller (host) and threaded through the run."
  (:require [clojure.string :as str]))

(def phase-init "init")
(def phase-intaked "intaked")
(def phase-refused "refused")

(def state-defaults
  {"phase"        phase-init
   "session_id"   ""
   "member_did"   ""
   "consent_ref"  ""
   "procedure_id" ""
   "mode"         "member-self-submit"
   "refusal"      ""})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))

(defn- lstrip-colon [s] (str/replace (str s) #"^:+" ""))

(defn transition
  "Open one procedureGuide session, or refuse. Pure: (state) -> {\"cell_state\" {…}}.

  Expected state keys: session_id, member_did, consent_ref, procedure_id, mode."
  [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0
                   "session_id"   (get state "session_id" (get cs0 "session_id"))
                   "member_did"   (str/trim (str (get state "member_did" (get cs0 "member_did"))))
                   "consent_ref"  (str/trim (str (get state "consent_ref" (get cs0 "consent_ref"))))
                   "procedure_id" (get state "procedure_id" (get cs0 "procedure_id"))
                   "mode"         (lstrip-colon (get state "mode" (get cs0 "mode"))))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (str/blank? (get cs "member_did"))
      (refuse "G4: 申請者本人(member-did)が未特定 — セッションを開けない")

      (str/blank? (get cs "consent_ref"))
      (refuse "G3: consent-ref が必須 — 同意ベースの本人手続きのみ")

      (str/blank? (get cs "procedure_id"))
      (refuse "G3/G8: 手続きが未指定 — セッション対象がない")

      :else
      (let [payload {":session/id"       (get cs "session_id")
                     ":session/member"   (get cs "member_did")
                     ":session/consent"  (get cs "consent_ref")
                     ":session/procedure" (get cs "procedure_id")
                     ":session/mode"     (get cs "mode")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-intaked)}))))
