(ns wakai.methods.test-pool
  "Conformance tests for the wakai pool engine (contribution / distribution / aggregation)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [wakai.methods.pool :as P]))

(def NOW "2026-07-06T00:00:00Z")

(deftest test-contribution-is-never-investment
  ;; G6: investmentReturnPromised is const false regardless of what the caller intends —
  ;; there is no input key that can set it true (structural, not merely runtime-checked).
  (let [c (P/validate-contribution
           {:created-at NOW :contributor-pseudonym-did "did:web:pseudo-1.test"
            :contribution-amount-encrypted-cid "bafy...amt" :member-consent-cid "bafy...consent"
            :contribution-method "usdc-base-l2-direct" :ability-scaled-attested true})]
    (is (= (get c "investmentReturnPromised") false))
    (is (= (get c "abilityScaledAttested") true))))

(deftest test-contribution-requires-core-fields
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (P/validate-contribution {:created-at "" :contributor-pseudonym-did "did:web:p.test"
                                        :contribution-amount-encrypted-cid "c" :member-consent-cid "m"
                                        :contribution-method "usdc-base-l2-direct"})))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (P/validate-contribution {:created-at NOW :contributor-pseudonym-did "did:web:p.test"
                                        :contribution-amount-encrypted-cid "c" :member-consent-cid "m"
                                        :contribution-method "bank-wire"}))))  ; unknown method

(deftest test-distribution-requires-3-community-and-3-council-attestations
  ;; G9: fewer than 3 of either attestation chain must be REJECTED, not silently accepted.
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (P/validate-distribution
                {:created-at NOW :recipient-pseudonym-did "did:web:r.test" :need-attestation-cid "bafy...need"
                 :need-category "health-event"
                 :community-discernment-attestations ["did:web:c1.test" "did:web:c2.test"]  ; only 2
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test"]
                 :distribution-method "usdc-base-l2-direct"})))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (P/validate-distribution
                {:created-at NOW :recipient-pseudonym-did "did:web:r.test" :need-attestation-cid "bafy...need"
                 :need-category "health-event"
                 :community-discernment-attestations ["did:web:c1.test" "did:web:c2.test" "did:web:c3.test"]
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test"]  ; only 2
                 :distribution-method "usdc-base-l2-direct"}))))

(deftest test-distribution-is-never-adjudicated-or-exclusionary
  ;; G3 + G7: both fields are structural — there is no input key to flip them.
  (let [d (P/validate-distribution
           {:created-at NOW :recipient-pseudonym-did "did:web:r.test" :need-attestation-cid "bafy...need"
            :need-category "disability"
            :community-discernment-attestations ["did:web:c1.test" "did:web:c2.test" "did:web:c3.test"]
            :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test"]
            :distribution-method "usdc-base-l2-direct"})]
    (is (= (get d "claimAdjudicated") false))
    (is (= (get d "noPreExistingConditionExclusion") true))
    (is (= (count (get d "communityDiscernmentAttestations")) 3))))

(deftest test-distribution-rejects-unknown-need-category
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (P/validate-distribution
                {:created-at NOW :recipient-pseudonym-did "did:web:r.test" :need-attestation-cid "bafy...need"
                 :need-category "premium-lapse"  ; not a discernment category — insurance-speak
                 :community-discernment-attestations ["did:web:c1.test" "did:web:c2.test" "did:web:c3.test"]
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test"]
                 :distribution-method "usdc-base-l2-direct"}))))

(deftest test-aggregate-pool-state-no-individual-amounts-and-correct-totals
  (let [report (P/aggregate-pool-state
                {:report-period-start-utc "2026-07-01T00:00:00Z"
                 :report-period-end-utc "2026-07-31T23:59:59Z"
                 :contributions [{:amount-usd-millicents 10000000} {:amount-usd-millicents 5000000}]
                 :distributions [{:amount-usd-millicents 3000000 :need-category "health-event"}
                                 {:amount-usd-millicents 2000000 :need-category "health-event"}
                                 {:amount-usd-millicents 1000000 :need-category "unemployment"}]
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test"]})]
    (is (= (get report "totalContributionsUsdMillicents") 15000000))
    (is (= (get report "totalDistributionsUsdMillicents") 6000000))
    (is (= (get report "totalPoolBalanceUsdMillicents") 9000000))
    (is (= (get report "contributorCount") 2))
    (is (= (get report "poolAssetClass") "usdc-stable-only"))
    (is (= (get report "defiYieldFarmingActiveCount") 0))
    (is (= (get report "tokenSpeculationActiveCount") 0))
    ;; G-privacy: no per-member amount ever appears in the aggregate output.
    (is (not (some #{"amountUsdMillicents" "memberAmount"} (keys report))))
    (let [by-cat (into {} (map (juxt #(get % "needCategory") #(get % "totalAmountUsdMillicents"))
                               (get report "distributionsByNeedCategory")))]
      (is (= (get by-cat "health-event") 5000000))
      (is (= (get by-cat "unemployment") 1000000)))))

(deftest test-aggregate-pool-state-requires-3-council-attestations
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (P/aggregate-pool-state
                {:report-period-start-utc NOW :report-period-end-utc NOW
                 :contributions [] :distributions []
                 :council-attestations ["did:web:cl1.test"]}))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? #?(:clj Exception :cljs js/Error) (P/solve))))

#?(:clj (defn -main [& _] (run-tests 'wakai.methods.test-pool)))
