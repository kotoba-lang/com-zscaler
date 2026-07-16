(ns kanayama.cells.mass-balance-binder.test-state-machine
  "Tests for the kanayama mass_balance_binder terminal state machine (G2 + G14). Drives
  records_collected → mass_balance_computed → kotoba_datomic_anchored → record_emitted: phase/pct
  progression, the mass-balance arithmetic (input = output_metal + net_dross + emission, closure %
  vs the 98% G12 KPI, parity-pinned to Python round(_, 2) = 98.79), and the emitted
  massBalanceBinderRecord with its kotoba-datomic anchor."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.mass-balance-binder.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"balance_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-records-collected s0)
        s2 (sm/transition-to-mass-balance-computed s1)
        s3 (sm/transition-to-kotoba-datomic-anchored s2)
        s4 (sm/transition-to-record-emitted s3)]
    (is (= "records_collected" (get-in s1 ["balance_state" "phase"])))
    (is (= [25 60 90 100]
           (mapv #(get-in % ["balance_state" "completionPct"]) [s1 s2 s3 s4])))
    (is (= 8 (count (get-in s1 ["balance_state" "upstreamRecords"]))))
    (is (= "kotoba-datomic_anchored" (get-in s3 ["balance_state" "phase"])))
    (is (= "end" (get s4 "next_node")))))

(deftest test-mass-balance-arithmetic
  (let [bs (-> {"balance_state" {"phase" "init" "lotId" "lot-3" "completionPct" 0}}
               sm/transition-to-records-collected sm/transition-to-mass-balance-computed
               (get "balance_state"))]
    (is (= 480.5 (get bs "inputMassKg")))
    (is (= 465.6 (get bs "outputMetalKg")))   ; 462.0 main pour + 3.6 recovered secondary
    (is (= 4.4 (get bs "drossMassKg")))        ; 8.0 gross - 3.6 recovered = net waste
    (is (= 4.7 (get bs "emissionMassKg")))
    (is (= 98.79 (get bs "closurePct")))       ; parity with Python round(_, 2)
    (is (= true (get bs "accept")))))          ; 98.79 >= 98.0 G12 KPI

(deftest test-record-and-anchor
  (let [rec (get (-> {"balance_state" {"phase" "init" "lotId" "lot-7" "completionPct" 0}}
                     sm/transition-to-records-collected sm/transition-to-mass-balance-computed
                     sm/transition-to-kotoba-datomic-anchored sm/transition-to-record-emitted)
                 "mass_balance_binder_record")]
    (is (= "etzhayyim:kanayama:massBalanceBinderRecord" (get rec "$type")))
    (is (= "lot-7" (get rec "lotId")))
    (is (= 98.0 (get rec "g2Limit")))
    (is (= true (get-in rec ["kotoba-datomicAnchor" "g2Compliant"])))
    (is (= "com.etzhayyim.kanayama" (get-in rec ["kotoba-datomicAnchor" "membraneNamespace"])))))
