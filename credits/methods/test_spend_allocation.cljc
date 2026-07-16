(ns credits.methods.test-spend-allocation
  "credits -- unit tests for methods.spend-allocation (10% public-fund split, G2/G3)."
  (:require [clojure.test :refer [deftest is]]
            [credits.methods._t :refer [expect-raises]]
            [credits.methods.spend-allocation :as spend]))

(deftest test-10pct-allocation-on-100
  (let [{:keys [amount public-fund-amount destination-id]}
        (spend/compute-spend-allocation 100 nil)]
    (is (= 100 amount))
    (is (= 10 public-fund-amount))
    (is (= "public-fund:common" destination-id))))

(deftest test-allocation-scales-linearly
  (is (= 1 (:public-fund-amount (spend/compute-spend-allocation 10 nil))))
  (is (= 100 (:public-fund-amount (spend/compute-spend-allocation 1000 nil)))))

(deftest test-unset-preference-resolves-to-default
  (is (= "public-fund:common" (spend/resolve-destination nil))))

(deftest test-explicit-destination-honored
  (doseq [d spend/allocation-destinations]
    (is (= d (spend/resolve-destination d)))))

(deftest test-unknown-destination-rejected
  (expect-raises "unknown allocation destination_id" (spend/resolve-destination "public-fund:not-a-real-fund")))

(deftest test-rejects-non-positive-amount
  (expect-raises "positive" (spend/compute-spend-allocation 0 nil))
  (expect-raises "positive" (spend/compute-spend-allocation -1 nil)))
