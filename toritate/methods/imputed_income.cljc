(ns toritate.methods.imputed-income
  "toritate 執帳 — Basic High Income accounting engine: imputed-income (FLOW) + commons-asset
  (STOCK) computation + `ledgerEntry` construction (R0 reference implementation,
  ADR-2605262900 + ADR-2605301020).

  Reads `valuation/v1-retail-equiv.json` (the open, method-versioned, Council-attestable
  reference-price table — ADR-2605301020 §4) rather than duplicating its figures in code, so
  the figures stay single-sourced and Council-attestable independent of this engine.

    G3/G4 100% on-chain : `ledger-entry`'s `chain` is restricted to the on-chain rails
                         {base-l2, geth-private, ipfs-record-only} — no off-chain rail is
                         representable, matching the Lexicon's `knownValues` exactly.
    G8 no fiat          : `nativeAsset` is restricted to {usdc, eth, n-a} — no fiat token.
    G12 no payroll      : `category` excludes payroll/wage/salary/bonus/commission — only the
                         volunteer-economy flow categories the Lexicon actually declares.
    ADR-2605301020 cash≡0 : `cashStipendUsdMicros` is NEVER a caller input in
                         `basic-high-income-report` — always 0, structural (N1 proof).

  All quantities are integer-with-implied-units per ADR-2605190900 (no float in Lexicons):
  USD as micros (×1e6), ratios as per-mille (×1000).

  House style: result maps stay string-keyed (matching the Lexicon/AT-record camelCase shape);
  pure fns; the valuation table load is the only I/O, isolated behind #?(:clj ...)."
  (:require [clojure.string :as str]
            #?(:clj [cheshire.core :as json])
            #?(:clj [clojure.java.io :as io])))

;; Mirrors 00-contracts/lexicons/com/etzhayyim/toritate/ledgerEntry.json knownValues exactly
;; (see test_imputed_income.cljc's cross-check test that guards against drift).
(def ^:private on-chain-rails #{"base-l2" "geth-private" "ipfs-record-only"})          ; G3/G4
(def ^:private non-fiat-assets #{"usdc" "eth" "n-a"})                                   ; G8
(def ^:private ledger-categories                                                        ; G12
  #{"donation-income" "kisha-income" "grant-income"
    "tithe-split-90pct-operational" "tithe-split-10pct-public-fund"
    "public-fund-grant-disbursement" "council-operational-expense"
    "external-counsel-engagement" "external-auditor-engagement"
    "subsistence-flow" "vocation-flow" "liberation-flow" "care-flow" "reimbursement"
    "land-trust-acquisition" "asset-acquisition" "asset-depreciation"
    "securities-donation-liquidation-proceeds"
    "internal-promo-expense" "uncategorized"})

#?(:clj
   (defn- default-valuation-file []
     ;; methods/imputed_income.cljc -> toritate/valuation/v1-retail-equiv.json
     (-> *file* io/file .getParentFile .getParentFile
         (io/file "valuation" "v1-retail-equiv.json"))))

(defn load-valuation-table
  "Load the valuation table (string-keyed, matching the JSON shape 1:1). Robust: a missing
  file / parse error returns nil rather than throwing at namespace load time."
  ([] #?(:clj (load-valuation-table (default-valuation-file)) :cljs nil))
  ([file]
   #?(:clj (try (json/parse-string (slurp (io/file file)))
                (catch Exception _ nil))
      :cljs nil)))

(def VALUATION-TABLE (atom (load-valuation-table)))

(defn- category-value [table section category-key value-key]
  (let [entry (get-in table [section category-key])]
    (when-not entry
      (throw (ex-info (str "imputed_income: unknown " section " category " (pr-str category-key)) {})))
    (get entry value-key)))

(defn compute-imputed-income
  "Sum retail-equivalent annual value (FLOW) across the given FLOW category keys consumed
  this period, in USD micros/yr. ACCOUNTING-ONLY — never a transfer amount (ADR-2605301020 §1)."
  ([category-keys] (compute-imputed-income category-keys @VALUATION-TABLE))
  ([category-keys table]
   (when-not table
     (throw (ex-info "imputed_income: valuation table not loaded" {})))
   (* 1000000 (reduce + 0 (map #(category-value table "flow" % "retailEquivUsdYr") category-keys)))))

(defn compute-commons-asset-value
  "Sum annualized access value (STOCK) across the given STOCK category keys, in USD micros.
  ACCOUNTING-ONLY, never a title/deed — access-only (ADR-2605301020 §2)."
  ([category-keys] (compute-commons-asset-value category-keys @VALUATION-TABLE))
  ([category-keys table]
   (when-not table
     (throw (ex-info "imputed_income: valuation table not loaded" {})))
   (* 1000000 (reduce + 0 (map #(category-value table "stock" % "annualizedAccessValueUsdYr") category-keys)))))

(defn basic-high-income-report
  "Construct the ADR-2605301020 §5 Liberation Metric `basicHighIncome` extension block.
  `cashStipendUsdMicros` is NEVER a caller input — always 0 (the on-chain proof N1 holds)."
  [{:keys [reporting-quarter stage adherents-at-stage flow-category-keys stock-category-keys
           valuation-method]}]
  (when-not (and reporting-quarter (not= reporting-quarter ""))
    (throw (ex-info "report: reporting_quarter is required" {})))
  (when-not (and stage (not= stage ""))
    (throw (ex-info "report: stage is required" {})))
  (let [table @VALUATION-TABLE
        flow-usd-yr (reduce + 0 (map #(category-value table "flow" % "retailEquivUsdYr") flow-category-keys))
        stock-usd-yr (reduce + 0 (map #(category-value table "stock" % "annualizedAccessValueUsdYr") stock-category-keys))
        benchmark-usd-yr (get-in table ["benchmark" "perAdherentUsdYr"])
        ratio-permille (if (and benchmark-usd-yr (pos? benchmark-usd-yr))
                          (quot (* 1000 (+ flow-usd-yr stock-usd-yr)) benchmark-usd-yr)
                          0)]
    {"reportingQuarter" reporting-quarter
     "stage" stage
     "adherentsAtStage" (or adherents-at-stage 0)
     "basicHighIncome"
     {"imputedIncomeMedianUsdMicrosYr" (* 1000000 flow-usd-yr)
      "imputedIncomeValuationMethod" (or valuation-method (get table "methodId" "v1-retail-equiv"))
      "commonsAssetAccessMedianUsdMicros" (* 1000000 stock-usd-yr)
      "highIncomeBenchmarkRatioPermille" ratio-permille
      "cashStipendUsdMicros" 0}}))                     ; N1 — structural, never a caller input

(defn ledger-entry
  "Validate + construct a `ledgerEntry` record (G3/G4/G8/G12). Pure function."
  [{:keys [created-at tx-cid chain category amount-usd-millicents native-asset
           counterparty-did linked-attestation-cid attesting-cell-did]}]
  (when-not (and created-at (not= created-at ""))
    (throw (ex-info "ledger_entry: created_at is required" {})))
  (when-not (and tx-cid (not= tx-cid ""))
    (throw (ex-info "ledger_entry: tx_cid is required" {})))
  (when-not (contains? on-chain-rails chain)
    (throw (ex-info (str "ledger_entry: chain must be one of " on-chain-rails ", got " (pr-str chain)) {})))
  (when-not (contains? ledger-categories category)
    (throw (ex-info (str "ledger_entry: unknown category " (pr-str category)) {})))
  (when (neg? amount-usd-millicents)
    (throw (ex-info "ledger_entry: amount_usd_millicents must be >= 0" {})))
  (when (and native-asset (not (contains? non-fiat-assets native-asset)))
    (throw (ex-info (str "ledger_entry: nativeAsset must be one of " non-fiat-assets ", got " (pr-str native-asset)) {})))
  (when-not (and counterparty-did (not= counterparty-did ""))
    (throw (ex-info "ledger_entry: counterparty_did is required" {})))
  (cond-> {"createdAt" created-at
           "txCid" tx-cid
           "chain" chain
           "category" category
           "amountUsdMillicents" amount-usd-millicents
           "counterpartyDid" counterparty-did
           "attestingCellDid" attesting-cell-did}
    native-asset (assoc "nativeAsset" native-asset)
    linked-attestation-cid (assoc "linkedAttestationCid" linked-attestation-cid)))

(defn solve
  "Cell entry — R0 is reference-only; LIVE ledger writes against real on-chain transactions
  are Council+operator gated (per ADR-2605262900's R0->R1 gate)."
  [& _]
  (throw (ex-info (str "toritate R0: reference accounting computation only. Live ledger "
                       "writes against real on-chain transactions are Council+operator gated.")
                  {})))
