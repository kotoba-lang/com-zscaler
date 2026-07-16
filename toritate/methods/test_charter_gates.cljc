(ns toritate.methods.test-charter-gates
  "toritate 執帳 — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(def ^:private ON-CHAIN-RAILS #{"base-l2" "geth-private" "ipfs-record-only"})
(def ^:private NON-FIAT-ASSETS #{"usdc" "eth" "n-a"})
(def ^:private FIAT-TOKENS ["usd" "jpy" "eur" "gbp" "cny" "fiat"])
(def ^:private PAYROLL-TOKENS ["salary" "wage" "payroll" "bonus" "compensation"])

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (contains? x "knownValues") (= parent field)) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

;; ── G3/G4 — 100% on-chain ledger ──
(deftest test-ledger-entry-is-on-chain
  (let [doc (lex "ledgerEntry")
        req (required-union doc)]
    (doseq [field ["chain" "txCid" "counterpartyDid" "amountUsdMillicents"]]
      (is (contains? req field) (str "G3/G4: ledgerEntry must require " field)))
    (is (= (known doc "chain") ON-CHAIN-RAILS) "G3/G4: ledger chain must be exactly the on-chain rails")))

;; ── G8 — no fiat asset is representable ──
(deftest test-ledger-asset-is-non-fiat
  (let [assets (known (lex "ledgerEntry") "nativeAsset")]
    (is (= assets NON-FIAT-ASSETS) "G8: nativeAsset must be exactly the non-fiat set")
    (doseq [tok FIAT-TOKENS]
      (is (not (contains? assets tok)) (str "G8: fiat asset '" tok "' must not be representable")))))

;; ── G8 — commercial accounting software + fiat leak are surfaced as audit observations ──
(deftest test-audit-surfaces-commercial-software-and-fiat-leak
  (let [cats (known (lex "auditObservation") "observationCategory")]
    (doseq [c ["commercial-accounting-software-integration-attempt" "fiat-leak-attempt" "tithe-split-mismatch"]]
      (is (contains? cats c) (str "G8: auditObservation must be able to flag '" c "'")))))

;; ── G12 — no payroll: no salary/wage category exists ──
(deftest test-no-payroll-category
  (let [cats (known (lex "ledgerEntry") "category")
        low (set (map str/lower-case cats))]
    (doseq [tok PAYROLL-TOKENS]
      (is (not (some #(str/includes? % tok) low)) (str "G12: ledger category must not include payroll term '" tok "'")))
    (is (and (contains? cats "subsistence-flow") (contains? cats "vocation-flow")) "G12: volunteer-economy flow categories must exist")))

;; ── tithe 90/10 split is structural ──
(deftest test-tithe-split-categories-present
  (let [cats (known (lex "ledgerEntry") "category")]
    (is (contains? cats "tithe-split-90pct-operational") "tithe: 90% operational split category must exist")
    (is (contains? cats "tithe-split-10pct-public-fund") "tithe: 10% Public Fund split category must exist")))

;; ── donor-PII protection ──
(deftest test-financial-attestation-protects-donor-pii
  (let [doc (lex "financialAttestation")]
    (is (contains? (required-union doc) "publishedDonorPii") "donor-PII: financialAttestation must declare publishedDonorPii")
    (is (= (known doc "publishedDonorPii") #{"none" "aggregated-only" "opt-in-explicit"}) "donor-PII: publishedDonorPii must be exactly the protected set")))

;; ── Council attestation on annual + external-auditor records ──
(deftest test-council-attestation-required
  (is (contains? (required-union (lex "annualReport")) "councilAttestations") "annualReport must require councilAttestations")
  (is (contains? (required-union (lex "externalAuditorEngagement")) "councilAttestations") "externalAuditorEngagement must require councilAttestations"))
