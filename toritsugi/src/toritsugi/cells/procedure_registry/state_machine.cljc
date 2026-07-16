(ns toritsugi.cells.procedure-registry.state-machine
  "Phase state machine for the 取次 (toritsugi) procedure_registry cell — the
  coded-procedure resolution membrane. A coded government 手続き enters and is
  RESOLVED only if it clears the non-fabrication + verified-procedure gates:

    G8(toritsugi) — the procedure cites non-blank 根拠法令 (legal-basis) AND
                provenance; no invented 手続き/様式/根拠法令/手数料/期限.
    G14(toritsugi) — verificationStatus is maintainer-verified / council-verified
                (NOT unverified-seed) AND the procedure is fresh (lastVerified
                within the freshness window). The caller passes `freshness_ok`
                (a boolean computed from today / last_verified / window) so the
                cell stays pure, deterministic and host-clock-free.

  Pure: (state) -> {\"cell_state\" {…}}. Stdlib only. Self-contained — the
  registry resolves here; live submission is gated downstream."
  (:require [clojure.string :as str]))

(def phase-init "init")
(def phase-resolved "resolved")
(def phase-refused "refused")

(def state-defaults
  {"phase"              phase-init
   "procedure_id"       ""
   "title"              ""
   "verification_status" "unverified-seed"
   "freshness_ok"       true
   "legal_basis"        ""
   "provenance"         ""
   "channel_type"       ""
   "required_docs"      []
   "fee_jpy"            0
   "statutory_days"     0
   "refusal"            ""})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))

(defn transition
  "Resolve one coded procedure toward a procedureGuide payload, or refuse with
  the failed invariant. Pure: (state) -> {\"cell_state\" {…}}.

  Expected state keys: procedure_id, title, verification_status, freshness_ok,
  legal_basis, provenance, channel_type, required_docs, fee_jpy, statutory_days."
  [state]
  (let [cs0 (cell-state state)
        cs  (-> cs0
                (assoc "procedure_id"        (get state "procedure_id" (get cs0 "procedure_id"))
                       "title"               (get state "title" (get cs0 "title"))
                       "verification_status" (get state "verification_status" (get cs0 "verification_status"))
                       "freshness_ok"        (boolean (get state "freshness_ok" (get cs0 "freshness_ok")))
                       "legal_basis"         (str/trim (str (get state "legal_basis" (get cs0 "legal_basis"))))
                       "provenance"          (str/trim (str (get state "provenance" (get cs0 "provenance"))))
                       "channel_type"        (get state "channel_type" (get cs0 "channel_type"))
                       "required_docs"       (vec (get state "required_docs" (get cs0 "required_docs")))
                       "fee_jpy"             (get state "fee_jpy" (get cs0 "fee_jpy"))
                       "statutory_days"      (get state "statutory_days" (get cs0 "statutory_days"))))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (= "unverified-seed" (get cs "verification_status"))
      (refuse "G14: verificationStatus=unverified-seed — 提出には maintainer/council-verified が必須")

      (not (get cs "freshness_ok"))
      (refuse "G14: lastVerified が鮮度Window切れ — 再検証してから提出可能")

      (str/blank? (get cs "legal_basis"))
      (refuse "G8: 根拠法令 (legal-basis) が空 — 手続きの捏造禁止")

      (str/blank? (get cs "provenance"))
      (refuse "G8: provenance が空 — 出典の明示必須 (捏造禁止)")

      :else
      (let [payload {":procedure/id"          (get cs "procedure_id")
                     ":procedure/title"       (get cs "title")
                     ":procedure/legal-basis" (get cs "legal_basis")
                     ":procedure/provenance"  (get cs "provenance")
                     ":procedure/channel"     (get cs "channel_type")
                     ":procedure/required-docs" (get cs "required_docs")
                     ":procedure/fee-jpy"     (get cs "fee_jpy")
                     ":procedure/statutory-days" (get cs "statutory_days")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-resolved)}))))
