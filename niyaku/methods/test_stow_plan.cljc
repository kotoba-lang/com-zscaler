(ns niyaku.methods.test-stow-plan
  "Tests for niyaku.methods.stow-plan."
  (:require [clojure.test :refer [deftest is testing]]
            [niyaku.methods.stow-plan :as sp]))

(defn- rot-index [rotation]
  (zipmap rotation (range)))

(deftest test-simple-plan-places-all
  (let [rotation ["SHA" "SIN" "ROT"]
        boxes [(sp/make-container "A" 22.0 "ROT")
               (sp/make-container "B" 18.0 "SIN")
               (sp/make-container "C" 14.0 "SHA")]
        plan (sp/build-stow-plan boxes rotation 1 1 3)
        box-port (zipmap (map :box-id boxes) (map :discharge-port boxes))]
    (is (= (set (keys (:assignments plan))) #{"A" "B" "C"}))
    (is (< (:tier (sp/slot-of plan "A")) (:tier (sp/slot-of plan "B"))))
    (is (< (:tier (sp/slot-of plan "B")) (:tier (sp/slot-of plan "C"))))
    (is (sp/validate-no-rehandle plan (rot-index rotation) box-port))))

(deftest test-weight-on-top-not-violated
  (let [rotation ["P1"]
        boxes [(sp/make-container "light" 5.0 "P1")
               (sp/make-container "heavy" 25.0 "P1")]
        plan (sp/build-stow-plan boxes rotation 1 1 2)]
    (is (< (:tier (sp/slot-of plan "heavy")) (:tier (sp/slot-of plan "light"))))))

(deftest test-capacity-exceeded-raises
  (let [rotation ["P1"]
        boxes (for [i (range 5)] (sp/make-container (str "b" i) 10.0 "P1"))]
    (is (thrown? clojure.lang.ExceptionInfo
                 (sp/build-stow-plan boxes rotation 1 1 4)))))

(deftest test-reefer-only-in-reefer-rows
  (let [rotation ["P1"]
        boxes [(sp/make-container "r" 10.0 "P1" true)]
        plan (sp/build-stow-plan boxes rotation 1 2 1 :reefer-rows [1])]
    (is (= 1 (:row (sp/slot-of plan "r"))))))

(deftest test-reefer-infeasible-when-no-reefer-row
  (let [rotation ["P1"]
        boxes [(sp/make-container "r" 10.0 "P1" true)]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (sp/build-stow-plan boxes rotation 1 1 1 :reefer-rows [])))))

(deftest test-hazmat-segregation-separates-classes
  (let [rotation ["P1"]
        boxes [(sp/make-container "flam" 10.0 "P1" false "3")
               (sp/make-container "oxid" 10.0 "P1" false "5.1")]
        ;; two classes cannot share a column; need ≥2 columns
        plan (sp/build-stow-plan boxes rotation 2 1 2)]
    (is (not= [(:bay (sp/slot-of plan "flam")) (:row (sp/slot-of plan "flam"))]
              [(:bay (sp/slot-of plan "oxid")) (:row (sp/slot-of plan "oxid"))]))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sp/build-stow-plan boxes rotation 1 1 2)))))

(deftest test-unknown-port-raises
  (is (thrown? clojure.lang.ExceptionInfo
               (sp/build-stow-plan [(sp/make-container "x" 1.0 "ZZZ")] ["P1"] 1 1 1))))

(deftest test-empty-rotation-raises
  (is (thrown? clojure.lang.ExceptionInfo
               (sp/build-stow-plan [] [] 1 1 1))))

(deftest test-discharge-sequence-top-first
  (let [rotation ["P1"]
        boxes (for [i (range 3)] (sp/make-container (str "b" i) (- 10.0 i) "P1"))
        plan (sp/build-stow-plan boxes rotation 1 1 3)
        seq (sp/discharge-sequence plan "P1")
        tiers (map #(:tier (sp/slot-of plan %)) seq)]
    (is (= tiers (sort > tiers)))))

(deftest test-weight-on-top-forces-new-column
  (let [rotation ["P0" "P1"]
        boxes [(sp/make-container "late_light" 10.0 "P1")
               (sp/make-container "early_heavy" 20.0 "P0")]
        plan (sp/build-stow-plan boxes rotation 2 1 2)]
    (is (not= (:bay (sp/slot-of plan "late_light"))
              (:bay (sp/slot-of plan "early_heavy"))))))

(deftest test-validate-no-rehandle-detects-violation
  (let [plan (-> (sp/make-stowage-plan)
                 (assoc-in [:assignments "below_early"] (sp/make-slot 0 0 0))
                 (assoc-in [:assignments "above_late"] (sp/make-slot 0 0 1)))
        rot-index {"P0" 0 "P1" 1}
        box-port {"below_early" "P0" "above_late" "P1"}]
    (is (false? (sp/validate-no-rehandle plan rot-index box-port)))))

(deftest test-slot-key
  (is (= [1 2 3] (sp/slot-key (sp/make-slot 1 2 3)))))
