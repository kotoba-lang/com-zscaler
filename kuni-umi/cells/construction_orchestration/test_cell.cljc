(ns kuni-umi.cells.construction-orchestration.test-cell
  "kuni-umi 国産み ConstructionOrchestrationCell smoke + invariant tests.
  Pins the witness-router branches, the cadence-≤10 build-graph invariant, the
  R0 substrate gates, and the event shims (mirrors site-survey test)."
  (:require [clojure.test :refer [deftest is]]
            [kuni-umi.cells.construction-orchestration.cell :as cell]))

(deftest closed-enums-preserve-python-value-identities
  (is (= "handoff-ready" (:handoff-ready cell/phases)))
  (is (= "in-progress" (:in-progress cell/phases)))
  (is (= 5 (count cell/phases))))

(deftest witness-router-enforces-n-ge-2-and-branches
  ;; < 2 attestations → constitutional violation → handler_anomaly
  (is (= "handler_anomaly" (cell/witness-router {:witnessAttestations []})))
  (is (= "handler_anomaly" (cell/witness-router {:witnessAttestations [{}]})))
  ;; 2 attestations + anomaly flag → handler_anomaly
  (is (= "handler_anomaly" (cell/witness-router {:witnessAttestations [{} {}]
                                                 :anomalyFlags ["sensor-drift"]})))
  ;; 2 attestations, no anomaly, handoff-ready → complete_handoff
  (is (= "complete_handoff" (cell/witness-router {:witnessAttestations [{} {}]
                                                  :phase "handoff-ready"})))
  ;; 2 attestations, no anomaly, mid-build → next super-step
  (is (= "super_step_loop" (cell/witness-router {:witnessAttestations [{} {}]
                                                 :phase "in-progress"})))
  ;; missing witnessAttestations defaults to [] (Python state.get default)
  (is (= "handler_anomaly" (cell/witness-router {}))))

(deftest witness-invariant-is-two
  (is (= 2 cell/witness-invariant-min)))

(deftest build-graph-enforces-cadence-ceiling
  ;; default cadence (10) builds fine
  (let [g (cell/build-graph (cell/make-cell-deps))]
    (is (= 6 (count (:nodes g))))
    (is (= cell/witness-router (get-in g [:conditional-edges "witness_verify"])))
    (is (some #{["handler_anomaly" "END"]} (:edges g)))
    (is (some #{["complete_handoff" "END"]} (:edges g))))
  ;; cadence > 10 → constitutional violation raises
  (let [e (try (cell/build-graph (cell/make-cell-deps :config {"cadence_hz_max" 60})) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (some? e))
    (is (true? (:kuni-umi/cadence-violation (ex-data e))))
    (is (= 60 (:cadence-hz-max (ex-data e))))))

(deftest substrate-gated-nodes-raise-at-r0
  (let [deps (cell/make-cell-deps)]
    (doseq [f [cell/dispatch-fleet cell/super-step-loop cell/stream-progress
               cell/witness-verify cell/handler-anomaly cell/complete-handoff]]
      (let [e (try (f cell/construction-orchestration-state deps) nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (some? e))
        (is (true? (:kuni-umi/not-implemented (ex-data e))))))))

(deftest event-shims-match-python
  (let [ev {"uri" "at://x" "rkey" "build-001"
            "value" {"planDid" "did:web:etzhayyim.com:plan:001"}}
        st (cell/state-from-event ev cell/trigger-nsid)]
    (is (= "did:web:etzhayyim.com:plan:001" (get st "planDid")))
    (is (= "at://x" (get st "_event_uri")))
    (is (= "ConstructionOrchestrationCell:did:web:etzhayyim.com:plan:001"
           (cell/thread-id-from-event ev cell/trigger-nsid)))
    (is (= "ConstructionOrchestrationCell:fallback"
           (cell/thread-id-from-event {"rkey" "fallback" "value" {}} cell/trigger-nsid)))))

(deftest healthz-extra-reports-phase-3-and-cadence
  (let [h (cell/healthz-extra (cell/make-cell-deps))]
    (is (= "3-construction" (get h "phase")))
    (is (= 10 (get h "cadence_hz_max")))
    (is (= 2 (get h "witness_invariant_min")))))

(deftest nsids-are-the-charter-constants
  (is (= "com.etzhayyim.apps.etzhayyim.kuniUmi.proposeDeploymentPlan" cell/trigger-nsid))
  (is (= "com.etzhayyim.apps.etzhayyim.kuniUmi.recordConstructionProgress" cell/progress-nsid)))
