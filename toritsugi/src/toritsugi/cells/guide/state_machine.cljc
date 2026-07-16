(ns toritsugi.cells.guide.state-machine
  "Phase state machine for the 取次 (toritsugi) guide cell — builds the
  step-by-step 案内 + 必要書類 checklist for a resolved procedure (templates
  pulled from chigiri). It produces a procedureGuide ONLY under the 行政書士法 /
  UPL boundary:

    G5(toritsugi) — 案内 (information + wayfinding) + 必要書類 checklist ONLY. The
                assist-mode must be :guide / 案内. :draft-for-member (作成代理)
                and :advise (legal/tax advice) are ALWAYS refused — those route
                to chigiri + licensed counsel.
    G8(toritsugi) — the guide carries the procedure's legal-basis + provenance
                verbatim (no invented steps / docs / fees / deadlines).

  Pure: (state) -> {\"cell_state\" {…}}. Stdlib only. Self-contained."
  (:require [clojure.string :as str]))

(def phase-init "init")
(def phase-guided "guided")
(def phase-refused "refused")

(def allowed-modes #{"guide" "案内"})
(def forbidden-modes #{"draft-for-member" "advise"})

(def state-defaults
  {"phase"        phase-init
   "procedure_id" ""
   "legal_basis"  ""
   "provenance"   ""
   "required_docs" []
   "assist_mode"  "guide"
   "refusal"      ""})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))

(defn- norm-mode [s] (str/trim (str/replace (str s) #"^:+" "")))

(defn transition
  "Build a procedureGuide (案内 + 必要書類 checklist), or refuse under UPL.
  Pure: (state) -> {\"cell_state\" {…}}.

  Expected state keys: procedure_id, legal_basis, provenance, required_docs,
  assist_mode."
  [state]
  (let [cs0 (cell-state state)
        mode (norm-mode (get state "assist_mode" (get cs0 "assist_mode")))
        cs  (assoc cs0
                   "procedure_id" (get state "procedure_id" (get cs0 "procedure_id"))
                   "legal_basis"  (str/trim (str (get state "legal_basis" (get cs0 "legal_basis"))))
                   "provenance"   (str/trim (str (get state "provenance" (get cs0 "provenance"))))
                   "required_docs" (vec (get state "required_docs" (get cs0 "required_docs")))
                   "assist_mode"  mode)
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (contains? forbidden-modes mode)
      (refuse (str "G5: 行政書士法/UPL — assist_mode=" mode " は予約 (chigiri + licensed)。案内のみ可能"))

      (not (contains? allowed-modes mode))
      (refuse (str "G5: guide の assist_mode は :guide/案内 のみ (mode=" mode ")"))

      (str/blank? (get cs "legal_basis"))
      (refuse "G8: 根拠法令なし — 案内は出典付きのみ (捏造禁止)")

      :else
      (let [payload {":guide/procedure"     (get cs "procedure_id")
                     ":guide/legal-basis"   (get cs "legal_basis")
                     ":guide/provenance"    (get cs "provenance")
                     ":guide/required-docs" (get cs "required_docs")
                     ":guide/assist-mode"   (get cs "assist_mode")
                     ":guide/upl-notice"    true}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-guided)}))))
