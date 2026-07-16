(ns tasuke.methods.test-analyze
  "End-to-end membrane tests for 助 (tasuke) — every case journey costs the victim ¥0.
  1:1 port of `methods/test_analyze.py` (clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [tasuke.methods.analyze :as analyze]))

#?(:clj (def RES (delay (analyze/run (analyze/load-seed)))))

(deftest test-run-over-seed-is-all-free
  (let [res @RES]
    (is (= 0 (get res "total_cost")))
    (is (seq (get res "rows")))
    (doseq [r (get res "rows")]
      (is (= 0 (get r "cost")))
      (is (= false (get r "paid_referral"))))))

(deftest test-every-case-generates-member-authored-docs
  (doseq [r (get @RES "rows")]
    (is (seq (get r "docs")))                          ;; at least the police core set
    (is (some #{"damage-report"} (get r "docs")))))

(deftest test-unauthorized-transfer-gets-bank-request
  (let [fund (first (filter #(= (get % "kind") "unauthorized-transfer") (get @RES "rows")))]
    (is (some #{"bank-freeze-request"} (get fund "docs")))
    (is (= "critical" (get fund "severity")))))

(deftest test-takeover-gets-recovery-plan
  (let [to (first (filter #(= (get % "kind") "account-takeover") (get @RES "rows")))]
    (is (some #{"recovery-plan"} (get to "docs")))
    (is (some #{"platform-request"} (get to "docs")))))

(deftest test-report-renders-and-is-free
  (let [rep (analyze/report @RES)]
    (is (str/includes? rep "¥0"))
    (is (str/includes? rep "FREE — G1 holds"))))
