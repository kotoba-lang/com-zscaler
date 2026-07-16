(ns hataori.cells.finishing-packing.test-state-machine
  "Tests for the hataori finishing_packing state machine (ADR-2606032100 + 2606032130 port;
  supersedes the Python cells/test_state_machines.py). Drives init → finished → folded →
  lot_attested and pins: N4 no-overproduction (quantity ≤ made-to-need ceiling), G9 no worker
  re-employed below BHI, G2 displacement-dividend attested + cohort registered, and the emitted
  finished-lot + fair-labor-provenance payload."
  (:require [clojure.test :refer [deftest is]]
            [hataori.cells.finishing-packing.state-machine :as sm]))

(defn- run-to-attested [fin-over att-over]
  (-> (merge {"cell_state" {} "quantity" 100 "made_to_need_ceiling" 100} fin-over)
      sm/transition-to-finished
      sm/transition-to-folded
      (merge att-over)
      sm/transition-to-lot-attested))

(deftest test-happy-path-emits-lot-and-provenance
  (let [s (run-to-attested {"offcut_waste_permille" 12}
                           {"displaced_cohort_id" "cohort-7" "dividend_attested" true})
        p (get-in s ["cell_state" "payload"])]
    (is (= "lot_attested" (get-in s ["cell_state" "phase"])))
    (is (= "end" (get s "next_node")))
    (is (= 100 (get-in p ["finished_lot" "quantity"])))
    (is (= 12 (get-in p ["finished_lot" "offcutWastePermille"])))
    (is (= "cohort-7" (get-in p ["fair_labor_provenance" "displacedCohortId"])))
    (is (= true (get-in p ["fair_labor_provenance" "noWorkerBelowBhi"])))
    (is (= true (get-in p ["fair_labor_provenance" "dividendAttested"])))))

(deftest test-phase-progression-and-ceiling-default
  (let [s1 (sm/transition-to-finished {"cell_state" {} "quantity" 50})   ; ceiling defaults to quantity
        s2 (sm/transition-to-folded s1)]
    (is (= "finished" (get-in s1 ["cell_state" "phase"])))
    (is (= 50 (get-in s1 ["cell_state" "made_to_need_ceiling"])))
    (is (= "folded" (get-in s2 ["cell_state" "phase"])))
    (is (= "lot_attested" (get s2 "next_node")))))

(deftest test-n4-blocks-overproduction
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"N4 violation"
                        (sm/transition-to-finished {"cell_state" {} "quantity" 200 "made_to_need_ceiling" 100}))))

(deftest test-g9-blocks-below-bhi-reemployment
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G9 violation"
                        (run-to-attested {} {"displaced_cohort_id" "c1" "dividend_attested" true
                                             "no_worker_below_bhi" false}))))

(deftest test-g2-blocks-unfunded-and-missing-cohort
  ;; dividend not attested → G2
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2 violation"
                        (run-to-attested {} {"displaced_cohort_id" "c1" "dividend_attested" false})))
  ;; cohort missing → G2
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2 violation"
                        (run-to-attested {} {"displaced_cohort_id" "" "dividend_attested" true}))))
