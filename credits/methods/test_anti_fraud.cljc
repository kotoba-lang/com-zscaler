(ns credits.methods.test-anti-fraud
  "credits -- unit tests for methods.anti-fraud (rate limits, high-value reject,
  reputation gate, duplicate reward -- G4/G5/G6/G7)."
  (:require [clojure.test :refer [deftest is]]
            [credits.methods.anti-fraud :as af]))

;; G4 -- spend rate limit
(deftest test-spend-allowed-under-limit
  (is (true? (:allowed (af/check-spend-allowed {:recent-spend-count 59})))))

(deftest test-spend-denied-at-limit
  (is (= {:allowed false :reason "spend-rate-limit-exceeded"}
         (af/check-spend-allowed {:recent-spend-count 60}))))

;; G4 -- earn rate limit
(deftest test-earn-allowed-under-limit
  (is (true? (:allowed (af/check-earn-allowed {:recent-earn-count 29})))))

(deftest test-earn-denied-at-limit
  (is (= "earn-rate-limit-exceeded"
         (:reason (af/check-earn-allowed {:recent-earn-count 30})))))

;; G5 -- high-value earn reject
(deftest test-earn-allowed-at-threshold
  (is (true? (:allowed (af/check-earn-allowed {:amount 50})))))

(deftest test-earn-denied-above-threshold
  (is (= "high-value-earn-reject"
         (:reason (af/check-earn-allowed {:amount 50.01})))))

;; G6 -- HC reputation gate
(deftest test-earn-allowed-at-reputation-floor
  (is (true? (:allowed (af/check-earn-allowed {:approval-rate 0.5})))))

(deftest test-earn-denied-below-reputation-floor
  (is (= "hc-reputation-gate"
         (:reason (af/check-earn-allowed {:approval-rate 0.49})))))

;; G7 -- duplicate reward
(deftest test-earn-denied-on-duplicate-task
  (is (= "duplicate-reward-task"
         (:reason (af/check-earn-allowed {:task-id "t1" :seen-task-ids #{"t1"}})))))

(deftest test-earn-denied-on-duplicate-session
  (is (= "duplicate-reward-session"
         (:reason (af/check-earn-allowed {:session-id "s1" :seen-session-ids #{"s1"}})))))

(deftest test-earn-allowed-on-fresh-task-and-session
  (is (true? (:allowed (af/check-earn-allowed {:task-id "t2" :session-id "s2"
                                                :seen-task-ids #{"t1"} :seen-session-ids #{"s1"}})))))

;; gate ordering: rate limit checked before high-value / reputation / duplicate
(deftest test-rate-limit-checked-first
  (is (= "earn-rate-limit-exceeded"
         (:reason (af/check-earn-allowed {:recent-earn-count 30 :amount 999
                                           :approval-rate 0.0 :task-id "dup"
                                           :seen-task-ids #{"dup"}})))))
