(ns tasuke.cells.platform-abuse.state-machine
  "Phase state machine for the 助 (tasuke) platform_abuse cell — bank/platform request membrane.
  1:1 port of cells/platform_abuse/state_machine.py (ADR-2606060900).

  Generates a bank-freeze or platform request, member-authored (G3), to be sent BY THE MEMBER
  (G2 — no 代理送付), free (G1), draft-only (G9). A request to send as an agent is REFUSED."
  (:require [clojure.string :as str]
            [tasuke.methods.report-gen :as rg]))

(def ^:private request-kinds #{"bank-freeze-request" "platform-request"})

;; ── RequestPhase (enum — Python value identities preserved) ──
(def phase-init      "init")
(def phase-generated "generated")
(def phase-refused   "refused")

;; ── RequestState (dataclass → plain map, string keys + field defaults) ──
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
      (refuse "G3: a request must be authored by 'member' (server/agent unrepresentable)")

      (not (contains? request-kinds (get cs "kind")))
      (refuse (str "unknown request kind " (pr-str (get cs "kind"))))

      :else
      (let [case (get state "case" {":case/id" (get cs "case_id")})
            doc (if (= (get cs "kind") "bank-freeze-request")
                  (rg/bank-freeze-request case)
                  (rg/platform-request case :purpose (get state "purpose" "凍結・復旧")))]
        (rg/assert-member-authored doc)   ; G1/G2/G3/G9 guard
        (let [cs (assoc cs
                        "payload" {"docId" (get doc ":doc/id")
                                   "kind" (get cs "kind")
                                   "authoredBy" "member"
                                   "addressedTo" (get doc ":doc/addressed-to")
                                   "needsMemberSignature" true
                                   "supportCostJpy" 0
                                   "published" false}
                        "refusal" ""
                        "phase" phase-generated)]
          {"cell_state" cs})))))
