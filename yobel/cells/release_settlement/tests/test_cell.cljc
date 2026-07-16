(ns yobel.cells.release-settlement.tests.test-cell
  "Tests for ReleaseSettlementCell — tax warning DMN, one-way boundary, 5-way method dispatch.

  Clojure port of cells/release_settlement/tests/test_cell.py."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [langgraph.checkpoint :as cp]
            [langgraph.graph :as g]
            [yobel.cells.release-settlement.cell :as cell]
            [yobel.ports :as ports]))

;; ─── Stub ports (conftest.py fakes, as protocol reifications) ────────

(def fake-tx-hash "0xfake-tx-hash")

(defn make-creditor-enrollment-port
  "debts-atom: {[rite-id creditor-did debt-id] DebtRow}"
  [debts-atom]
  (reify ports/CreditorEnrollmentPort
    (find-debts-for-debtor [_ _rite-id _debtor-did] [])
    (get-debt-for-release [_ rite-id creditor-did debt-id _decryptor]
      (get @debts-atom [rite-id creditor-did debt-id]))))

(defn make-envelope-crypto []
  (let [counter (atom 0)]
    (reify ports/EnvelopeCryptoPort
      (envelope [_ _plaintext _recipients purpose]
        (ports/->EnvelopeCipher (str "ipfs://fake-cipher-" purpose "-" (swap! counter inc)))))))

(defn make-base-l2-paymaster
  ([] (make-base-l2-paymaster {}))
  ([{:keys [revert?]}]
   (reify ports/BaseL2PaymasterPort
     (release-usdc [_ from-did _to-did _amount-micro-usdc _tithe-neutral _rite-id]
       (if revert?
         (throw (ex-info (str "paymaster revert: insufficient vault balance for " from-did) {}))
         (ports/->BaseL2Tx fake-tx-hash))))))

(defn make-lawfirm-invoke []
  (reify ports/LawfirmInvokePort
    (invoke [_ _method _state]
      (ports/->LawfirmInvokeResult true "at://fake/lawfirm/result"))))

(defn make-anchor-bridge
  "Records writes in writes-atom as
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

(defn make-audit-witness-emit
  "Records events in events-atom as {:event-type .. & kwargs}."
  [events-atom]
  (reify ports/AuditWitnessEmitPort
    (emit [_ event-type kwargs]
      (swap! events-atom conj (assoc kwargs :event-type event-type))
      nil)))

;; ─── tax-warning-dmn (COLLECT hit) ───────────────────────────────────

(deftest test-tax-dmn-usa-caution-severity
  (let [out (cell/tax-warning-dmn
             {:debtor-did "did:web:debtor.usa.example"
              :creditor-did "did:web:creditor.example"
              :released-micro-usdc 100000000 ; $100
              :release-method "base_l2_transfer"})]
    (is (= "caution" (:tax-severity out)))
    (is (some #(str/includes? % "IRC §61(a)(11)") (:tax-warnings out)))
    (is (true? (:consult-legal-delegate out)))))

(deftest test-tax-dmn-japan-caution
  (let [out (cell/tax-warning-dmn
             {:debtor-did "did:web:debtor.jpn.example"
              :creditor-did "did:web:creditor.example"
              :released-micro-usdc 1000000000 ; $1000
              :release-method "voluntary_bookkeeping"})]
    (is (some #(str/includes? % "日本所得税法") (:tax-warnings out)))
    (is (= "caution" (:tax-severity out)))))

(deftest test-tax-dmn-high-severity-at-1m-usdc
  (let [out (cell/tax-warning-dmn
             {:debtor-did "did:web:debtor.usa.example"
              :creditor-did "did:web:creditor.usa.example"
              :released-micro-usdc (* 1000000 1000000) ; $1M USDC
              :release-method "base_l2_transfer"})]
    (is (= "high" (:tax-severity out)))
    (is (some #(str/includes? % "disguised-gift") (:tax-warnings out)))))

(deftest test-tax-dmn-form-1099c-threshold
  (let [out (cell/tax-warning-dmn
             {:debtor-did "did:web:debtor.usa.example"
              :creditor-did "did:web:creditor.usa.example"
              :released-micro-usdc (* 700 1000000) ; $700 (≥ $600 threshold)
              :release-method "voluntary_bookkeeping"})]
    (is (some #(str/includes? % "1099-C") (:tax-warnings out)))))

(deftest test-tax-dmn-no-warnings-below-thresholds
  (let [out (cell/tax-warning-dmn
             {:debtor-did "did:web:debtor.unknown.example"
              :creditor-did "did:web:creditor.unknown.example"
              :released-micro-usdc 0
              :release-method "voluntary_bookkeeping"})]
    (is (= "info" (:tax-severity out)))
    (is (false? (:consult-legal-delegate out)))))

;; ─── one-way-boundary-check (Charter Rider §2(b)) ────────────────────

(deftest test-one-way-check-accepts-equal-to-cap
  (let [out (cell/one-way-boundary-check
             {:released-micro-usdc 100000000
              :debt-principal-micro-usdc 90000000
              :debt-accrued-micro-usdc 10000000})]
    (is (true? (:one-way-compliant out)))))

(deftest test-one-way-check-rejects-over-release
  (let [out (cell/one-way-boundary-check
             {:released-micro-usdc 200000000
              :debt-principal-micro-usdc 90000000
              :debt-accrued-micro-usdc 10000000})]
    (is (false? (:one-way-compliant out)))
    (is (str/includes? (:one-way-violation out) "one-way invariant"))))

(deftest test-one-way-check-rejects-negative
  (let [out (cell/one-way-boundary-check
             {:released-micro-usdc -1
              :debt-principal-micro-usdc 100000000
              :debt-accrued-micro-usdc 0})]
    (is (false? (:one-way-compliant out)))
    (is (str/includes? (:one-way-violation out) "negative"))))

;; ─── execute-release: 5-way dispatch ─────────────────────────────────

(deftest test-execute-release-voluntary-bookkeeping-no-tx
  (let [out (cell/execute-release
             {:release-method "voluntary_bookkeeping" :creditor-did "c" :debtor-did "d"
              :released-micro-usdc 100 :rite-id "r"}
             nil (make-base-l2-paymaster) (make-lawfirm-invoke))]
    (is (true? (:settlement-ok out)))
    (is (= "" (:base-l2-tx-hash out)))
    (is (= "" (:lawfirm-invoke-uri out)))))

(deftest test-execute-release-base-l2-calls-paymaster
  (let [out (cell/execute-release
             {:release-method "base_l2_transfer" :creditor-did "c" :debtor-did "d"
              :released-micro-usdc 100 :rite-id "r"}
             nil (make-base-l2-paymaster) (make-lawfirm-invoke))]
    (is (true? (:settlement-ok out)))
    (is (= fake-tx-hash (:base-l2-tx-hash out)))))

(deftest test-execute-release-base-l2-revert-recorded
  (let [out (cell/execute-release
             {:release-method "base_l2_transfer" :creditor-did "c" :debtor-did "d"
              :released-micro-usdc 100 :rite-id "r"}
             nil (make-base-l2-paymaster {:revert? true}) (make-lawfirm-invoke))]
    (is (false? (:settlement-ok out)))
    (is (str/includes? (:settlement-error out) "revert"))))

(deftest test-execute-release-court-order-invokes-lawfirm
  (let [out (cell/execute-release
             {:release-method "court_order" :creditor-did "c" :debtor-did "d"
              :released-micro-usdc 100 :rite-id "r"}
             nil (make-base-l2-paymaster) (make-lawfirm-invoke))]
    (is (true? (:settlement-ok out)))
    (is (str/starts-with? (:lawfirm-invoke-uri out) "at://fake/lawfirm"))))

(deftest test-execute-release-sovereign-decree-invokes-lawfirm
  (let [out (cell/execute-release
             {:release-method "sovereign_decree" :creditor-did "c" :debtor-did "d"
              :released-micro-usdc 100 :rite-id "r"}
             nil (make-base-l2-paymaster) (make-lawfirm-invoke))]
    (is (true? (:settlement-ok out)))))

(deftest test-execute-release-ecclesiastical-indulgence-no-tx
  (let [out (cell/execute-release
             {:release-method "ecclesiastical_indulgence" :creditor-did "c" :debtor-did "d"
              :released-micro-usdc 0 :rite-id "r"}
             nil (make-base-l2-paymaster) (make-lawfirm-invoke))]
    (is (true? (:settlement-ok out)))))

;; ─── End-to-end via build-graph ──────────────────────────────────────

(deftest test-e2e-happy-path-base-l2
  (let [debts (atom {["shmita-5786" "did:web:creditor.example" "d1"]
                     (ports/map->DebtRow
                      {:debt-id "d1" :debtor-did "did:web:debtor.example"
                       :principal-micro-usdc 100000000 :accrued-micro-usdc 5000000
                       :origination-date "2022-01-01T00:00:00Z" :instrument "loan"})})
        writes (atom [])
        events (atom [])
        graph (cell/build-graph
               {:checkpointer (cp/mem-checkpointer)
                :creditor-enrollment-port (make-creditor-enrollment-port debts)
                :envelope-crypto (make-envelope-crypto)
                :tithe-router-port nil
                :base-l2-paymaster (make-base-l2-paymaster)
                :lawfirm-invoke (make-lawfirm-invoke)
                :anchor-bridge (make-anchor-bridge writes)
                :audit-witness-emit (make-audit-witness-emit events)})
        result (g/invoke graph
                         {:release-id "rel-shmita-5786-d1"
                          :rite-id "shmita-5786"
                          :debt-id "d1"
                          :debtor-did "did:web:debtor.example"
                          :creditor-did "did:web:creditor.example"
                          :release-method "base_l2_transfer"
                          :released-micro-usdc 100000000
                          :released-at "2026-10-01T00:00:00Z"}
                         {:thread-id "test-release-happy"})]
    (is (str/starts-with? (:release-vertex-uri result) "at://"))
    (is (= 1 (count @writes)))
    (is (= 1 (count @events)))
    (is (= "yobel.release_finalized" (:event-type (first @events))))))

(deftest test-e2e-one-way-violation-blocks-anchor
  (testing "released > principal+accrued → Charter Rider §2(b) blocks before settlement"
    (let [debts (atom {["shmita-5786" "did:web:creditor.example" "d1"]
                       (ports/map->DebtRow
                        {:debt-id "d1" :debtor-did "did:web:debtor.example"
                         :principal-micro-usdc 10000000 :accrued-micro-usdc 0 ; only $10
                         :origination-date "2022-01-01T00:00:00Z" :instrument "loan"})})
          writes (atom [])
          events (atom [])
          graph (cell/build-graph
                 {:checkpointer (cp/mem-checkpointer)
                  :creditor-enrollment-port (make-creditor-enrollment-port debts)
                  :envelope-crypto (make-envelope-crypto)
                  :tithe-router-port nil
                  :base-l2-paymaster (make-base-l2-paymaster)
                  :lawfirm-invoke (make-lawfirm-invoke)
                  :anchor-bridge (make-anchor-bridge writes)
                  :audit-witness-emit (make-audit-witness-emit events)})
          result (g/invoke graph
                           {:release-id "rel-bad"
                            :rite-id "shmita-5786"
                            :debt-id "d1"
                            :debtor-did "did:web:debtor.example"
                            :creditor-did "did:web:creditor.example"
                            :release-method "base_l2_transfer"
                            :released-micro-usdc 100000000 ; Trying to release $100 from $10 debt
                            :released-at "2026-10-01T00:00:00Z"}
                           {:thread-id "test-release-violation"})]
      (is (false? (:settlement-ok result)))
      (is (str/includes? (:settlement-error result) "one-way invariant"))
      (is (= 0 (count @writes)))
      (is (= 0 (count @events))))))
