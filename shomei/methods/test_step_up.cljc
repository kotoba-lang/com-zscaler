#!/usr/bin/env bb
;; shomei 証明 — tests for the IAL step-up-requirement (constructive inverse of assurance-level).
;; Run:  bb --classpath 20-actors 20-actors/shomei/methods/test_step_up.cljc
(ns shomei.methods.test-step-up
  "Tests for step-up-requirement — what a member must add to reach a target Identity Assurance Level.
  Pins the requirement against `assurance-level` (satisfying it reaches the target), and the G8
  invariant (identity-proof requirements only — no worth/behavior/rank field)."
  (:require [shomei.methods.factors :as f]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(deftest already-at-target-is-met
  (let [r (f/step-up-requirement #{"device" "social"} 2 2)]
    (is (:met? r))
    (is (= 0 (:additional-factors r)))
    (is (= 0 (:distinct-classes-needed r)))
    (is (= [] (:missing-classes r)))))

(deftest ial-1-to-2-needs-a-second-class-and-factor
  (let [r (f/step-up-requirement #{"device"} 1 2)]
    (is (= 1 (:current-ial r)))
    (is (= 1 (:additional-factors r)) "needs one more verified factor")
    (is (= 1 (:distinct-classes-needed r)) "and that factor must be in a new class")
    (is (= 2 (f/assurance-level #{"device" "social"} 2)) "satisfying it → IAL 2")))

(deftest ial-3-names-a-covenant-factor
  (let [r (f/step-up-requirement #{"device" "social"} 2 3)]
    (is (= ["covenant"] (:missing-classes r)))
    (is (= 3 (f/assurance-level #{"device" "social" "covenant"} 3)) "adding a covenant factor → IAL 3")))

(deftest ial-4-names-government-and-flags-council
  (let [r (f/step-up-requirement #{"device" "social"} 2 4)]
    (is (= ["government"] (:missing-classes r)))
    (is (str/includes? (:note r) "Council") "the gov leg is flagged Council-attested, not self-reachable")
    (is (= 4 (f/assurance-level #{"device" "government"} 2)) "a gov factor + another class → IAL 4")))

(deftest requirement-is-sufficient-across-targets
  ;; the model-pin: satisfying each target's stated requirement reaches that IAL via assurance-level
  (doseq [[start-classes start-count target sat-classes sat-count]
          [[#{} 0 1 #{"device"} 1]
           [#{} 0 2 #{"device" "social"} 2]
           [#{"device"} 1 3 #{"device" "covenant"} 2]
           [#{"device"} 1 4 #{"device" "government"} 2]]]
    (let [r (f/step-up-requirement start-classes start-count target)]
      (is (not (:met? r)) (str "not yet at IAL " target))
      (is (>= (f/assurance-level sat-classes sat-count) target)
          (str "satisfying the IAL-" target " requirement reaches it")))))

(deftest output-has-no-score-or-rank-field-g8
  (is (not-any? #{:score :rank :reputation :worth} (keys (f/step-up-requirement #{"device"} 1 4)))
      "identity-proof requirements only — no worth/behavior score (G8)"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'shomei.methods.test-step-up)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
