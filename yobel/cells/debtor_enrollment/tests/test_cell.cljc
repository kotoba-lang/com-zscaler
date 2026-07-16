(ns yobel.cells.debtor-enrollment.tests.test-cell
  "Tests for DebtorEnrollmentCell — DMN eligibility (R12 SBT, R13 §2(b), R1-R11 rite-type rules).

  Clojure port of cells/debtor_enrollment/tests/test_cell.py."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [langgraph.checkpoint :as cp]
            [langgraph.graph :as g]
            [yobel.cells.debtor-enrollment.cell :refer [build-graph run-eligibility-dmn]]
            [yobel.ports :as ports]))

(def all-rite-types
  ["shmita_7yr" "yobel_50yr" "tokusei_rei" "religious_jubilee" "political_amnesty"])

;; ─── DMN R14: natural-person-only gate (highest-priority short-circuit) ──

(deftest test-dmn-r14-legal-person-sbt-rejects-all-rite-types
  (testing "Even if SBT level is high and instruments are clean, legal_person entityType → R14 reject."
    (doseq [rite-type all-rite-types]
      (let [out (run-eligibility-dmn
                 {:debtor-sbt-level 5
                  :debtor-entity-type "natural_person"   ;; declared OK
                  :debtor-sbt-entity-type "legal_person" ;; but SBT says legal_person
                  :debtor-community-member true
                  :rite-type rite-type
                  :rite-effective-date "2026-09-26T00:00:00Z"
                  :rite-jurisdiction-scope ["ALL"]
                  :debtor-jurisdiction-iso3 "JPN"
                  :matched-debts [{:instrument "loan" :origination-date "2024-01-01T00:00:00Z"}]})]
        (is (false? (:eligible out)) (str "R14 (legal_person SBT) failed for " rite-type))
        (is (= "R14" (:dmn-rule-fired out)))))))

(deftest test-dmn-r14-declared-legal-person-rejects
  (testing "Declared debtorEntityType=legal_person → R14 reject (caller cannot lie)."
    (let [out (run-eligibility-dmn
               {:debtor-sbt-level 5
                :debtor-entity-type "legal_person"
                :debtor-sbt-entity-type "natural_person"
                :debtor-community-member true
                :rite-type "shmita_7yr"
                :rite-effective-date "2026-09-26T00:00:00Z"
                :rite-jurisdiction-scope ["ALL"]
                :matched-debts []})]
      (is (false? (:eligible out)))
      (is (= "R14" (:dmn-rule-fired out))))))

(deftest test-dmn-r14-unset-entity-type-rejects
  (testing "Missing entity-type (neither declared nor SBT) → R14 reject."
    (let [out (run-eligibility-dmn
               {:debtor-sbt-level 5
                :debtor-community-member true
                :rite-type "shmita_7yr"
                :rite-effective-date "2026-09-26T00:00:00Z"
                :rite-jurisdiction-scope ["ALL"]
                :matched-debts []})]
      (is (false? (:eligible out)))
      (is (= "R14" (:dmn-rule-fired out))))))

(deftest test-dmn-r14-takes-priority-over-r12
  (testing "Even with NO SBT, legal_person entityType → R14 fires first (not R12)."
    (let [out (run-eligibility-dmn
               {:debtor-sbt-level 0
                :debtor-entity-type "legal_person"
                :debtor-sbt-entity-type "legal_person"
                :rite-type "shmita_7yr"
                :matched-debts []})]
      (is (= "R14" (:dmn-rule-fired out))))))

;; ─── DMN R12: SBT gate (short-circuit) ───────────────────────────────

(deftest test-dmn-r12-no-sbt-rejects-all-rite-types
  (doseq [rite-type all-rite-types]
    (let [out (run-eligibility-dmn
               {:debtor-sbt-level 0
                :debtor-entity-type "natural_person"
                :debtor-sbt-entity-type "natural_person"
                :debtor-community-member true
                :rite-type rite-type
                :rite-effective-date "2026-09-26T00:00:00Z"
                :rite-jurisdiction-scope ["ALL"]
                :debtor-jurisdiction-iso3 "JPN"
                :matched-debts []})]
      (is (false? (:eligible out)) (str "R12 failed for " rite-type))
      (is (= "R12" (:dmn-rule-fired out))))))

;; ─── DMN R13: Charter Rider §2(b) prohibited-instrument gate ──────────

(deftest test-dmn-r13-prohibited-instrument-rejects
  (doseq [bad-instrument ["liquidation" "margin_call" "seizure"]]
    (let [out (run-eligibility-dmn
               {:debtor-sbt-level 5
                :debtor-entity-type "natural_person"
                :debtor-sbt-entity-type "natural_person" ;; SBT OK
                :debtor-community-member true
                :rite-type "shmita_7yr"
                :rite-effective-date "2026-09-26T00:00:00Z"
                :rite-jurisdiction-scope ["ALL"]
                :debtor-jurisdiction-iso3 "JPN"
                :matched-debts [{:instrument "loan" :origination-date "2020-01-01T00:00:00Z"}
                                {:instrument bad-instrument :origination-date "2020-01-01T00:00:00Z"}]})]
      (is (false? (:eligible out)) (str "R13 failed for " bad-instrument))
      (is (= "R13" (:dmn-rule-fired out)))
      (is (some #(str/includes? % "§2(b)") (:dmn-reasons out))))))

;; ─── DMN R1-R3: shmita ────────────────────────────────────────────────

(deftest test-dmn-r1-shmita-happy-path
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member true
              :rite-type "shmita_7yr"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["ALL"]
              :debtor-jurisdiction-iso3 "ISR"
              :matched-debts [{:instrument "loan" :origination-date "2024-01-01T00:00:00Z"}]})]
    (is (true? (:eligible out)))
    (is (= "R1" (:dmn-rule-fired out)))))

(deftest test-dmn-r2-shmita-post-cycle-debt-rejects
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member true
              :rite-type "shmita_7yr"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["ALL"]
              :debtor-jurisdiction-iso3 "ISR"
              :matched-debts [{:instrument "loan" :origination-date "2027-01-01T00:00:00Z"}]})]
    (is (false? (:eligible out)))
    (is (= "R2" (:dmn-rule-fired out)))))

(deftest test-dmn-r3-shmita-non-community-rejects
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member false ;; Deut 15:3 foreigner exclusion
              :rite-type "shmita_7yr"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["ALL"]
              :debtor-jurisdiction-iso3 "USA"
              :matched-debts []})]
    (is (false? (:eligible out)))
    (is (= "R3" (:dmn-rule-fired out)))))

;; ─── DMN R4-R5: yobel ─────────────────────────────────────────────────

(deftest test-dmn-r4-yobel-happy-path
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member true
              :rite-type "yobel_50yr"
              :rite-effective-date "2074-01-01T00:00:00Z"
              :rite-jurisdiction-scope ["ALL"]
              :debtor-jurisdiction-iso3 "ISR"
              :matched-debts [{:instrument "loan" :origination-date "2050-01-01T00:00:00Z"}]})]
    (is (true? (:eligible out)))
    (is (= "R4" (:dmn-rule-fired out)))))

;; ─── DMN R6-R7: tokusei ──────────────────────────────────────────────

(deftest test-dmn-r6-tokusei-in-jurisdiction-accepts
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member false ;; tokusei doesn't require community
              :rite-type "tokusei_rei"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["JPN"]
              :debtor-jurisdiction-iso3 "JPN"
              :matched-debts [{:instrument "loan" :origination-date "2020-01-01T00:00:00Z"}]})]
    (is (true? (:eligible out)))
    (is (= "R6" (:dmn-rule-fired out)))))

(deftest test-dmn-r7-tokusei-out-of-jurisdiction-rejects
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member false
              :rite-type "tokusei_rei"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["JPN"]
              :debtor-jurisdiction-iso3 "USA" ;; not in scope
              :matched-debts []})]
    (is (false? (:eligible out)))
    (is (= "R7" (:dmn-rule-fired out)))))

;; ─── DMN R8-R9: religious_jubilee ────────────────────────────────────

(deftest test-dmn-r8-religious-jubilee-tithe-accepts
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member true
              :rite-type "religious_jubilee"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["ALL"]
              :debtor-jurisdiction-iso3 "ITA"
              :matched-debts [{:instrument "tithe_obligation" :origination-date "2024-01-01T00:00:00Z"}]})]
    (is (true? (:eligible out)))
    (is (= "R8" (:dmn-rule-fired out)))))

(deftest test-dmn-r9-religious-jubilee-monetary-debt-rejects
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member true
              :rite-type "religious_jubilee"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["ALL"]
              :debtor-jurisdiction-iso3 "ITA"
              :matched-debts [{:instrument "loan" :origination-date "2024-01-01T00:00:00Z"}]})]
    (is (false? (:eligible out)))
    (is (= "R9" (:dmn-rule-fired out)))))

;; ─── DMN R10-R11: political_amnesty ──────────────────────────────────

(deftest test-dmn-r10-political-amnesty-accepts-individual-tax-amnesty
  (testing "political_amnesty scope (post-natural-person-only amendment): mass amnesty for
    individual debtors under sovereign decree (e.g. national tax delinquency pardon).
    Sovereign / corporate debt restructuring is OUT of scope for yobel."
    (let [out (run-eligibility-dmn
               {:debtor-sbt-level 1
                :debtor-entity-type "natural_person"
                :debtor-sbt-entity-type "natural_person"
                :debtor-community-member false
                :rite-type "political_amnesty"
                :rite-effective-date "2026-09-26T00:00:00Z"
                :rite-jurisdiction-scope ["AFG"]
                :debtor-jurisdiction-iso3 "AFG"
                :matched-debts [{:instrument "tax_obligation" :origination-date "2010-01-01T00:00:00Z"}
                                {:instrument "loan" :origination-date "2015-01-01T00:00:00Z"}]})]
      (is (true? (:eligible out)))
      (is (= "R10" (:dmn-rule-fired out))))))

(deftest test-dmn-r11-political-amnesty-out-of-scope-rejects
  (let [out (run-eligibility-dmn
             {:debtor-sbt-level 1
              :debtor-entity-type "natural_person"
              :debtor-sbt-entity-type "natural_person"
              :debtor-community-member false
              :rite-type "political_amnesty"
              :rite-effective-date "2026-09-26T00:00:00Z"
              :rite-jurisdiction-scope ["AFG"]
              :debtor-jurisdiction-iso3 "USA"
              :matched-debts []})]
    (is (false? (:eligible out)))
    (is (= "R11" (:dmn-rule-fired out)))))

;; ─── Stub ports (reify of yobel.ports protocols; conftest.py analogues) ──

(defn stub-rite-registry [rites]
  (reify ports/RiteRegistryPort
    (get-rite [_ rite-id] (get rites rite-id))))

(defn stub-creditor-enrollment [debts-by-debtor]
  (reify ports/CreditorEnrollmentPort
    (find-debts-for-debtor [_ rite-id debtor-did]
      (get debts-by-debtor [rite-id debtor-did] []))
    (get-debt-for-release [_ _rite-id _creditor-did _debt-id _decryptor] nil)))

(defn stub-council-sbt [levels entity-types]
  (reify ports/CouncilSbtPort
    (balance-of-level [_ did] (get levels did 0))
    (entity-type-of [_ did] (get entity-types did "unknown"))))

(defn stub-charter-compliance [aligned jurisdictions]
  (reify ports/CharterCompliancePort
    (is-aligned [_ did] (contains? aligned did))
    (jurisdiction-of [_ did] (get jurisdictions did "ALL"))))

(defn stub-envelope-crypto []
  (let [counter (atom 0)]
    (reify ports/EnvelopeCryptoPort
      (envelope [_ _plaintext _recipients purpose]
        (ports/->EnvelopeCipher (str "ipfs://fake-cipher-" purpose "-" (swap! counter inc)))))))

(defn stub-anchor-bridge
  "Records writes in `writes-atom` as
  {:collection .. :rkey .. :payload {..kebab..} :anchor-to-base-l2 bool}."
  [writes-atom]
  (reify ports/AnchorBridgePort
    (write-and-anchor [_ collection rkey payload anchor-to-base-l2]
      (swap! writes-atom conj {:collection collection
                               :rkey rkey
                               :payload payload
                               :anchor-to-base-l2 anchor-to-base-l2})
      (ports/->AnchorResult (str "at://fake/" collection "/" rkey)
                            (if anchor-to-base-l2 "0xfake-anchor" "")))
    (batched-anchor [_ _contract cids]
      (ports/->BatchedAnchorResult (str "0xfake-batch-" (count cids))))))

(def shmita-5786
  (ports/->Rite "shmita-5786" "shmita_7yr" "active"
                "2026-09-26T00:00:00Z" nil
                "etzhayyim" ["ALL"]
                "did:web:etzhayyim.com" "Lev 25"))

;; ─── End-to-end via build-graph ──────────────────────────────────────

(deftest test-e2e-eligible-debtor-anchors-with-base-l2
  (let [writes (atom [])
        debtor "did:web:etzhayyim.com:debtor1"
        graph (build-graph
               {:checkpointer (cp/mem-checkpointer)
                :rite-registry-port (stub-rite-registry {"shmita-5786" shmita-5786})
                :creditor-enrollment-port
                (stub-creditor-enrollment
                 {["shmita-5786" debtor]
                  [(ports/->DebtRow "d1" debtor 100000000 0
                                    "2024-01-01T00:00:00Z" "loan")]})
                :council-sbt-port (stub-council-sbt {debtor 1} {debtor "natural_person"})
                :charter-compliance-port (stub-charter-compliance #{} {})
                :envelope-crypto (stub-envelope-crypto)
                :anchor-bridge (stub-anchor-bridge writes)})
        result (g/invoke graph
                         {:rite-id "shmita-5786"
                          :debtor-did debtor
                          :debtor-entity-type "natural_person"
                          :eligibility-proof "community-member-card-x123"}
                         {:thread-id "test-debtor-happy"})]
    (is (= "paired" (:pairing-status result)))
    (is (= 1 (count @writes)))
    (is (true? (:anchor-to-base-l2 (first @writes))))
    ;; payload fields the orchestrator later scans for
    (let [payload (:payload (first @writes))]
      (is (true? (:eligible payload)))
      (is (= debtor (:debtor-did payload)))
      (is (= "shmita-5786" (:rite-id payload))))))

(deftest test-e2e-ineligible-debtor-anchors-without-base-l2
  (testing "R12 (no SBT) → eligible=false → AT MST write but skip Base L2 anchor (gas savings)."
    (let [writes (atom [])
          graph (build-graph
                 {:checkpointer (cp/mem-checkpointer)
                  :rite-registry-port (stub-rite-registry {"shmita-5786" shmita-5786})
                  :creditor-enrollment-port (stub-creditor-enrollment {})
                  ;; NO SBT
                  :council-sbt-port (stub-council-sbt {} {})
                  :charter-compliance-port (stub-charter-compliance #{} {})
                  :envelope-crypto (stub-envelope-crypto)
                  :anchor-bridge (stub-anchor-bridge writes)})]
      (g/invoke graph
                {:rite-id "shmita-5786"
                 :debtor-did "did:web:no-sbt.example"
                 :eligibility-proof ""}
                {:thread-id "test-debtor-r12"})
      (is (= 1 (count @writes)))
      ;; Base L2 anchor SKIPPED for ineligible
      (is (false? (:anchor-to-base-l2 (first @writes)))))))
