(ns kanayama.cells.melting-furnace.test-state-machine
  "Tests for the kanayama melting_furnace state machine (L3). Drives charged → melt_held → degas →
  alloy_adjusted → pour_witnessed → record_emitted: phase/pct progression, witness quorum ≥2 (G4),
  and the emitted meltingAttestation."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.melting-furnace.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"melting_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-charged s0)
        s2 (sm/transition-to-melt-held s1)
        s3 (sm/transition-to-degas-complete s2)
        s4 (sm/transition-to-alloy-adjusted s3)
        s5 (sm/transition-to-pour-witnessed s4)
        s6 (sm/transition-to-record-emitted s5)]
    (is (= "charged" (get-in s1 ["melting_state" "phase"])))
    (is (= [15 40 60 80 92 100]
           (mapv #(get-in % ["melting_state" "completionPct"]) [s1 s2 s3 s4 s5 s6])))
    (is (= 720 (get-in s2 ["melting_state" "furnaceTempC"])))
    (is (= "record_emitted" (get-in s6 ["melting_state" "phase"])))
    (is (= "end" (get s6 "next_node")))))

(deftest test-pour-witness-quorum-at-least-two
  (let [s (-> {"melting_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
              sm/transition-to-charged sm/transition-to-melt-held sm/transition-to-degas-complete
              sm/transition-to-alloy-adjusted sm/transition-to-pour-witnessed)]
    (is (>= (count (get-in s ["melting_state" "robotSignatures"])) 2))    ; G4
    (is (= 462.0 (get-in s ["melting_state" "pourMassKg"])))))

(deftest test-record-emitted-carries-fields
  (let [rec (get (-> {"melting_state" {"phase" "init" "lotId" "lot-5" "completionPct" 0}}
                     sm/transition-to-charged sm/transition-to-melt-held sm/transition-to-degas-complete
                     sm/transition-to-alloy-adjusted sm/transition-to-pour-witnessed sm/transition-to-record-emitted)
                 "melting_attestation")]
    (is (= "com.etzhayyim.kanayama.meltingAttestation" (get rec "$type")))
    (is (= "lot-5" (get rec "lotId")))
    (is (= "AA3104 (can body)" (get-in rec ["alloyComposition" "designation"])))
    (is (= 2 (count (get rec "attestingRobots"))))))
