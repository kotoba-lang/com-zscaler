(ns shomei.methods.test-factors
  "test_factors.cljc — taxonomy + assurance ladder. 1:1 Clojure port of `methods/test_factors.py`.
  ADR-2606072100. clojure.test; `expect_raises(contains=…)` → `shomei.methods._t/expect-raises`.
  The Python `__main__` demo (`run(\"factors\", CASES)`) is omitted (clojure.test drives the suite)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [shomei.methods._t :refer [expect-raises]]
            [shomei.methods.factors :as f]))

(deftest test-every-kind-has-class-and-proofs
  (doseq [k f/FACTOR_KINDS]
    (is (contains? f/FACTOR_CLASS k))
    (is (seq (get f/ALLOWED_PROOFS k)) (str k " has no allowed proofs"))))

(deftest test-class-partition
  (is (= "key" (get f/FACTOR_CLASS "wallet-evm") (get f/FACTOR_CLASS "wallet-btc")))
  (is (= "government" (get f/FACTOR_CLASS "gov-mynumber")))
  (is (= "covenant" (get f/FACTOR_CLASS "etz-at-oath")))
  (is (= "device" (get f/FACTOR_CLASS "webauthn")))
  (is (= "social" (get f/FACTOR_CLASS "sns-x"))))

(deftest test-gov-factors-set
  (is (= #{"gov-mynumber" "gov-passport" "gov-license"} f/GOV_FACTORS)))

(deftest test-public-handle-only-wallet-sns
  (doseq [k f/PUBLIC_HANDLE_FACTORS]
    (is (or (str/starts-with? k "wallet-") (str/starts-with? k "sns-"))))
  (is (not (contains? f/PUBLIC_HANDLE_FACTORS "gov-mynumber")))
  (is (not (contains? f/PUBLIC_HANDLE_FACTORS "etz-at-oath"))))

(deftest test-factor-class-unknown-raises
  (expect-raises "unknown factorKind" (f/factor-class "nope")))

(deftest test-ial-levels
  (is (= 0 (f/assurance-level #{} 0)))
  (is (= 1 (f/assurance-level #{"key"} 1)))                       ;; single factor
  (is (= 2 (f/assurance-level #{"key" "social"} 2)))             ;; two classes
  (is (= 3 (f/assurance-level #{"key" "covenant"} 2)))          ;; covenant-bound
  (is (= 4 (f/assurance-level #{"device" "government"} 2))))    ;; gov-verified

(deftest test-two-key-wallets-is-one-class-not-ial2
  ;; EVM + BTC both 'key' → count 2 but class-diversity 1 → still IAL1 (sybil-resistance: classes)
  (is (= 1 (f/assurance-level #{"key"} 2))))

(deftest test-covenant-alone-one-factor-is-ial1
  (is (= 1 (f/assurance-level #{"covenant"} 1))))

(deftest test-proof-of-personhood
  (is (= true (f/proof-of-personhood 2 2)))
  (is (= false (f/proof-of-personhood 1 1)))
  (is (= false (f/proof-of-personhood 2 1))))  ;; one class, even if level bumped, never PoP

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-factors)))
