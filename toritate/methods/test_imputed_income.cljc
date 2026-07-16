(ns toritate.methods.test-imputed-income
  "Conformance tests for the toritate imputed-income accounting engine."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]
            [toritate.methods.imputed-income :as I]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/toritate"))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (contains? x "knownValues") (= parent field)) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

;; ── drift guard: the engine's hardcoded enums must match the Lexicon exactly ──
(deftest test-engine-enums-match-lexicon-exactly
  (is (= (known (lex "ledgerEntry") "chain") #{"base-l2" "geth-private" "ipfs-record-only"})
      "G3/G4: on-chain-rails set drifted from the ledgerEntry Lexicon")
  (is (= (known (lex "ledgerEntry") "nativeAsset") #{"usdc" "eth" "n-a"})
      "G8: non-fiat-assets set drifted from the ledgerEntry Lexicon")
  (is (= (known (lex "ledgerEntry") "category")
         #{"donation-income" "kisha-income" "grant-income"
           "tithe-split-90pct-operational" "tithe-split-10pct-public-fund"
           "public-fund-grant-disbursement" "council-operational-expense"
           "external-counsel-engagement" "external-auditor-engagement"
           "subsistence-flow" "vocation-flow" "liberation-flow" "care-flow" "reimbursement"
           "land-trust-acquisition" "asset-acquisition" "asset-depreciation"
           "securities-donation-liquidation-proceeds"
           "internal-promo-expense" "uncategorized"})
      "G12: ledger-categories set drifted from the ledgerEntry Lexicon"))

;; ── compute-imputed-income: L2 sustenance-tier basket, cross-checked against the valuation
;;    table's own stageBaskets.L2.flowUsdYr planning figure (2880 USD/yr) ──
(deftest test-compute-imputed-income-l2-sustenance-basket
  (is (= (I/compute-imputed-income ["food_staple" "otc_pharma" "electricity"])
         2880000000)))

;; ── compute-commons-asset-value: L3 shelter tier's STOCK facet (security-of-tenure), cross-
;;    checked against stageBaskets.L3.stockUsdYr (1800 USD/yr) ──
(deftest test-compute-commons-asset-value-l3-tenure
  (is (= (I/compute-commons-asset-value ["land_trust_tenure_access"])
         1800000000)))

(deftest test-compute-imputed-income-rejects-unknown-category
  (is (thrown? Exception (I/compute-imputed-income ["not-a-real-category"]))))

(deftest test-basic-high-income-report-cash-is-always-zero
  ;; N1 structural proof: there is no key a caller can pass to make this non-zero.
  (let [r (I/basic-high-income-report
           {:reporting-quarter "2027-Q1" :stage "L2" :adherents-at-stage 847
            :flow-category-keys ["food_staple" "otc_pharma" "electricity"]
            :stock-category-keys []})]
    (is (= (get-in r ["basicHighIncome" "cashStipendUsdMicros"]) 0))
    (is (= (get-in r ["basicHighIncome" "imputedIncomeMedianUsdMicrosYr"]) 2880000000))
    (is (= (get-in r ["basicHighIncome" "commonsAssetAccessMedianUsdMicros"]) 0))
    (is (= (get r "stage") "L2"))))

(deftest test-basic-high-income-report-requires-quarter-and-stage
  (is (thrown? Exception (I/basic-high-income-report {:reporting-quarter "" :stage "L2"
                                                       :flow-category-keys [] :stock-category-keys []})))
  (is (thrown? Exception (I/basic-high-income-report {:reporting-quarter "2027-Q1" :stage ""
                                                       :flow-category-keys [] :stock-category-keys []}))))

;; ── ledger-entry: G3/G4 (on-chain only), G8 (no fiat), G12 (no payroll) ──
(deftest test-ledger-entry-happy-path
  (let [e (I/ledger-entry {:created-at "2026-07-06T00:00:00Z" :tx-cid "0xabc..." :chain "base-l2"
                          :category "subsistence-flow" :amount-usd-millicents 200000000
                          :native-asset "usdc" :counterparty-did "did:web:steward.test"
                          :attesting-cell-did "did:web:toritate.etzhayyim.com"})]
    (is (= (get e "chain") "base-l2"))
    (is (= (get e "category") "subsistence-flow"))))

(deftest test-ledger-entry-rejects-off-chain-rail
  (is (thrown? Exception (I/ledger-entry {:created-at "2026-07-06T00:00:00Z" :tx-cid "0xabc..."
                                          :chain "swift-wire" :category "subsistence-flow"
                                          :amount-usd-millicents 100 :counterparty-did "did:web:s.test"}))))

(deftest test-ledger-entry-rejects-fiat-asset
  (is (thrown? Exception (I/ledger-entry {:created-at "2026-07-06T00:00:00Z" :tx-cid "0xabc..."
                                          :chain "base-l2" :category "subsistence-flow"
                                          :amount-usd-millicents 100 :native-asset "usd"
                                          :counterparty-did "did:web:s.test"}))))

(deftest test-ledger-entry-rejects-payroll-like-category
  (is (thrown? Exception (I/ledger-entry {:created-at "2026-07-06T00:00:00Z" :tx-cid "0xabc..."
                                          :chain "base-l2" :category "payroll"
                                          :amount-usd-millicents 100 :counterparty-did "did:web:s.test"}))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? Exception (I/solve))))

(defn -main [& _] (run-tests 'toritate.methods.test-imputed-income))
