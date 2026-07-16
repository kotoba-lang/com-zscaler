(ns himawari.cells.cell-process.test-state-machine
  "Tests for the himawari cell_process gated state machine (ADR-2606021200 port).
  1:1 port of the cell_process cases from cells/test_cell_process.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [himawari.cells.cell-process.state-machine :as sm]))

(deftest test-cell-process-happy-path-complete
  (testing "Cell process completes successfully through all phases"
    (let [s0 (sm/transition-init {"batchId" "batch-001" "waferBatchId" "wf-001" "waferCount" 1000})
          s1 (sm/transition-texture s0)
          s2 (sm/transition-junction s1)
          s3 (sm/transition-metallization s2)
          s4 (sm/transition-flash-iv s3)
          s5 (sm/transition-gas-abatement s4)
          s6 (sm/transition-witness s5)
          s7 (sm/transition-emit-record s6)
          record (get s7 "cell_batch_record")]
      (is (= "batch-001" (get record "batchId")))
      (is (= "wf-001" (get record "waferBatchId")))
      (is (= 2 (count (get record "attestingRobots"))))
      (is (= "com.etzhayyim.himawari.cellBatchRecord" (get record "$type"))))))

(deftest test-cell-process-g3-gas-abatement-passes
  (testing "G3 gate passes when gas abatement is adequate"
    (let [s0 (sm/transition-init {"cellArchitecture" "TOPCon" "waferCount" 1000})
          s1 (sm/transition-texture s0)
          s2 (sm/transition-junction s1)
          s3 (sm/transition-metallization s2)
          s4 (sm/transition-flash-iv s3)
          s5 (sm/transition-gas-abatement s4)
          cs (get s5 "cell_state")]
      (is (= "witness" (get s5 "next_node")))
      (is (= "abatement_verified" (get cs "phase"))))))

(deftest test-cell-process-g6-silver-only-flags
  (testing "G6 gate flags silver-only metallization as off-roadmap"
    (let [s0 (sm/transition-init {"metallization" "silver"})
          s1 (sm/transition-texture s0)
          s2 (sm/transition-junction s1)
          s3 (sm/transition-metallization s2)
          flags (get (get s3 "cell_state") "metallizationFlags")]
      (is (> (count flags) 0))
      (is (str/includes? (first flags) "G6:silver-only-off-roadmap")))))

(deftest test-cell-process-ag-cu-hybrid-no-flags
  (testing "G6 gate does not flag ag-cu-hybrid as off-roadmap"
    (let [s0 (sm/transition-init {"metallization" "ag-cu-hybrid"})
          s1 (sm/transition-texture s0)
          s2 (sm/transition-junction s1)
          s3 (sm/transition-metallization s2)
          flags (get (get s3 "cell_state") "metallizationFlags")]
      (is (empty? flags)))))

(deftest test-cell-process-solve-end-to-end
  (testing "solve() completes the entire DAG end-to-end"
    (let [result (sm/solve {"batchId" "cell-batch-123" "waferBatchId" "wf-456" "waferCount" 500})
          record (get result "cell_batch_record")]
      (is (some? record))
      (is (= "cell-batch-123" (get record "batchId"))))))
