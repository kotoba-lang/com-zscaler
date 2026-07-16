(ns tasuke.cells.police-report.state-machine
  "Phase state machine for the 助 (tasuke) police_report cell — the G3 generation membrane.
  1:1 port of cells/police_report/state_machine.py (ADR-2606060900).

  Generates a police-side document and GUARDS the invariants: it is authored BY THE MEMBER
  (本人作成の申告書類, never police-authored — 公文書偽造を排除), requires the member's signature,
  is free, and is draft-only at R0. A request to author a doc AS the police is REFUSED."
  (:require [clojure.string :as str]
            [tasuke.methods.report-gen :as rg]))

(def ^:private police-kinds
  #{"damage-report" "incident-statement" "evidence-index" "damage-calculation"})

;; ── ReportPhase (enum — Python value identities preserved) ──
(def phase-init      "init")
(def phase-generated "generated")
(def phase-refused   "refused")

;; ── ReportState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"       phase-init
   "case_id"     ""
   "kind"        ""
   "authored_by" ""
   "refusal"     ""
   "payload"     {}})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- kw* [v]
  (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))

(defn generate [state]
  (let [cs (cell-state state)
        cs (assoc cs
                  "case_id" (get state "case_id" (get cs "case_id"))
                  "kind" (kw* (get state "kind" (get cs "kind")))
                  "authored_by" (kw* (get state "authored_by" "member")))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (not= (get cs "authored_by") "member")
      (refuse (str "G3: a generated document must be authored by 'member' (公文書偽造を排除; "
                   "police/official/server unrepresentable)"))

      (not (contains? police-kinds (get cs "kind")))
      (refuse (str "unknown police doc kind " (pr-str (get cs "kind"))))

      :else
      (let [case-map (get state "case" {":case/id" (get cs "case_id")})
            kind (get cs "kind")
            doc (case kind
                  "evidence-index"     (rg/evidence-index-doc case-map (get state "evidence" []))
                  "damage-report"      (rg/damage-report case-map)
                  "incident-statement" (rg/incident-statement case-map)
                  "damage-calculation" (rg/damage-calculation case-map))]
        (rg/assert-member-authored doc)   ; G1/G2/G3/G9 guard
        (let [cs (assoc cs
                        "payload" {"docId" (get doc ":doc/id")
                                   "kind" kind
                                   "authoredBy" "member"
                                   "needsMemberSignature" true
                                   "supportCostJpy" 0
                                   "published" false}
                        "refusal" ""
                        "phase" phase-generated)]
          {"cell_state" cs})))))
