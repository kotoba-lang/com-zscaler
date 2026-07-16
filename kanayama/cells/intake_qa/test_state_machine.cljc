(ns kanayama.cells.intake-qa.test-state-machine
  "Tests for the kanayama intake_qa state machine (cells/intake_qa/state_machine.cljc). Drives the
  full L1 sequence init → bale_weighed → contamination_scanned → accept_or_reject_decided →
  record_emitted: phase/completionPct progression, the contamination accept/reject thresholds, and
  the emitted intakeRecord."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.intake-qa.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"intake_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-bale-weighed s0)
        s2 (sm/transition-to-contamination-scanned s1)
        s3 (sm/transition-to-accept-or-reject-decided s2)
        s4 (sm/transition-to-record-emitted s3)]
    (is (= "bale_weighed" (get-in s1 ["intake_state" "phase"])))
    (is (= 25 (get-in s1 ["intake_state" "completionPct"])))
    (is (= "contamination_scanned" (get-in s2 ["intake_state" "phase"])))
    (is (= 65 (get-in s2 ["intake_state" "completionPct"])))
    (is (= "accept_or_reject_decided" (get-in s3 ["intake_state" "phase"])))
    (is (= 90 (get-in s3 ["intake_state" "completionPct"])))
    (is (= "record_emitted" (get-in s4 ["intake_state" "phase"])))
    (is (= 100 (get-in s4 ["intake_state" "completionPct"])))
    (is (= "end" (get s4 "next_node")))))

(deftest test-clean-bale-accepted
  ;; default scan values (Cl 12<50, moisture 1.8<5, mag 0.3<1.0, nonAl 0.7<2.0) → accept true
  (let [s (-> {"intake_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
              sm/transition-to-bale-weighed sm/transition-to-contamination-scanned
              sm/transition-to-accept-or-reject-decided)]
    (is (= true (get-in s ["intake_state" "accept"])))))

(deftest test-contaminated-bale-rejected
  ;; force an over-threshold chloride reading → accept false
  (let [scanned {"intake_state" {"phase" "contamination_scanned" "lotId" "lot-2" "completionPct" 65
                                 "chlorideResidualPpm" 80.0 "moisturePct" 1.0
                                 "magneticImpurityPct" 0.2 "nonAlNonMagneticImpurityPct" 0.5}}
        s (sm/transition-to-accept-or-reject-decided scanned)]
    (is (= false (get-in s ["intake_state" "accept"])))))

(deftest test-record-emitted-carries-fields
  (let [rec (get (-> {"intake_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
                     sm/transition-to-bale-weighed sm/transition-to-contamination-scanned
                     sm/transition-to-accept-or-reject-decided sm/transition-to-record-emitted)
                 "intake_record")]
    (is (= "com.etzhayyim.kanayama.intakeRecord" (get rec "$type")))
    (is (= "lot-1" (get rec "lotId")))
    (is (= 480.5 (get rec "baleWeightKg")))
    (is (= true (get rec "accept")))))
