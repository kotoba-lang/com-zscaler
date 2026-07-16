(ns tasuke.cells.account-recovery.state-machine
  "Phase state machine for the 助 (tasuke) account_recovery cell — the G2 self-help membrane.
  1:1 port of cells/account_recovery/state_machine.py (ADR-2606060900).

  Generates a recovery plan whose :support-role is :self-submit (the member executes the steps;
  助 never logs in as an agent). A request to act AS the member (represent/proxy/agent-file) is
  REFUSED. Free (G1), member-authored (G3), draft-only (G9)."
  (:require [clojure.string :as str]
            [tasuke.methods.report-gen :as rg]))

(def ^:private allowed-roles #{"guide" "draft-assist" "self-submit"})

;; ── RecoveryPhase (enum — Python value identities preserved) ──
(def phase-init    "init")
(def phase-planned "planned")
(def phase-refused "refused")

;; ── RecoveryState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"   phase-init
   "case_id" ""
   "service" ""
   "role"    "self-submit"
   "refusal" ""
   "payload" {}})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- kw* [v]
  (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))

(defn plan [state]
  (let [cs (cell-state state)
        cs (assoc cs
                  "case_id" (get state "case_id" (get cs "case_id"))
                  "service" (let [s (get state "service" (get cs "service"))]
                              (if (or (nil? s) (= s "")) "（サービス名）" s))
                  "role" (kw* (get state "role" (get cs "role"))))]
    (if (not (contains? allowed-roles (get cs "role")))
      {"cell_state" (assoc cs
                           "refusal" (str "G2: support-role " (pr-str (get cs "role"))
                                          " unrepresentable — only guide/draft-assist/self-submit (no 代理ログイン)")
                           "phase" phase-refused)}
      (let [case (get state "case" {":case/id" (get cs "case_id")})
            doc (rg/recovery-plan case :service (get cs "service"))]
        (rg/assert-member-authored doc)
        (let [cs (assoc cs
                        "payload" {"docId" (get doc ":doc/id")
                                   "kind" "recovery-plan"
                                   "service" (get cs "service")
                                   "authoredBy" "member"
                                   "supportRole" ":self-submit"
                                   "steps" (get doc "steps" [])
                                   "supportCostJpy" 0
                                   "published" false}
                        "refusal" ""
                        "phase" phase-planned)]
          {"cell_state" cs})))))
