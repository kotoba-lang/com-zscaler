(ns toritsugi.cells.draft.state-machine
  "Phase state machine for the 取次 (toritsugi) draft cell — assists filling the
  様式/フォーム toward an applicationDraft the member reviews + owns. It is the
  tightest UPL membrane in the actor, so it produces a draft ONLY if:

    G5(toritsugi) — assist-mode is :input-assist ONLY (入力補助). :draft-for-member
                (作成代理) is ALWAYS refused — that is reserved to 行政書士/弁護士/
                税理士 via chigiri. The member authors; toritsugi assists.
    G6(toritsugi) — the draft body is an encrypted ref (com.etzhayyim.encrypted.*),
                NEVER plaintext. A plaintext draft-body is refused by construction
                and is not representable in the payload.

  Pure: (state) -> {\"cell_state\" {…}}. Stdlib only. Self-contained."
  (:require [clojure.string :as str]))

(def phase-init "init")
(def phase-drafted "drafted")
(def phase-refused "refused")

(def encrypted-prefix "com.etzhayyim.encrypted")

(def state-defaults
  {"phase"               phase-init
   "session_id"          ""
   "procedure_id"        ""
   "assist_mode"         "input-assist"
   "encrypted_draft_ref" ""
   "draft_body"          nil                 ; plaintext — MUST stay nil (G6)
   "refusal"             ""})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))

(defn- norm-mode [s] (str/trim (str/replace (str s) #"^:+" "")))

(defn- blank-ref? [ref]
  (or (nil? ref) (str/blank? (str/trim (str ref)))
      (not (str/starts-with? (str/trim (str ref)) encrypted-prefix))))

(defn transition
  "Assist one applicationDraft (member-owned, encrypted), or refuse under UPL/G6.
  Pure: (state) -> {\"cell_state\" {…}}.

  Expected state keys: session_id, procedure_id, assist_mode,
  encrypted_draft_ref, draft_body (MUST be nil)."
  [state]
  (let [cs0 (cell-state state)
        mode (norm-mode (get state "assist_mode" (get cs0 "assist_mode")))
        draft-body (get state "draft_body" (get cs0 "draft_body"))
        cs  (assoc cs0
                   "session_id"   (get state "session_id" (get cs0 "session_id"))
                   "procedure_id" (get state "procedure_id" (get cs0 "procedure_id"))
                   "assist_mode"  mode
                   "encrypted_draft_ref" (str/trim (str (get state "encrypted_draft_ref" (get cs0 "encrypted_draft_ref"))))
                   "draft_body"   draft-body)
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (not= "input-assist" mode)
      (refuse (str "G5: 行政書士法/UPL — draft は :input-assist のみ (作成代理/advise は chigiri)。mode=" mode))

      (some? draft-body)
      (refuse "G6: 平文ドラフト本文は表現不可 — com.etzhayyim.encrypted.* 参照のみ")

      (blank-ref? (get cs "encrypted_draft_ref"))
      (refuse "G6: encrypted_draft_ref が必須 (com.etzhayyim.encrypted.*) — 暗号化参照なしのドラフト不可")

      :else
      (let [payload {":draft/session"     (get cs "session_id")
                     ":draft/procedure"   (get cs "procedure_id")
                     ":draft/assist-mode" (get cs "assist_mode")
                     ":draft/encrypted-ref" (get cs "encrypted_draft_ref")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-drafted)}))))
