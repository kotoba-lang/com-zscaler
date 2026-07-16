(ns tasuke.cells.test-state-machines
  "State-machine tests for 助 (tasuke) cells (R0).
  1:1 port of cells/test_state_machines.py (ADR-2606160842 py→clj port wave).
  .solve() is NOT exercised (R0 scaffold raises); the cell.py-importing test is dropped per the
  port-wave rule — the cljc state machines require the cljc method twins, so every assertion passes."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [tasuke.cells.intake-triage.state-machine :as intake]
            [tasuke.cells.evidence-preservation.state-machine :as evid]
            [tasuke.cells.police-report.state-machine :as report]
            [tasuke.cells.platform-abuse.state-machine :as request]
            [tasuke.cells.account-recovery.state-machine :as recovery]))

(def ^:private CASE
  {":case/id" "c1" ":case/subject" "did:web:etzhayyim.com:member:alice"
   ":case/scam-kind" ":unauthorized-transfer" ":case/loss-jpy" 480000
   ":case/narrative" "不正送金"})

;; ── intake_triage (G1/G4/G7) ─────────────────────────────────────────────────
(defn- screen [& {:as over}]
  (let [base {"cell_state" {} "case_id" "c1" "subject" "did:member:alice"
              "consent" true "support_cost_jpy" 0 "server_held_key" false}]
    (intake/transition-to-screened (merge base over))))

(deftest test-intake-screens-and-triages
  (let [cs (get (screen) "cell_state")]
    (is (= intake/phase-screened (get cs "phase")))
    (let [cs2 (get (intake/transition-to-triaged {"cell_state" cs "intake" CASE}) "cell_state")]
      (is (= intake/phase-triaged (get cs2 "phase")))
      (is (= "unauthorized-transfer" (get-in cs2 ["payload" "scamKind"])))
      (is (= 0 (get-in cs2 ["payload" "supportCostJpy"]))))))

(deftest test-intake-refuses-no-consent
  (let [cs (get (screen "consent" false) "cell_state")]
    (is (= intake/phase-refused (get cs "phase")))
    (is (str/includes? (get cs "refusal") "G7"))))

(deftest test-intake-refuses-nonzero-cost
  (let [cs (get (screen "support_cost_jpy" 500) "cell_state")]
    (is (= intake/phase-refused (get cs "phase")))
    (is (str/includes? (get cs "refusal") "G1"))))

(deftest test-intake-refuses-server-held-key
  (let [cs (get (screen "server_held_key" true) "cell_state")]
    (is (= intake/phase-refused (get cs "phase")))
    (is (str/includes? (get cs "refusal") "no-server-key"))))

;; ── evidence_preservation (G6) ───────────────────────────────────────────────
(deftest test-evidence-preserves-clean-items
  (let [items [{":evidence/id" "e1" ":evidence/case" "c1" ":evidence/kind" ":screenshot"
                ":evidence/envelope-ref" "ipfs://bafyX" ":evidence/bytes" "abc"
                ":evidence/captured-at" 1}]
        cs (get (evid/preserve {"cell_state" {} "case_id" "c1" "items" items}) "cell_state")]
    (is (= evid/phase-preserved (get cs "phase")))
    (is (= 1 (get cs "count")))))

(deftest test-evidence-refuses-plaintext-pii
  (let [items [{":evidence/id" "e1" ":evidence/kind" ":screenshot"
                ":evidence/envelope-ref" "ipfs://x" ":evidence/plaintext" "secret"}]
        cs (get (evid/preserve {"cell_state" {} "case_id" "c1" "items" items}) "cell_state")]
    (is (= evid/phase-refused (get cs "phase")))
    (is (str/includes? (get cs "refusal") "G6"))))

;; ── police_report (G3) ───────────────────────────────────────────────────────
(deftest test-report-generates-member-authored
  (let [cs (get (report/generate {"cell_state" {} "case_id" "c1" "kind" "damage-report"
                                  "authored_by" "member" "case" CASE}) "cell_state")]
    (is (= report/phase-generated (get cs "phase")))
    (is (= "member" (get-in cs ["payload" "authoredBy"])))
    (is (= false (get-in cs ["payload" "published"])))))

(deftest test-report-refuses-police-authored
  (let [cs (get (report/generate {"cell_state" {} "case_id" "c1" "kind" "damage-report"
                                  "authored_by" "police" "case" CASE}) "cell_state")]
    (is (= report/phase-refused (get cs "phase")))
    (is (str/includes? (get cs "refusal") "G3"))))

;; ── platform_abuse (bank/platform request) ───────────────────────────────────
(deftest test-request-generates-bank-freeze
  (let [cs (get (request/generate {"cell_state" {} "case_id" "c1" "kind" "bank-freeze-request"
                                   "authored_by" "member" "case" CASE}) "cell_state")]
    (is (= request/phase-generated (get cs "phase")))
    (is (= "member" (get-in cs ["payload" "authoredBy"])))))

(deftest test-request-refuses-agent-author
  (let [cs (get (request/generate {"cell_state" {} "case_id" "c1" "kind" "platform-request"
                                   "authored_by" "server" "case" CASE}) "cell_state")]
    (is (= request/phase-refused (get cs "phase")))
    (is (str/includes? (get cs "refusal") "G3"))))

;; ── account_recovery (G2 self-submit) ────────────────────────────────────────
(deftest test-recovery-plans-self-submit
  (let [cs (get (recovery/plan {"cell_state" {} "case_id" "c1" "service" "Google"
                                "role" "self-submit" "case" CASE}) "cell_state")]
    (is (= recovery/phase-planned (get cs "phase")))
    (is (= ":self-submit" (get-in cs ["payload" "supportRole"])))
    (is (seq (get-in cs ["payload" "steps"])))))

(deftest test-recovery-refuses-representation-role
  (let [cs (get (recovery/plan {"cell_state" {} "case_id" "c1" "service" "LINE"
                                "role" "represent" "case" CASE}) "cell_state")]
    (is (= recovery/phase-refused (get cs "phase")))
    (is (str/includes? (get cs "refusal") "G2"))))
