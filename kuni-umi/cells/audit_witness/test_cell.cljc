(ns kuni-umi.cells.audit-witness.test-cell
  "kuni-umi 国産み AuditWitnessCell smoke + invariant tests. Pins the pure
  verify-signatures / compare-blobs logic, the N≥2 invariant, both routers, the
  R0 substrate gates, and the event shims."
  (:require [clojure.test :refer [deftest is]]
            [kuni-umi.cells.audit-witness.cell :as cell]))

(deftest closed-enums-preserve-python-value-identities
  (is (= "injury" (:injury cell/event-classes)))
  (is (= "community-event" (:community-event cell/event-classes)))
  (is (= 5 (count cell/event-classes))))

(deftest witness-invariant-is-two
  (is (= 2 cell/witness-invariant-min))
  (is (= 2 (get (cell/healthz-extra (cell/make-cell-deps)) "witness_invariant_min"))))

(deftest verify-signatures-counts-signed-attestations
  (let [deps (cell/make-cell-deps)]
    (is (= 2 (:signaturesVerified
              (cell/verify-signatures
               {:witnessAttestations [{:signature "a"} {:signature "b"} {}]} deps))))
    (is (= 0 (:signaturesVerified
              (cell/verify-signatures cell/audit-witness-state deps))))))

(deftest compare-blobs-sets-consistency-and-violation
  (let [deps (cell/make-cell-deps)]
    ;; 2 signed + matching blob hashes → consistent, not violated
    (let [s (-> {:witnessAttestations [{:signature "a" :blobHash "h"}
                                       {:signature "b" :blobHash "h"}]}
                (cell/verify-signatures deps)
                (cell/compare-blobs deps))]
      (is (true? (:blobConsistencyOk s)))
      (is (false? (:invariantViolated s))))
    ;; mismatched blob hashes → inconsistent → violated
    (let [s (-> {:witnessAttestations [{:signature "a" :blobHash "h1"}
                                       {:signature "b" :blobHash "h2"}]}
                (cell/verify-signatures deps)
                (cell/compare-blobs deps))]
      (is (false? (:blobConsistencyOk s)))
      (is (true? (:invariantViolated s))))
    ;; only 1 signature → violated regardless
    (let [s (-> {:witnessAttestations [{:signature "a" :blobHash "h"}]}
                (cell/verify-signatures deps)
                (cell/compare-blobs deps))]
      (is (true? (:invariantViolated s))))
    ;; no blob hashes → consistency false
    (let [s (-> {:witnessAttestations [{:signature "a"} {:signature "b"}]}
                (cell/verify-signatures deps)
                (cell/compare-blobs deps))]
      (is (false? (:blobConsistencyOk s)))
      (is (true? (:invariantViolated s))))))

(deftest routers-branch-faithfully
  ;; consistency_router
  (is (= "escalate_mismatch" (cell/consistency-router {:invariantViolated true})))
  (is (= "attest_record" (cell/consistency-router {:invariantViolated false})))
  ;; attest_router: injury/community-event → feedback_phenotype, else END
  (is (= "feedback_phenotype" (cell/attest-router {:eventClass "injury"})))
  (is (= "feedback_phenotype" (cell/attest-router {:eventClass "community-event"})))
  (is (= "END" (cell/attest-router {:eventClass "anomaly"})))
  (is (= "END" (cell/attest-router {}))))

(deftest substrate-gated-nodes-raise-at-r0
  (let [deps (cell/make-cell-deps)]
    (doseq [f [cell/poll-witnesses cell/attest-record
               cell/escalate-mismatch cell/feedback-phenotype]]
      (let [e (try (f cell/audit-witness-state deps) nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (some? e))
        (is (true? (:kuni-umi/not-implemented (ex-data e))))))))

(deftest build-graph-wiring-is-faithful
  (let [g (cell/build-graph (cell/make-cell-deps))]
    (is (= 6 (count (:nodes g))))
    (is (= cell/consistency-router (get-in g [:conditional-edges "compare_blobs"])))
    (is (= cell/attest-router (get-in g [:conditional-edges "attest_record"])))
    (is (some #{["escalate_mismatch" "END"]} (:edges g)))
    ;; pure nodes callable through the graph
    (is (= 1 (:signaturesVerified
              ((get-in g [:steps "verify_signatures"])
               {:witnessAttestations [{:signature "a"}]}))))))

(deftest event-shims-match-python
  (let [ev {"uri" "at://x" "rkey" "audit-001" "value" {"eventClass" "injury"}}
        st (cell/state-from-event ev cell/trigger-nsid)]
    (is (= "injury" (get st "eventClass")))
    (is (= "AuditWitnessCell:audit-001" (cell/thread-id-from-event ev cell/trigger-nsid)))
    (is (= "AuditWitnessCell:unknown"
           (cell/thread-id-from-event {"value" {}} cell/trigger-nsid)))))

(deftest nsid-is-the-charter-constant
  (is (= "com.etzhayyim.apps.etzhayyim.kuniUmi.recordPhysicalAuditEvent" cell/trigger-nsid)))
