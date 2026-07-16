(ns wakai.methods.test-charter-gates
  "wakai 和会 — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- consts [doc]
  "field-name -> const value, for every const declared in the lexicon tree."
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (contains? x "const") (string? parent)) (swap! acc assoc parent (get x "const")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── G6 — no investment-return promise / no speculation ──
(deftest test-g6-no-investment-return-promise
  (let [c (consts (lex "mutualAidContributionAttestation"))]
    (is (= (get c "investmentReturnPromised") false) "G6: investmentReturnPromised must be const false")
    (is (contains? (required-union (lex "mutualAidContributionAttestation")) "investmentReturnPromised"))))

(deftest test-g6-pool-is-stable-only-no-defi-speculation
  (let [c (consts (lex "mutualAidPoolStateReport"))]
    (is (= (get c "poolAssetClass") "usdc-stable-only") "G6: pool must be usdc-stable-only")
    (is (= (get c "defiYieldFarmingActiveCount") 0) "G6: no DeFi yield farming")
    (is (= (get c "tokenSpeculationActiveCount") 0) "G6: no token speculation")))

;; ── G3 — NOT insurance: no claim adjudication / no denial ──
(deftest test-g3-not-insurance-no-claim-adjudication
  (let [c (consts (lex "mutualAidDistributionAttestation"))]
    (is (= (get c "claimAdjudicated") false) "G3: claimAdjudicated must be const false (community discernment, not adjudication)")))

;; ── G7 — no pre-existing-condition exclusion / no underwriting ──
(deftest test-g7-no-pre-existing-condition-exclusion
  (let [doc (lex "mutualAidDistributionAttestation")
        c (consts doc)]
    (is (= (get c "noPreExistingConditionExclusion") true) "G7: noPreExistingConditionExclusion must be const true")
    (is (contains? (required-union doc) "noPreExistingConditionExclusion"))))

;; ── G9 — community discernment distribution (Council ≥3) ──
(deftest test-g9-distribution-requires-community-and-council-attestations
  (let [req (required-union (lex "mutualAidDistributionAttestation"))]
    (is (contains? req "communityDiscernmentAttestations") "G9: distribution must carry community discernment attestations")
    (is (contains? req "councilAttestations") "G9: distribution must carry Council attestations")))

;; ── L5 silenWakaiReview — the full anti-insurance / anti-speculation const ledger ──
(deftest test-silen-review-const-ledger-exact
  (let [c (consts (lex "silenWakaiReview"))
        expected {"commercialInsuranceSoftwarePenetrationPct" 0
                  "commercialReInsurancePenetrationPct" 0
                  "defiYieldFarmingActiveCount" 0
                  "tokenSpeculationActiveCount" 0
                  "claimDenialEventsCount" 0
                  "preExistingConditionExclusionEventsCount" 0
                  "administratorVocationFlowCompliantRatioPctIntegerHundredths" 10000}]
    (doseq [[field want] expected]
      (is (= (get c field) want) (str "silenWakaiReview." field " must be const " want)))))

(deftest test-silen-review-requires-its-const-fields
  (let [req (required-union (lex "silenWakaiReview"))]
    (doseq [field ["commercialInsuranceSoftwarePenetrationPct" "claimDenialEventsCount" "preExistingConditionExclusionEventsCount"]]
      (is (contains? req field) (str "silenWakaiReview must require " field)))))
