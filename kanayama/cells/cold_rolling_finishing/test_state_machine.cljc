(ns kanayama.cells.cold-rolling-finishing.test-state-machine
  "Tests for the kanayama cold_rolling_finishing state machine (L5b). Drives hot_band_loaded →
  cold_passes_complete → temper_complete → surface_inspection_complete → coil_qualified →
  record_emitted: phase/pct progression, the 4-pass cold schedule to 0.27 mm + H19 temper, and
  the emitted coilQualificationRecord."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.cold-rolling-finishing.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"cold_rolling_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-hot-band-loaded s0)
        s2 (sm/transition-to-cold-passes-complete s1)
        s3 (sm/transition-to-temper-complete s2)
        s4 (sm/transition-to-surface-inspection-complete s3)
        s5 (sm/transition-to-coil-qualified s4)
        s6 (sm/transition-to-record-emitted s5)]
    (is (= "hot_band_loaded" (get-in s1 ["cold_rolling_state" "phase"])))
    (is (= [10 45 65 80 92 100]
           (mapv #(get-in % ["cold_rolling_state" "completionPct"]) [s1 s2 s3 s4 s5 s6])))
    (is (= 4 (count (get-in s2 ["cold_rolling_state" "coldPasses"]))))
    (is (= 0.27 (get-in s2 ["cold_rolling_state" "finalGaugeMm"])))
    (is (= "H19" (get-in s3 ["cold_rolling_state" "temper"])))
    (is (= true (get-in s5 ["cold_rolling_state" "qualificationAccept"])))
    (is (= "end" (get s6 "next_node")))))

(deftest test-record-carries-fields
  (let [rec (get (-> {"cold_rolling_state" {"phase" "init" "lotId" "lot-11" "completionPct" 0}}
                     sm/transition-to-hot-band-loaded sm/transition-to-cold-passes-complete
                     sm/transition-to-temper-complete sm/transition-to-surface-inspection-complete
                     sm/transition-to-coil-qualified sm/transition-to-record-emitted)
                 "coil_qualification_record")]
    (is (= "com.etzhayyim.kanayama.coilQualificationRecord" (get rec "$type")))
    (is (= "lot-11" (get rec "lotId")))
    (is (= "can-body-stock-AA3104" (get rec "intendedProduct")))
    (is (= 12450.0 (get rec "coilMassKg")))
    (is (= 0.27 (get-in rec ["coldPasses" 3 "out_mm"])))))
