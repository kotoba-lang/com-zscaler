(ns toritsugi.cells.submit.state-machine
  "Phase state machine for the 取次 (toritsugi) submit cell — THE ONLY
  active-outbound cell. Default mode hands the assembled draft back for MEMBER
  SELF-SUBMISSION; 代行 (agent-on-behalf) is the gated R3 exception. A
  submissionRecord is producible ONLY if:

    G3(toritsugi) — an active consent-ref is present (member-initiated).
    G6(toritsugi) — the submission body is an encrypted ref (com.etzhayyim.
                encrypted.*); plaintext PII is refused by construction.
    G10(toritsugi) — the channel is a lawful official one (online / in-person /
                postal); scraping / control-circumvention is refused.
    G14(toritsugi) — the procedure is verified AND fresh (freshness_ok, computed
                by the caller); unverified-seed / stale is refused.
    G15(toritsugi) — mode ∈ {member-self-submit, agent-on-behalf}. member-self-
                submit is the DEFAULT and proceeds; agent-on-behalf (代行) is
                gated — it is flagged for a human/Council sign-off downstream
                (interrupt-before :request-approval), never auto.

  Pure: (state) -> {\"cell_state\" {…}}. Stdlib only. Self-contained."
  (:require [clojure.string :as str]))

(def phase-init "init")
(def phase-submitted "submitted")
(def phase-refused "refused")

(def encrypted-prefix "com.etzhayyim.encrypted")
(def lawful-channels #{"online" "in-person" "postal"})
(def valid-modes #{"member-self-submit" "agent-on-behalf"})

(def state-defaults
  {"phase"              phase-init
   "session_id"         ""
   "procedure_id"       ""
   "member_did"         ""
   "consent_ref"        ""
   "mode"               "member-self-submit"
   "channel"            "online"
   "verification_status" "maintainer-verified"
   "freshness_ok"       true
   "encrypted_pii_ref"  ""
   "plaintext_pii"      nil               ; MUST stay nil (G6)
   "council_gate_ref"   ""                ; required for 代行 (G15)
   "refusal"            ""})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))

(defn- norm [s] (str/trim (str/replace (str s) #"^:+" "")))

(defn- blank-ref? [ref]
  (let [r (str/trim (str ref))]
    (or (str/blank? r) (not (str/starts-with? r encrypted-prefix)))))

(defn transition
  "Produce one submissionRecord (or refuse). member-self-submit proceeds; 代行
  (agent-on-behalf) is flagged gated (pending sign-off). Pure: (state) ->
  {\"cell_state\" {…}}."
  [state]
  (let [cs0 (cell-state state)
        mode    (norm (get state "mode" (get cs0 "mode")))
        channel (norm (get state "channel" (get cs0 "channel")))
        cs  (assoc cs0
                   "session_id"   (get state "session_id" (get cs0 "session_id"))
                   "procedure_id" (get state "procedure_id" (get cs0 "procedure_id"))
                   "member_did"   (str/trim (str (get state "member_did" (get cs0 "member_did"))))
                   "consent_ref"  (str/trim (str (get state "consent_ref" (get cs0 "consent_ref"))))
                   "mode"         mode
                   "channel"      channel
                   "verification_status" (get state "verification_status" (get cs0 "verification_status"))
                   "freshness_ok" (boolean (get state "freshness_ok" (get cs0 "freshness_ok")))
                   "encrypted_pii_ref" (str/trim (str (get state "encrypted_pii_ref" (get cs0 "encrypted_pii_ref"))))
                   "plaintext_pii" (get state "plaintext_pii" (get cs0 "plaintext_pii"))
                   "council_gate_ref" (str/trim (str (get state "council_gate_ref" (get cs0 "council_gate_ref")))))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (str/blank? (get cs "consent_ref"))
      (refuse "G3: consent-ref が必須 — 同意ベース提出のみ")

      (= "unverified-seed" (get cs "verification_status"))
      (refuse "G14: verificationStatus=unverified-seed — 提出不可 (要 maintainer/council 検証)")

      (not (get cs "freshness_ok"))
      (refuse "G14: 手続きが鮮度Window切れ — 再検証後に提出可")

      (not (contains? lawful-channels channel))
      (refuse (str "G10: 公式 channel (online/in-person/postal) のみ — channel=" channel))

      (some? (get cs "plaintext_pii"))
      (refuse "G6: 平文 PII は表現不可 — com.etzhayyim.encrypted.* のみ")

      (blank-ref? (get cs "encrypted_pii_ref"))
      (refuse "G6: encrypted_pii_ref が必須 (com.etzhayyim.encrypted.*)")

      (not (contains? valid-modes mode))
      (refuse (str "G15: mode は member-self-submit | agent-on-behalf (mode=" mode ")"))

      (and (= "agent-on-behalf" mode) (str/blank? (get cs "council_gate_ref")))
      (refuse "G15: 代行(agent-on-behalf)は Council Lv7+ gate(council_gate_ref)必須 — gated R3 例外")

      :else
      (let [gated? (= "agent-on-behalf" mode)
            payload {":submission/session"   (get cs "session_id")
                     ":submission/procedure" (get cs "procedure_id")
                     ":submission/member"    (get cs "member_did")
                     ":submission/consent"   (get cs "consent_ref")
                     ":submission/mode"      (get cs "mode")
                     ":submission/channel"   (get cs "channel")
                     ":submission/encrypted-ref" (get cs "encrypted_pii_ref")
                     ":submission/gated-signoff" gated?
                     ":submission/council-gate-ref" (get cs "council_gate_ref")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-submitted
                             "gated" gated?)}))))
