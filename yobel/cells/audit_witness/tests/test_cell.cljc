(ns yobel.cells.audit-witness.tests.test-cell
  "Tests for AuditWitnessCell — chain continuity, signing, tampering detection.

  Clojure port of cells/audit_witness/tests/test_cell.py (clojure.test)."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [langgraph.checkpoint :as cp]
            [langgraph.graph :as g]
            [yobel.cells.audit-witness.cell :as cell]
            [yobel.ports :as p]))

;; ─── Stub ports (mirror conftest.py fakes) ──────────────────────────

(defn fake-audit-log
  "Stub AuditLogPort. opts: :tail :chain-ok :consensus :batch.
  Returns {:port .. :appended atom :anchored-marks atom}."
  [& {:keys [tail chain-ok consensus batch]
      :or {chain-ok true
           consensus true
           batch (p/->AuditBatchStatus 0 0 [])}}]
  (let [appended (atom [])
        anchored-marks (atom [])
        counter (atom 0)]
    {:appended appended
     :anchored-marks anchored-marks
     :port
     (reify p/AuditLogPort
       (tail-signed-triple [_ _rite-id] tail)
       (verify-chain-link [_ _prev-signed-cid _next-state-root-before] chain-ok)
       (poll-replica-consensus [_ _rite-id _expected-prev-cid]
         (p/->ReplicaConsensus consensus))
       (append [_ rite-id source-kind prev-cid state-root-before state-root-after
                tx-digest witness-key-id signature-hex event-payload]
         (swap! appended conj {:rite-id rite-id
                               :source-kind source-kind
                               :prev-cid prev-cid
                               :state-root-before state-root-before
                               :state-root-after state-root-after
                               :tx-digest tx-digest
                               :witness-key-id witness-key-id
                               :signature-hex signature-hex
                               :event-payload event-payload})
         (swap! counter inc)
         (p/->AuditAppendResult (str "ipfs://fake-audit-" @counter)
                                (str "at://fake/audit/" @counter)))
       (batch-status [_] batch)
       (mark-anchored [_ cids tx-hash]
         (swap! anchored-marks conj [cids tx-hash])
         nil))}))

(defn fake-witness-keystore []
  (reify p/WitnessKeystorePort
    (current-key [_] (p/->WitnessKey "fake-witness-key-001"))
    (sign [_ _key-id payload]
      ;; conftest FakeWitnessKeystore: b"\x00"*32 + payload[:16]
      (str "0000000000000000000000000000000000000000000000000000000000000000"
           (subs payload 0 (min 16 (count payload)))))))

(defn fake-anchor-bridge []
  (reify p/AnchorBridgePort
    (write-and-anchor [_ collection rkey _payload anchor-to-base-l2]
      (p/->AnchorResult (str "at://fake/" collection "/" rkey)
                        (if anchor-to-base-l2 "0xfake-anchor" "")))
    (batched-anchor [_ _contract cids]
      (p/->BatchedAnchorResult (str "0xfake-batch-" (count cids))))))

(defn fake-public-fund
  "Returns {:port .. :grants atom}."
  []
  (let [grants (atom [])]
    {:grants grants
     :port (reify p/PublicFundPort
             (request-audit-grant [_ reason rite-id chain-break-reason]
               (swap! grants conj {:reason reason
                                   :rite-id rite-id
                                   :chain-break-reason chain-break-reason})
               nil))}))

(defn fake-council-notifier
  "Returns {:port .. :notifications atom}."
  []
  (let [notifications (atom [])]
    {:notifications notifications
     :port (reify p/CouncilNotifierPort
             (notify [_ targets event-type rite-id severity _payload]
               (swap! notifications conj {:targets targets
                                          :event-type event-type
                                          :rite-id rite-id
                                          :severity severity})
               (p/->NotificationResult (str "at://fake/incident/" rite-id))))}))

(defn- build-graph-with [checkpointer audit-log keystore bridge fund notifier]
  (cell/build-graph
   {:checkpointer checkpointer
    :audit-log-port (:port audit-log)
    :witness-keystore keystore
    :anchor-bridge bridge
    :public-fund-port (:port fund)
    :council-notifier (:port notifier)}))

;; ─── collect-state-diff ──────────────────────────────────────────────

(deftest test-collect-state-diff-passes-through-when-digest-present
  (let [out (cell/collect-state-diff
             {:state-root-before "0xaaaa"
              :state-root-after "0xbbbb"
              :tx-digest "0xdeadbeef"})]
    ;; When tx-digest present, function is a no-op pass-through
    (is (contains? out :tx-digest))
    (is (= "0xdeadbeef" (:tx-digest out)))))

(deftest test-collect-state-diff-fallback-digest-when-missing
  (let [out (cell/collect-state-diff
             {:state-root-before "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              :state-root-after "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"})]
    (is (str/includes? (:tx-digest out) "::"))
    (is (str/includes? (:tx-digest out) "0xaaaa"))
    (is (str/includes? (:tx-digest out) "0xbbbb"))))

;; ─── verify-chain-continuity ─────────────────────────────────────────

(deftest test-chain-continuity-genesis-no-prior-triple
  (let [audit-log (fake-audit-log :tail nil) ; No prior witness for this rite
        out (cell/verify-chain-continuity {:rite-id "shmita-5786"} (:port audit-log))]
    (is (true? (:chain-valid out)))
    (is (= "none" (:tampering-severity out)))))

(deftest test-chain-continuity-valid-link
  (let [audit-log (fake-audit-log :tail (p/->SignedTriple "ipfs://prev-1")
                                  :chain-ok true)
        out (cell/verify-chain-continuity
             {:rite-id "shmita-5786" :state-root-before "0xaa"}
             (:port audit-log))]
    (is (true? (:chain-valid out)))
    (is (= "none" (:tampering-severity out)))))

(deftest test-chain-break-single-node-is-suspicion
  (testing "Chain break + replicas disagree → 'suspicion' (not 'confirmed')."
    (let [audit-log (fake-audit-log :tail (p/->SignedTriple "ipfs://prev-1")
                                    :chain-ok false
                                    :consensus false) ; replicas DON'T agree
          out (cell/verify-chain-continuity
               {:rite-id "shmita-5786" :state-root-before "0xaa"}
               (:port audit-log))]
      (is (false? (:chain-valid out)))
      (is (= "suspicion" (:tampering-severity out))))))

(deftest test-chain-break-replica-consensus-is-confirmed
  (testing "Chain break + replicas agree → 'confirmed' tampering."
    (let [audit-log (fake-audit-log :tail (p/->SignedTriple "ipfs://prev-1")
                                    :chain-ok false
                                    :consensus true) ; replicas AGREE on chain break
          out (cell/verify-chain-continuity
               {:rite-id "shmita-5786" :state-root-before "0xaa"}
               (:port audit-log))]
      (is (false? (:chain-valid out)))
      (is (= "confirmed" (:tampering-severity out))))))

;; ─── End-to-end via build-graph: happy path ──────────────────────────

(deftest test-e2e-super-step-signed-and-logged
  (let [audit-log (fake-audit-log :tail nil) ; Genesis for this rite
        fund (fake-public-fund)
        notifier (fake-council-notifier)
        graph (build-graph-with (cp/mem-checkpointer) audit-log
                                (fake-witness-keystore) (fake-anchor-bridge)
                                fund notifier)
        result (g/invoke graph
                         {:source-kind "super_step"
                          :rite-id "shmita-5786"
                          :state-root-before "0xaa"
                          :state-root-after "0xbb"
                          :tx-digest "0xcc"
                          :event-payload {:step "declareRite"}}
                         {:thread-id "test-audit-happy"})]
    (is (str/starts-with? (:audit-event-uri result) "at://"))
    (is (= 1 (count @(:appended audit-log))))
    ;; No anchor yet (batch threshold not reached)
    (is (false? (get result :batch-anchored false)))
    ;; No tampering incident
    (is (or (not (contains? result :incident-uri))
            (empty? (:incident-uri result))))))

(deftest test-e2e-tampering-triggers-council-notification
  (testing "Chain break + replica consensus → mark superseded + Public Fund + Council."
    (let [audit-log (fake-audit-log :tail (p/->SignedTriple "ipfs://corrupt-prev")
                                    :chain-ok false
                                    :consensus true) ; CONFIRMED tampering
          fund (fake-public-fund)
          notifier (fake-council-notifier)
          graph (build-graph-with (cp/mem-checkpointer) audit-log
                                  (fake-witness-keystore) (fake-anchor-bridge)
                                  fund notifier)
          result (g/invoke graph
                           {:source-kind "super_step"
                            :rite-id "shmita-5786"
                            :state-root-before "0xnew"
                            :state-root-after "0xnewer"
                            :tx-digest "0xtx"
                            :event-payload {}}
                           {:thread-id "test-audit-tamper"})]
      ;; Tampering path: no audit append, no anchor — instead Public Fund + Council
      (is (= 0 (count @(:appended audit-log))))
      (is (= 1 (count @(:grants fund))))
      (is (= "yobel.tampering_detected" (:reason (first @(:grants fund)))))
      (is (= 1 (count @(:notifications notifier))))
      (is (some #{"council_lv9_chair"} (:targets (first @(:notifications notifier)))))
      (is (some #{"five_bootstrap_council"} (:targets (first @(:notifications notifier)))))
      (is (str/starts-with? (:incident-uri result) "at://")))))

(deftest test-e2e-anchor-batch-fires-at-threshold
  (testing "Once batch has ≥100 events, anchor-batch fires + Base L2 tx returned."
    (let [audit-log (fake-audit-log
                     :tail nil
                     :batch (p/->AuditBatchStatus
                             100 300 (mapv #(str "ipfs://e" %) (range 100))))
          fund (fake-public-fund)
          notifier (fake-council-notifier)
          graph (build-graph-with (cp/mem-checkpointer) audit-log
                                  (fake-witness-keystore) (fake-anchor-bridge)
                                  fund notifier)
          result (g/invoke graph
                           {:source-kind "super_step"
                            :rite-id "shmita-5786"
                            :state-root-before "0xaa"
                            :state-root-after "0xbb"
                            :tx-digest "0xcc"}
                           {:thread-id "test-audit-anchor"})]
      (is (true? (:batch-anchored result)))
      (is (str/starts-with? (:base-l2-anchor-tx-hash result) "0xfake-batch-"))
      ;; mark-anchored should have been called
      (is (= 1 (count @(:anchored-marks audit-log))))
      (is (str/starts-with? (second (first @(:anchored-marks audit-log)))
                            "0xfake-batch-")))))
