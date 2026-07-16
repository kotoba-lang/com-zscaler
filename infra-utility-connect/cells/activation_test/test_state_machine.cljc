(ns infra-utility-connect.cells.activation-test.test-state-machine
  "Tests for infra-utility-connect activation_test state machine."
  (:require [clojure.test :refer [deftest is testing]]
            [infra-utility-connect.cells.activation-test.state-machine :as sm]))

(deftest chain-reaches-complete
  (let [out (sm/run-chain {"projectId" "ACT-004"})]
    (testing "phase is complete"
      (is (= "complete" (get-in out ["utility_state" "phase"]))))
    (testing "completionPct is 100"
      (is (= 100 (get-in out ["utility_state" "completionPct"]))))
    (testing "next_node is end"
      (is (= "end" (get out "next_node"))))))

(deftest project-id-propagates
  (is (= "ACTIVATE-1" (get-in (sm/run-chain {"projectId" "ACTIVATE-1"})
                               ["utility_state" "projectId"]))))

(deftest default-project-id
  (is (= "unknown" (get-in (sm/run-chain {}) ["utility_state" "projectId"]))))
