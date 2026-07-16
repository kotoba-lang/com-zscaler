(ns matsurigoto.methods.modules.test-benefit-disburse
  "Conformance tests for the benefit-disburse module."
  (:require [clojure.test :refer [deftest is run-tests]]
            [matsurigoto.methods.modules.benefit-disburse :as B]))

(deftest test-no-server-authority-certificate-unsigned
  (is (= B/SERVER-HELD-AUTHORITY false))
  (let [e (B/assess-entitlement "did:web:claimant.test" "housing" "commons-asset-access"
                                "Land Trust residency (ADR-2605192245)" "sovereign-governance")]
    (is (nil? (get-in e ["certificate" "proof"])))
    (is (= (get-in e ["certificate" "server_held_authority"]) false))
    (is (= (get-in e ["certificate" "status"]) "assessed-unsigned"))))

(deftest test-requires-claimant-and-evidence
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (B/assess-entitlement "" "old-age" "in-kind-service" "basis" "sovereign-governance")))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (B/assess-entitlement "did:web:c.test" "old-age" "in-kind-service" "" "sovereign-governance"))))

(deftest test-rejects-unknown-category
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (B/assess-entitlement "did:web:c.test" "not-a-cofog-group" "in-kind-service"
                                    "basis" "sovereign-governance"))))

(deftest test-sovereign-governance-cannot-express-cash
  ;; the structural cash≡0 proof (ADR-2605301020): principal A is confined to the two
  ;; non-cash media even if a caller tries to pass "cash-transfer".
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (B/assess-entitlement "did:web:c.test" "unemployment" "cash-transfer"
                                    "basis" "sovereign-governance"))))

(deftest test-supplied-to-state-may-use-cash
  ;; principal B (a nation-state adopter) runs its own ordinary G2P programme — cash is not
  ;; an etzhayyim constitutional matter for a state's own benefit system.
  (let [e (B/assess-entitlement "did:web:c.test" "unemployment" "cash-transfer"
                               "national unemployment insurance statute" "supplied-to-state")]
    (is (= (get e "medium") "cash-transfer"))))

(deftest test-unknown-principal-rejected
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (B/assess-entitlement "did:web:c.test" "old-age" "in-kind-service"
                                    "basis" "not-a-principal"))))

(deftest test-imputed-value-is-accounting-only
  (let [v (B/compute-imputed-value 30.0 66667)]  ; 30 days staple food @ ~$0.0667/day-unit
    (is (= (get v "accounting_only") true))
    (is (= (get v "total_value_usd_micros") 2000010))))

(deftest test-imputed-value-rejects-negative-inputs
  (is (thrown? #?(:clj Exception :cljs js/Error) (B/compute-imputed-value -1.0 100)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (B/compute-imputed-value 1.0 -100))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? #?(:clj Exception :cljs js/Error) (B/solve))))

#?(:clj (defn -main [& _] (run-tests 'matsurigoto.methods.modules.test-benefit-disburse)))
