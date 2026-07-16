(ns kuni-umi.cells.deployment-planning.test-cell
  "kuni-umi 国産み DeploymentPlanningCell smoke + invariant tests.

  cell.py has no cell-level Python test (the actor's test_agent.py exercises the
  flat py/agent.py module, not the cells). These assertions pin the ported cell's
  pure surface (proportionality-check) + its R0 substrate gates + the graph
  wiring, mirroring the site-survey test."
  (:require [clojure.test :refer [deftest is]]
            [kuni-umi.cells.deployment-planning.cell :as cell]))

(deftest closed-enums-preserve-python-value-identities
  (is (= "accept" (:accept cell/decisions)))
  (is (= "awaiting-governance" (:awaiting-governance cell/decisions)))
  (is (= "awaiting-force-authorization" (:awaiting-force-authorization cell/decisions)))
  (is (= "awaiting-public-fund" (:awaiting-public-fund cell/decisions)))
  (is (= 5 (count cell/decisions))))

(deftest proportionality-check-is-the-only-pure-node
  (let [deps (cell/make-cell-deps)]
    ;; populationImpacted > 100 → requiresGovernance true
    (let [out (cell/proportionality-check (assoc cell/deployment-planning-state
                                                 :populationImpacted 250) deps)]
      (is (true? (:requiresGovernance out)))
      (is (false? (:requiresPublicFund out))))
    ;; at/below threshold → false
    (let [out (cell/proportionality-check (assoc cell/deployment-planning-state
                                                 :populationImpacted 100) deps)]
      (is (false? (:requiresGovernance out))))
    ;; missing populationImpacted defaults to 0 (Python state.get default)
    (let [out (cell/proportionality-check cell/deployment-planning-state deps)]
      (is (false? (:requiresGovernance out))))))

(deftest governance-threshold-is-100
  (is (= 100 cell/governance-population-threshold)))

(deftest substrate-gated-nodes-raise-at-r0
  (let [deps (cell/make-cell-deps)]
    (doseq [f [cell/derive-target-topology cell/bom-generation
               cell/counterparty-classification cell/payment-plan
               cell/fleet-allocation cell/propose-plan]]
      (let [e (try (f cell/deployment-planning-state deps) nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (some? e))
        (is (true? (:kuni-umi/not-implemented (ex-data e))))))))

(deftest build-graph-wiring-is-faithful
  (let [g (cell/build-graph (cell/make-cell-deps))]
    (is (= 7 (count (:nodes g))))
    (is (contains? (:steps g) "proportionality_check"))
    ;; linear graph: no conditional edge, START→derive, propose→END
    (is (some #{["START" "derive_target_topology"]} (:edges g)))
    (is (some #{["propose_plan" "END"]} (:edges g)))
    ;; the pure node is callable through the graph; a gated one raises
    (is (true? (:requiresGovernance
                ((get-in g [:steps "proportionality_check"])
                 (assoc cell/deployment-planning-state :populationImpacted 250)))))
    (is (thrown? clojure.lang.ExceptionInfo
                 ((get-in g [:steps "bom_generation"]) cell/deployment-planning-state)))))

(deftest event-shims-match-python
  (let [ev {"uri" "at://x" "cid" "bafy" "indexedAt" "2026-06-12"
            "rkey" "plan-001" "value" {"siteDid" "did:web:etzhayyim.com:site:001"
                                       "populationImpacted" 42}}
        st (cell/state-from-event ev cell/trigger-nsid)]
    (is (= 42 (get st "populationImpacted")))
    (is (= "at://x" (get st "_event_uri")))
    (is (= "DeploymentPlanningCell:did:web:etzhayyim.com:site:001"
           (cell/thread-id-from-event ev cell/trigger-nsid)))
    (is (= "DeploymentPlanningCell:fallback"
           (cell/thread-id-from-event {"rkey" "fallback" "value" {}} cell/trigger-nsid)))))

(deftest nsids-are-the-charter-constants
  (is (= "com.etzhayyim.apps.etzhayyim.kuniUmi.submitSiteSurvey" cell/trigger-nsid))
  (is (= "com.etzhayyim.apps.etzhayyim.kuniUmi.proposeDeploymentPlan" cell/submit-nsid)))

(deftest healthz-extra-reports-phase-2
  (let [h (cell/healthz-extra (cell/make-cell-deps))]
    (is (= "2-planning" (get h "phase")))
    (is (= cell/trigger-nsid (get h "trigger_nsid")))))
