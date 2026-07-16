(ns kanayama.cells.hot-rolling.test-state-machine
  "Tests for the kanayama hot_rolling state machine (L5a). Drives slab_reheated → rough_roll_complete
  → finish_roll_complete → coiled → record_emitted: phase/pct progression, the 8-pass schedule
  (4 rough + 4 finish accumulated), final 3 mm gauge, and the emitted rollingAttestation."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.hot-rolling.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"hot_rolling_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-slab-reheated s0)
        s2 (sm/transition-to-rough-roll-complete s1)
        s3 (sm/transition-to-finish-roll-complete s2)
        s4 (sm/transition-to-coiled s3)
        s5 (sm/transition-to-record-emitted s4)]
    (is (= "slab_reheated" (get-in s1 ["hot_rolling_state" "phase"])))
    (is (= [15 50 75 90 100]
           (mapv #(get-in % ["hot_rolling_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= 4 (count (get-in s2 ["hot_rolling_state" "passes"]))))
    (is (= 8 (count (get-in s3 ["hot_rolling_state" "passes"]))))   ; 4 rough + 4 finish accumulate
    (is (= 3.0 (get-in s3 ["hot_rolling_state" "finalGaugeMm"])))
    (is (= "end" (get s5 "next_node")))))

(deftest test-pass-schedule-and-record
  (let [s3 (-> {"hot_rolling_state" {"phase" "init" "lotId" "lot-9" "completionPct" 0}}
               sm/transition-to-slab-reheated sm/transition-to-rough-roll-complete
               sm/transition-to-finish-roll-complete)
        passes (get-in s3 ["hot_rolling_state" "passes"])
        rec (get (-> s3 sm/transition-to-coiled sm/transition-to-record-emitted) "rolling_attestation")]
    (is (= 1 (get-in passes [0 "pass"])))
    (is (= 3 (get-in passes [7 "out_mm"])))      ; final pass exits at 3 mm
    (is (= "com.etzhayyim.kanayama.rollingAttestation" (get rec "$type")))
    (is (= "lot-9" (get rec "lotId")))
    (is (= "hot" (get rec "rollingStage")))
    (is (= 12700.0 (get rec "hotBandMassKg")))))
