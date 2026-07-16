(ns silicon.cells.test-state-machine
  "silicon fab cell state machines — R0 transition coverage (ADR-2605242500). Threads each of the 4
  deterministic mock-data phase machines (chiptest / mask_lithography / packaging / wafer_processing)
  end-to-end and pins the completionPct ladder, the terminal record, and representative mock payloads.
  (No Python cell test existed; this authors the structural coverage.) .solve() raises at R0."
  (:require [clojure.test :refer [deftest is]]
            [silicon.cells.chiptest.state-machine :as ct]
            [silicon.cells.mask-lithography.state-machine :as ml]
            [silicon.cells.packaging.state-machine :as pk]
            [silicon.cells.wafer-processing.state-machine :as wp]))

(deftest test-chiptest-threads-to-graded
  (let [s (ct/transition-to-contact-probe-engaged {"chiptest_state" {}})]
    (is (= 20 (get-in s ["chiptest_state" "completionPct"])))
    (is (= "parametric_test" (get s "next_node")))
    (let [s (ct/transition-to-parametric-test-complete s)
          s (ct/transition-to-functional-test-complete s)
          s (ct/transition-to-chip-graded s)
          rec (get s "chiptest_record")]
      (is (= 100 (get-in s ["chiptest_state" "completionPct"])))
      (is (= "chip_graded" (get-in s ["chiptest_state" "phase"])))
      (is (= "end" (get s "next_node")))
      (is (= "A" (get rec "yieldGrade")))
      (is (= 2 (count (get rec "attestingRobots")))))))

(deftest test-mask-lithography-threads-to-verified
  (let [s (-> {"mask_state" {}}
              ml/transition-to-mask-design-loaded
              ml/transition-to-photoresist-applied
              ml/transition-to-exposure-complete
              ml/transition-to-development-complete
              ml/transition-to-mask-verified)
        rec (get s "mask_lithography_record")]
    (is (= 100 (get-in s ["mask_state" "completionPct"])))
    (is (= "mask_verified" (get-in s ["mask_state" "phase"])))
    (is (= "QmMaskDesign7nm20260526" (get rec "designCid")))
    (is (= true (get-in rec ["metrology" "mask_qualification_pass"])))))

(deftest test-packaging-threads-to-tested
  (let [s (-> {"packaging_state" {}}
              pk/transition-to-die-attached
              pk/transition-to-wire-bonding-complete
              pk/transition-to-encapsulation-complete
              pk/transition-to-package-tested)
        rec (get s "packaging_record")]
    (is (= 100 (get-in s ["packaging_state" "completionPct"])))
    (is (= "package_tested" (get-in s ["packaging_state" "phase"])))
    (is (= "A" (get-in rec ["finalTest" "package_quality_grade"])))
    (is (= "gold" (get-in rec ["wireBond" "wire_material"])))))

(deftest test-wafer-processing-threads-to-verified
  (let [s (-> {"wafer_state" {}}
              wp/transition-to-deposition-complete
              wp/transition-to-etching-complete
              wp/transition-to-implantation-complete
              wp/transition-to-cmp-complete
              wp/transition-to-wafer-verified)
        rec (get s "wafer_processing_record")]
    (is (= 100 (get-in s ["wafer_state" "completionPct"])))
    (is (= "wafer_verified" (get-in s ["wafer_state" "phase"])))
    (is (= "SiO2" (get-in rec ["deposition" "material"])))
    (is (= true (get-in rec ["metrology" "wafer_release_approved"])))))

(deftest test-completion-ladders-monotone
  ;; each cell's pcts strictly increase and reach 100
  (is (= [20 50 75 100]
         (map #(get-in (% {"chiptest_state" {}}) ["chiptest_state" "completionPct"])
              [ct/transition-to-contact-probe-engaged ct/transition-to-parametric-test-complete
               ct/transition-to-functional-test-complete ct/transition-to-chip-graded])))
  (is (= [20 40 60 80 100]
         (map #(get-in (% {"wafer_state" {}}) ["wafer_state" "completionPct"])
              [wp/transition-to-deposition-complete wp/transition-to-etching-complete
               wp/transition-to-implantation-complete wp/transition-to-cmp-complete wp/transition-to-wafer-verified]))))

(deftest test-all-cells-solve-raises
  (doseq [solve [ct/solve ml/solve pk/solve wp/solve]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0 scaffold" (solve {})))))
