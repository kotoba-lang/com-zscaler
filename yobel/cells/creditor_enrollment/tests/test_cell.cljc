(ns yobel.cells.creditor-enrollment.tests.test-cell
  "Tests for CreditorEnrollmentCell — signedConsent verify, historical-record gate, §2(b) instrument gate.

  (Clojure port of tests/test_cell.py — clojure.test over langgraph-clj.)"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [langgraph.checkpoint :as cp]
            [langgraph.graph :as g]
            [yobel.cells.creditor-enrollment.cell :as cell]
            [yobel.ports :as ports]))

;; ─── Stub ports (conftest.py fixtures) ──────────────────────────────

(defn stub-rite-registry [rites-atom]
  (reify ports/RiteRegistryPort
    (get-rite [_ rite-id] (get @rites-atom rite-id))))

(defn stub-council-sbt [levels-atom]
  (reify ports/CouncilSbtPort
    (balance-of-level [_ did] (get @levels-atom did 0))
    (entity-type-of [_ did] "unknown")))

(defn stub-charter-compliance [aligned-atom]
  (reify ports/CharterCompliancePort
    (is-aligned [_ did] (contains? @aligned-atom did))
    (jurisdiction-of [_ _did] "ALL")))

(defn stub-erc725 [valid-signers-atom]
  (reify ports/Erc725Port
    (verify-eip712-signed-consent [_ signer-did _payload signature]
      (contains? @valid-signers-atom [signer-did signature]))))

(defn stub-envelope-crypto [counter-atom]
  (reify ports/EnvelopeCryptoPort
    (envelope [_ _plaintext _recipients purpose]
      (ports/->EnvelopeCipher
       (str "ipfs://fake-cipher-" purpose "-" (swap! counter-atom inc))))))

(defn stub-anchor-bridge [writes-atom]
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

(def shmita-rite
  (ports/map->Rite
   {:rite-id "shmita-5786"
    :rite-type "shmita_7yr"
    :status "active"
    :effective-date "2026-09-26T00:00:00Z"
    :expiry-date nil
    :scope "etzhayyim"
    :scope-jurisdictions ["ALL"]
    :issuer-did "did:web:etzhayyim.com:steward-001"
    :doctrinal-basis "Lev 25"}))

(defn build-test-env
  "Fresh stub-port environment per test (pytest fixture equivalent)."
  []
  (let [rites (atom {})
        levels (atom {})
        aligned (atom #{})
        valid-signers (atom #{})
        envelope-counter (atom 0)
        writes (atom [])]
    {:rites rites
     :levels levels
     :aligned aligned
     :valid-signers valid-signers
     :writes writes
     :checkpointer (cp/mem-checkpointer)
     :rite-registry (stub-rite-registry rites)
     :council-sbt (stub-council-sbt levels)
     :charter-compliance (stub-charter-compliance aligned)
     :erc725 (stub-erc725 valid-signers)
     :envelope-crypto (stub-envelope-crypto envelope-counter)
     :anchor-bridge (stub-anchor-bridge writes)}))

(defn build-test-graph [env]
  (cell/build-graph
   {:checkpointer (:checkpointer env)
    :rite-registry-port (:rite-registry env)
    :council-sbt-port (:council-sbt env)
    :charter-compliance-port (:charter-compliance env)
    :erc725-port (:erc725 env)
    :envelope-crypto (:envelope-crypto env)
    :anchor-bridge (:anchor-bridge env)}))

;; ─── validate-input ──────────────────────────────────────────────────

(deftest test-validate-input-happy
  (let [out (cell/validate-input
             {:creditor-did "did:web:creditor.example"
              :signed-consent "0xdeadbeef"
              :debts [{:debt-id "d1"
                       :debtor-did "did:web:debtor.example"
                       :principal-micro-usdc 100000000
                       :origination-date "2020-01-01T00:00:00Z"
                       :instrument "loan"}]})]
    (is (true? (:input-valid out)))))

(deftest test-validate-input-empty-debts-fails
  (let [out (cell/validate-input
             {:creditor-did "did:web:creditor.example"
              :signed-consent "0xdeadbeef"
              :debts []})]
    (is (false? (:input-valid out)))
    (is (some #(str/includes? % "debts[]") (:input-errors out)))))

(deftest test-validate-input-caps-at-1000
  (let [debts (mapv (fn [i] {:debt-id (str "d" i)
                             :principal-micro-usdc 1
                             :origination-date "2020-01-01T00:00:00Z"})
                    (range 1001))
        out (cell/validate-input
             {:creditor-did "did" :signed-consent "0x" :debts debts})]
    (is (false? (:input-valid out)))))

(deftest test-validate-input-negative-principal-fails
  (let [out (cell/validate-input
             {:creditor-did "did:web:creditor.example"
              :signed-consent "0xdeadbeef"
              :debts [{:debt-id "d1"
                       :principal-micro-usdc -1
                       :origination-date "2020-01-01T00:00:00Z"}]})]
    (is (false? (:input-valid out)))
    (is (some #(str/includes? % "principalMicroUsdc") (:input-errors out)))))

;; ─── historical-record-gate ──────────────────────────────────────────

(deftest test-historical-record-gate-accepts-pre-cycle-debt
  (let [out (cell/historical-record-gate
             {:rite-effective-date "2026-09-26T00:00:00Z"
              :debts [{:origination-date "2020-01-01T00:00:00Z"}
                      {:origination-date "2025-12-31T23:59:59Z"}]})]
    (is (true? (:historical-record-compliant out)))))

(deftest test-historical-record-gate-rejects-post-cycle-debt
  (let [out (cell/historical-record-gate
             {:rite-effective-date "2026-09-26T00:00:00Z"
              :debts [{:origination-date "2020-01-01T00:00:00Z"}
                      ;; AFTER cycle start
                      {:origination-date "2026-12-01T00:00:00Z"}]})]
    (is (false? (:historical-record-compliant out)))
    (is (some #(str/includes? % "new debt origination not allowed")
              (:historical-record-violations out)))))

;; ─── instrument-safety-gate ──────────────────────────────────────────

(deftest test-instrument-safety-gate-accepts-normal-instruments
  (let [out (cell/instrument-safety-gate
             {:debts [{:instrument "loan"}
                      {:instrument "promissory_note"}
                      {:instrument "mortgage"}]})]
    (is (true? (:instrument-safety-compliant out)))))

(deftest test-instrument-safety-gate-rejects-margin-call
  (testing "Charter Rider §2(b) — defense in depth even though lexicon enum excludes."
    (let [out (cell/instrument-safety-gate
               {:debts [{:instrument "loan"}
                        {:instrument "margin_call"}]})]
      (is (false? (:instrument-safety-compliant out)))
      (is (some #(str/includes? % "§2(b)") (:instrument-violations out))))))

;; ─── End-to-end via build-graph ──────────────────────────────────────

(deftest test-e2e-happy-path
  (let [env (build-test-env)]
    (swap! (:rites env) assoc "shmita-5786" shmita-rite)
    (swap! (:levels env) assoc "did:web:creditor.example" 2)
    (swap! (:valid-signers env) conj ["did:web:creditor.example" "0xgoodsig"])
    (let [graph (build-test-graph env)
          result (g/invoke graph
                           {:rite-id "shmita-5786"
                            :creditor-did "did:web:creditor.example"
                            :signed-consent "0xgoodsig"
                            :debts [{:debt-id "d1"
                                     :debtor-did "did:web:debtor1.example"
                                     :principal-micro-usdc 500000000 ; $500 USDC
                                     :accrued-micro-usdc 5000000
                                     :origination-date "2022-01-01T00:00:00Z"
                                     :instrument "loan"}]}
                           {:thread-id "test-credit-happy"})]
      (is (= 1 (:debt-count result)))
      (is (str/starts-with? (:enrollment-vertex-uri result) "at://"))
      (is (= 1 (count @(:writes env)))))))

(deftest test-e2e-rejects-bad-signature
  (let [env (build-test-env)]
    (swap! (:rites env) assoc "shmita-5786" shmita-rite)
    (swap! (:levels env) assoc "did:web:creditor.example" 2)
    ;; Note: erc725 valid-signers is EMPTY — signature is bogus
    (let [graph (build-test-graph env)
          result (g/invoke graph
                           {:rite-id "shmita-5786"
                            :creditor-did "did:web:creditor.example"
                            :signed-consent "0xbadsig"
                            :debts [{:debt-id "d1"
                                     :debtor-did "did:web:debtor.example"
                                     :principal-micro-usdc 100000000
                                     :origination-date "2022-01-01T00:00:00Z"
                                     :instrument "loan"}]}
                           {:thread-id "test-credit-badsig"})]
      (is (= "" (:enrollment-vertex-uri result)))
      (is (zero? (count @(:writes env)))))))

(deftest test-e2e-rejects-post-cycle-debt
  (let [env (build-test-env)]
    (swap! (:rites env) assoc "shmita-5786" shmita-rite)
    (swap! (:levels env) assoc "did:web:creditor.example" 2)
    (swap! (:valid-signers env) conj ["did:web:creditor.example" "0xgoodsig"])
    (let [graph (build-test-graph env)
          result (g/invoke graph
                           {:rite-id "shmita-5786"
                            :creditor-did "did:web:creditor.example"
                            :signed-consent "0xgoodsig"
                            :debts [{:debt-id "d1"
                                     :debtor-did "did:web:debtor.example"
                                     :principal-micro-usdc 100000000
                                     ;; POST cycle — rejected
                                     :origination-date "2027-01-01T00:00:00Z"
                                     :instrument "loan"}]}
                           {:thread-id "test-credit-postcycle"})]
      (is (= "" (:enrollment-vertex-uri result)))
      (is (zero? (count @(:writes env)))))))
