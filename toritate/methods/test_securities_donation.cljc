(ns toritate.methods.test-securities-donation
  "Conformance tests for the toritate securities-donation intake engine."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]
            [toritate.methods.securities-donation :as S]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/give/stock"))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (contains? x "knownValues") (= parent field)) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- const-of [doc field]
  (get-in doc ["defs" "main" "record" "properties" field "const"]))

;; ── drift guard: hardcoded scheme set + structural consts must match the Lexicon ──
(deftest test-engine-matches-lexicon-exactly
  (is (= (known (lex "donation") "securityIdentifierScheme") #{"ticker" "cusip" "isin"}))
  (is (= (const-of (lex "donation") "heldAsEquityPosition") false)
      "the Lexicon's own const must agree with this engine's structural false"))

(def ^:private valid-donation
  {:donor-did "did:web:donor.test" :security-identifier "AAPL"
   :security-identifier-scheme "ticker" :share-quantity 100
   :fair-market-value-usd-micros 19500000000 :valuation-date-utc "2026-07-06T00:00:00Z"
   :brokerage-transfer-confirmation-cid "bafy...confirmation" :created-at "2026-07-06T00:00:00Z"})

(deftest test-validate-donation-never-holds-equity
  (let [d (S/validate-securities-donation valid-donation)]
    (is (= (get d "heldAsEquityPosition") false))
    (is (= (get d "securityIdentifier") "AAPL"))
    (is (= (get d "shareQuantity") 100))))

(deftest test-validate-donation-rejects-unknown-identifier-scheme
  (is (thrown? Exception
               (S/validate-securities-donation (assoc valid-donation :security-identifier-scheme "sedol")))))

(deftest test-validate-donation-rejects-non-positive-quantities
  (is (thrown? Exception (S/validate-securities-donation (assoc valid-donation :share-quantity 0))))
  (is (thrown? Exception (S/validate-securities-donation (assoc valid-donation :fair-market-value-usd-micros 0)))))

(deftest test-validate-donation-requires-brokerage-confirmation
  (is (thrown? Exception (S/validate-securities-donation (assoc valid-donation :brokerage-transfer-confirmation-cid "")))))

;; ── record-liquidation ──
(deftest test-record-liquidation-happy-path
  (let [d (S/validate-securities-donation valid-donation)
        liquidated (S/record-liquidation d
                    {:liquidated-at-utc "2026-07-07T00:00:00Z"
                     :liquidation-proceeds-usd-micros 19450000000
                     :liquidation-donation-ref "at://did:web:etzhayyim.com/com.etzhayyim.give.usdc.donation/abc123"})]
    (is (= (get liquidated "liquidationProceedsUsdMicros") 19450000000))
    (is (= (get liquidated "heldAsEquityPosition") false))
    ;; the original donation map is untouched (pure fn, no mutation)
    (is (nil? (get d "liquidatedAtUtc")))))

(deftest test-record-liquidation-requires-donation-ref
  (let [d (S/validate-securities-donation valid-donation)]
    (is (thrown? Exception
                 (S/record-liquidation d {:liquidated-at-utc "2026-07-07T00:00:00Z"
                                          :liquidation-proceeds-usd-micros 19450000000
                                          :liquidation-donation-ref ""})))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? Exception (S/solve))))

(defn -main [& _] (run-tests 'toritate.methods.test-securities-donation))
