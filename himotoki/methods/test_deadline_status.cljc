#!/usr/bin/env bb
;; himotoki 繙き — tests for the deadline_tracker status read.
;; Run:  bb --classpath 20-actors 20-actors/himotoki/methods/test_deadline_status.cljc
(ns himotoki.methods.test-deadline-status
  "Tests for deadline-status — the operational status of an outstanding request's response clock
  (open / due-soon / overdue / appeal-eligible), pure arithmetic over the request's own
  registry-sourced deadline (himotoki tracks, chigiri owns the law; nothing statutory hardcoded).
  Deterministic (elapsed-days supplied); operational, not legal advice (G5/UPL)."
  (:require [himotoki.methods.request :as r]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private req {"statutoryDeadlineDays" 30 "extensionDays" 60})  ; e.g. a GDPR-shaped request

(deftest open-while-comfortably-within-the-window
  (let [s (r/deadline-status req 10)]
    (is (= :open (:status s)) "10 of 30 days elapsed → open")
    (is (= 20 (:remaining s)))))

(deftest due-soon-inside-the-notice-window
  (is (= :due-soon (:status (r/deadline-status req 25))) "5 days left (≤7 notice) → due-soon")
  (is (= :open (:status (r/deadline-status req 25 3))) "with a 3-day notice window, 5 left is still open"))

(deftest overdue-past-the-deadline-but-within-extension
  (let [s (r/deadline-status req 40)]
    (is (= :overdue (:status s)) "past the 30-day deadline, still inside the +60 extension → overdue")
    (is (= -10 (:remaining s)) "remaining goes negative once past the deadline")))

(deftest appeal-eligible-past-deadline-plus-extension
  (is (= :appeal-eligible (:status (r/deadline-status req 95)))
      "past 30 + 60 = 90 days → the member may escalate via appeal_route"))

(deftest no-fixed-deadline-when-the-regime-sets-none
  ;; APPI 遅滞なく / PIPL timely → statutoryDeadlineDays nil
  (let [s (r/deadline-status {"statutoryDeadlineDays" nil} 100)]
    (is (= :no-fixed-deadline (:status s)) "no fixed statutory day-count → cannot day-track")
    (is (= 100 (:elapsed s)))))

(deftest no-extension-defaults-to-zero
  ;; a request without extensionDays goes straight overdue→appeal at the statutory deadline
  (is (= :appeal-eligible (:status (r/deadline-status {"statutoryDeadlineDays" 20} 21)))
      "no documented extension → past the deadline is immediately appeal-eligible"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'himotoki.methods.test-deadline-status)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
