(ns credits.methods.test-ledger-rails
  "credits -- unit tests for methods.ledger-rails (non-fiat asset + banned-vendor
  predicate -- G8/G9)."
  (:require [clojure.test :refer [deftest is]]
            [credits.methods.ledger-rails :as rails]))

(deftest test-native-asset-is-not-fiat
  (is (not (contains? rails/fiat-currency-codes rails/native-asset))))

(deftest test-internal-ledger-rail-is-valid
  (is (rails/valid-payment-rail? "internal-ledger")))

(deftest test-banned-vendors-are-not-valid-rails
  (doseq [v rails/banned-payment-vendors]
    (is (not (rails/valid-payment-rail? v)))
    (is (not (contains? rails/allowed-payment-rails v)))))

(deftest test-fiat-codes-are-not-valid-rails
  (doseq [f rails/fiat-currency-codes]
    (is (not (rails/valid-payment-rail? f)))))
