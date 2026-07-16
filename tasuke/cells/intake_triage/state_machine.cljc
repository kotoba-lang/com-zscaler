(ns tasuke.cells.intake-triage.state-machine
  "Phase state machine for the 助 (tasuke) intake_triage cell — the G1/G4/G7 intake membrane.
  1:1 port of cells/intake_triage/state_machine.py (ADR-2606060900).

  A consenting victim enters here. The case is SCREENED (refused outright) unless:
    G7 — consent is true and server-held-key is false (no-server-key);
    G1 — the support cost is 0 (全て無料; a fee is unrepresentable).
  A SCREENED case is then TRIAGED into a scam KIND + severity (G4 — a routing kind, never a verdict).
  REFUSAL gate, not a clamp.

  Conventions: dataclass IntakeState → a plain map with the SAME string field keys the Python
  `cs.__dict__` round-trips; phase enum value identities stay strings."
  (:require [tasuke.methods.triage :as triage]))

;; ── IntakePhase (enum — Python value identities preserved) ──
(def phase-init     "init")
(def phase-screened "screened")
(def phase-triaged  "triaged")
(def phase-refused  "refused")

;; ── IntakeState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"            phase-init
   "case_id"          ""
   "subject"          ""
   "consent"          false
   "support_cost_jpy" 0
   "server_held_key"  false
   "scam_kind"        ""
   "severity"         ""
   "refusal"          ""
   "payload"          {}})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- to-int [v] (long (or v 0)))

(defn transition-to-screened [state]
  (let [cs (cell-state state)
        cs (assoc cs
                  "case_id" (get state "case_id" (get cs "case_id"))
                  "subject" (get state "subject" (get cs "subject"))
                  "consent" (boolean (get state "consent" (get cs "consent")))
                  "support_cost_jpy" (to-int (get state "support_cost_jpy" (get cs "support_cost_jpy")))
                  "server_held_key" (boolean (get state "server_held_key" (get cs "server_held_key"))))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (not (get cs "consent"))
      (refuse "G7: a case is opened only with the victim's explicit consent")

      (not= (get cs "support_cost_jpy") 0)
      (refuse "G1 全て無料: support cost must be 0 (cash≡0)")

      (get cs "server_held_key")
      (refuse "G7/no-server-key: server-held-key must be false (ADR-2605231525)")

      :else
      {"cell_state" (assoc cs "refusal" "" "phase" phase-screened)})))

(defn transition-to-triaged [state]
  (let [cs (cell-state state)]
    (if (not= (get cs "phase") phase-screened)
      {"cell_state" (assoc cs
                           "refusal" "cannot triage a case that was not screened clean"
                           "phase" phase-refused)}
      (let [intake (get state "intake" {})
            scam-kind (triage/classify intake)
            severity (triage/assess-severity intake scam-kind)
            cs (assoc cs
                      "scam_kind" scam-kind
                      "severity" severity
                      "payload" {"caseId" (get cs "case_id")
                                 "subject" (get cs "subject")
                                 "scamKind" scam-kind
                                 "severity" severity
                                 "consent" true
                                 "supportCostJpy" 0
                                 "serverHeldKey" false}
                      "phase" phase-triaged)]
        {"cell_state" cs}))))
