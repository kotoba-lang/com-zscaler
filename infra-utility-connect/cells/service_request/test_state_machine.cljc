(ns infra-utility-connect.cells.service-request.test-state-machine
  "Tests for infra-utility-connect service_request state machine."
  (:require [clojure.test :refer [deftest is testing]]
            [infra-utility-connect.cells.service-request.state-machine :as sm]))

(deftest chain-reaches-complete
  (let [out (sm/run-chain {"projectId" "PRJ-001"})]
    (testing "phase is complete"
      (is (= "complete" (get-in out ["utility_state" "phase"]))))
    (testing "completionPct is 100"
      (is (= 100 (get-in out ["utility_state" "completionPct"]))))
    (testing "next_node is end"
      (is (= "end" (get out "next_node"))))))

(deftest project-id-propagates
  (is (= "MY-PROJECT" (get-in (sm/run-chain {"projectId" "MY-PROJECT"})
                               ["utility_state" "projectId"]))))

(deftest default-project-id
  (is (= "unknown" (get-in (sm/run-chain {}) ["utility_state" "projectId"]))))
