(ns kanayama.cells.dc-casting.test-state-machine
  "Tests for the kanayama dc_casting state machine (L4). Drives mold_prepared → dc_casting_complete
  → homogenization_complete → inspection_passed → record_emitted: phase/pct progression, slab
  geometry/mass, and the emitted dcCastingAttestation."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.dc-casting.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"casting_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-mold-prepared s0)
        s2 (sm/transition-to-dc-casting-complete s1)
        s3 (sm/transition-to-homogenization-complete s2)
        s4 (sm/transition-to-inspection-passed s3)
        s5 (sm/transition-to-record-emitted s4)]
    (is (= "mold_prepared" (get-in s1 ["casting_state" "phase"])))
    (is (= [15 45 70 90 100]
           (mapv #(get-in % ["casting_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= {"width" 1000 "thickness" 600 "length" 8000} (get-in s1 ["casting_state" "slabDimensionsMm"])))
    (is (= 560 (get-in s3 ["casting_state" "homogenizationTempC"])))
    (is (= 12960.0 (get-in s4 ["casting_state" "slabMassKg"])))
    (is (= "end" (get s5 "next_node")))))

(deftest test-record-carries-fields
  (let [rec (get (-> {"casting_state" {"phase" "init" "lotId" "lot-7" "completionPct" 0}}
                     sm/transition-to-mold-prepared sm/transition-to-dc-casting-complete
                     sm/transition-to-homogenization-complete sm/transition-to-inspection-passed
                     sm/transition-to-record-emitted)
                 "dc_casting_attestation")]
    (is (= "com.etzhayyim.kanayama.dcCastingAttestation" (get rec "$type")))
    (is (= "lot-7" (get rec "lotId")))
    (is (= 690 (get rec "castingTempC")))
    (is (= 18 (get rec "homogenizationHours")))
    (is (= [] (get rec "inspectionFindings")))))
