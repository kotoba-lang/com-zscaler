(ns matsurigoto.methods.modules.test-tax-assess
  "test_tax_assess.py — conformance tests for the tax-assess module.
  1:1 Clojure port (stdlib unittest-style → clojure.test).

  The income-tax assertions reproduce the published JP 速算表 exactly. The Python __main__
  runner is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [matsurigoto.methods.modules.tax-assess :as T]))

(deftest test-no-server-authority
  (is (= T/SERVER-HELD-AUTHORITY false))
  (let [r (T/assess-from-return 5000000 0 "JPN.income")]
    (is (nil? (get-in r ["receipt" "proof"])))
    (is (= (get-in r ["receipt" "server_held_authority"]) false))))

(deftest test-jp-quick-table-5m
  (let [r (T/assess-income-tax 5000000 (get-in @T/RATE-TABLES ["JPN.income" "brackets"]))]
    (is (= (get r "liability") 572500.0))))

(deftest test-jp-quick-table-3m
  (let [r (T/assess-income-tax 3000000 (get-in @T/RATE-TABLES ["JPN.income" "brackets"]))]
    (is (= (get r "liability") 202500.0))))

(deftest test-jp-quick-table-20m
  (let [r (T/assess-income-tax 20000000 (get-in @T/RATE-TABLES ["JPN.income" "brackets"]))]
    (is (= (get r "liability") 5204000.0))))

(deftest test-jp-top-bracket-50m
  (let [r (T/assess-income-tax 50000000 (get-in @T/RATE-TABLES ["JPN.income" "brackets"]))]
    (is (= (get r "liability") 17704000.0))))

(deftest test-zero-income-zero-tax
  (let [r (T/assess-income-tax 0 (get-in @T/RATE-TABLES ["JPN.income" "brackets"]))]
    (is (= (get r "liability") 0.0))
    (is (= (get r "effective_rate") 0.0))))

(deftest test-negative-income-raises
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (T/assess-income-tax -1 (get-in @T/RATE-TABLES ["JPN.income" "brackets"])))))

(deftest test-flat-rate-localization
  (let [r (T/assess-income-tax 1000000 (get-in @T/RATE-TABLES ["FLAT20.income" "brackets"]))]
    (is (= (get r "liability") 200000.0))
    (is (= (get r "effective_rate") 0.20))))

(deftest test-deductions-reduce-taxable
  (let [r (T/assess-from-return 6000000 1000000 "JPN.income")]
    (is (= (get r "taxable_income") 5000000))
    (is (= (get r "liability") 572500.0))
    (is (= (get r "currency") "JPY"))))

(deftest test-deductions-floor-at-zero
  (let [r (T/assess-from-return 500000 900000 "JPN.income")]
    (is (= (get r "taxable_income") 0.0))
    (is (= (get r "liability") 0.0))))

(deftest test-unknown-table-raises
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (T/assess-from-return 1 0 "NOPE.income"))))

(deftest test-vat-net-due-and-refund
  (let [due (T/assess-vat 300000 120000 "JPY")]
    (is (= (get due "net_vat_due") 180000.0))
    (is (= (get due "refund_due") 0.0)))
  (let [refund (T/assess-vat 100000 160000 "JPY")]
    (is (= (get refund "net_vat_due") 0.0))
    (is (= (get refund "refund_due") 60000.0))))

(deftest test-effective-rate-is-below-top-marginal
  (let [r (T/assess-income-tax 20000000 (get-in @T/RATE-TABLES ["JPN.income" "brackets"]))]
    (is (< (get r "effective_rate") 0.40))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? #?(:clj Exception :cljs js/Error) (T/solve))))

(deftest test-r1d-rate-tables-loaded-for-each-country
  (doseq [key ["JPN.income" "USA.income" "DEU.income" "GBR.income" "KOR.income" "IND.income"]]
    (is (contains? @T/RATE-TABLES key) key)
    (is (seq (get-in @T/RATE-TABLES [key "brackets"])) key)))

(deftest test-usa-lowest-bracket-10pct
  (let [r (T/assess-from-return 10000 0 "USA.income")]
    (is (= (get r "liability") 1000.0))   ; 10% of 10,000
    (is (= (get r "currency") "USD"))))

(deftest test-gbr-personal-allowance-zero-tax
  (let [r (T/assess-from-return 10000 0 "GBR.income")]
    (is (= (get r "liability") 0.0))      ; below the £12,570 allowance
    (is (= (get r "currency") "GBP"))))

(deftest test-ind-new-regime-below-threshold-zero
  (let [r (T/assess-from-return 250000 0 "IND.income")]
    (is (= (get r "liability") 0.0))))    ; below ₹300,000

(deftest test-kor-currency-and-progression
  (let [r (T/assess-from-return 20000000 0 "KOR.income")]
    (is (= (get r "currency") "KRW"))
    (is (< (get r "effective_rate") 0.15))))

#?(:clj (defn -main [& _] (run-tests 'matsurigoto.methods.modules.test-tax-assess)))
