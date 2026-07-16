(ns kanayama.cells.decoating-separation.test-state-machine
  "Tests for the kanayama decoating_separation state machine (L2). Drives the full sequence
  decoater_heated → lacquer_burnoff → shred → magnetic → eddy_current → record_emitted: phase/
  completionPct progression and the emitted decoatingAttestation."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.decoating-separation.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"decoating_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-decoater-heated s0)
        s2 (sm/transition-to-lacquer-burnoff-complete s1)
        s3 (sm/transition-to-shred-complete s2)
        s4 (sm/transition-to-magnetic-separation-complete s3)
        s5 (sm/transition-to-eddy-current-separation-complete s4)
        s6 (sm/transition-to-record-emitted s5)]
    (is (= "decoater_heated" (get-in s1 ["decoating_state" "phase"])))
    (is (= [15 40 60 75 90 100]
           (mapv #(get-in % ["decoating_state" "completionPct"]) [s1 s2 s3 s4 s5 s6])))
    (is (= 500 (get-in s1 ["decoating_state" "decoaterTempC"])))
    (is (= 470.0 (get-in s5 ["decoating_state" "cleanAlMassKg"])))
    (is (= "record_emitted" (get-in s6 ["decoating_state" "phase"])))
    (is (= "end" (get s6 "next_node")))))

(deftest test-record-emitted-carries-fields
  (let [rec (get (-> {"decoating_state" {"phase" "init" "lotId" "lot-9" "completionPct" 0}}
                     sm/transition-to-decoater-heated sm/transition-to-lacquer-burnoff-complete
                     sm/transition-to-shred-complete sm/transition-to-magnetic-separation-complete
                     sm/transition-to-eddy-current-separation-complete sm/transition-to-record-emitted)
                 "decoating_attestation")]
    (is (= "com.etzhayyim.kanayama.decoatingAttestation" (get rec "$type")))
    (is (= "lot-9" (get rec "lotId")))
    (is (= 470.0 (get rec "cleanAlMassKg")))
    (is (= 1.4 (get-in rec ["magneticFraction" "removedFeKg"])))))
