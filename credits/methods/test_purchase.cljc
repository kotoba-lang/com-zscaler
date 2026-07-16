(ns credits.methods.test-purchase
  "credits -- unit tests for methods.purchase (PurchaseCredits 30% fee, G1)."
  (:require [clojure.test :refer [deftest is]]
            [credits.methods._t :refer [expect-raises]]
            [credits.methods.purchase :as purchase]))

(deftest test-30pct-fee-on-100
  (let [{:keys [gross fee net]} (purchase/preview-purchase 100)]
    (is (= 100 gross))
    (is (= 30 fee))
    (is (= 70 net))))

(deftest test-fee-scales-linearly
  (is (= 3 (:fee (purchase/preview-purchase 10))))
  (is (= 300 (:fee (purchase/preview-purchase 1000)))))

(deftest test-gross-equals-fee-plus-net
  (doseq [g [1 7 33 250 9999]]
    (let [{:keys [gross fee net]} (purchase/preview-purchase g)]
      (is (= gross (+ fee net))))))

(deftest test-rejects-non-positive-gross
  (expect-raises "positive" (purchase/preview-purchase 0))
  (expect-raises "positive" (purchase/preview-purchase -5)))

(deftest test-rejects-non-numeric-gross
  (expect-raises (purchase/preview-purchase "100")))
