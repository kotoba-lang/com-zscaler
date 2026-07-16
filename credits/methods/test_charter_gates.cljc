(ns credits.methods.test-charter-gates
  "credits -- constitutional-gate conformance tests (G1-G10; G10 added iteration
  #5, 2026-07-10, the identity-gate slice). Structural shape follows the
  mizuho/toritate house style (methods/test_charter_gates.cljc pinning fixed
  policy constants + manifest.edn gate declarations) but pins against credits'
  own pure functions/constants directly rather than external Lexicon JSON
  (credits has no Lexicons yet -- building those is out of scope for this
  first slice, see README.md)."
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [credits.methods._t :refer [expect-raises]]
            [credits.methods.anti-fraud :as af]
            [credits.methods.identity-gate :as ig]
            [credits.methods.ledger-rails :as rails]
            [credits.methods.purchase :as purchase]
            [credits.methods.spend-allocation :as spend]))

;; ── manifest.edn gate declarations (documentation <-> code sync) ──
#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))
     (def ^:private actor-dir (.getParentFile here))
     (defn- manifest []
       (edn/read-string (slurp (java.io.File. actor-dir "manifest.edn"))))))

#?(:clj
   (deftest test-all-10-gates-declared
     (let [gates (set (map :gate/id (:actor/gates (manifest))))]
       (is (= gates (set (map #(str "G" %) (range 1 11))))))))

;; ── G1 -- purchase platform fee is a fixed 30% ──
(deftest test-g1-purchase-fee-is-fixed-30pct
  (is (= 3000 purchase/platform-fee-bps))
  (is (= 30 (:fee (purchase/preview-purchase 100)))))

;; ── G2 -- spend public-fund allocation is a fixed 10% ──
(deftest test-g2-spend-allocation-is-fixed-10pct
  (is (= 1000 spend/public-fund-bps))
  (is (= 10 (:public-fund-amount (spend/compute-spend-allocation 100 nil)))))

;; ── G3 -- allocation destination enum + default ──
(deftest test-g3-allocation-destinations-and-default
  (is (= #{"public-fund:common" "public-fund:education-family"
           "public-fund:health-access" "public-fund:climate-resilience"}
         spend/allocation-destinations))
  (is (= "public-fund:common" spend/default-destination-id))
  (is (= spend/default-destination-id (spend/resolve-destination nil)))
  (expect-raises "unknown allocation destination_id"
    (spend/resolve-destination "not-a-declared-fund")))

;; ── G4 -- anti-fraud rate limits ──
(deftest test-g4-rate-limits
  (is (= 60 af/spend-rate-limit-per-hour))
  (is (= 30 af/earn-rate-limit-per-hour))
  (is (false? (:allowed (af/check-spend-allowed {:recent-spend-count 60}))))
  (is (false? (:allowed (af/check-earn-allowed {:recent-earn-count 30})))))

;; ── G5 -- high-value earn reject (>50) ──
(deftest test-g5-high-value-earn-reject
  (is (= 50 af/high-value-earn-reject-threshold))
  (is (true? (:allowed (af/check-earn-allowed {:amount 50}))))
  (is (= "high-value-earn-reject" (:reason (af/check-earn-allowed {:amount 50.01})))))

;; ── G6 -- HC reputation gate (approval_rate < 50% rejected) ──
(deftest test-g6-hc-reputation-gate
  (is (= 0.5 af/hc-reputation-gate-min-approval-rate))
  (is (= "hc-reputation-gate" (:reason (af/check-earn-allowed {:approval-rate 0.49})))))

;; ── G7 -- duplicate reward rejected ──
(deftest test-g7-duplicate-reward-rejected
  (is (= "duplicate-reward-task"
         (:reason (af/check-earn-allowed {:task-id "t1" :seen-task-ids #{"t1"}}))))
  (is (= "duplicate-reward-session"
         (:reason (af/check-earn-allowed {:session-id "s1" :seen-session-ids #{"s1"}})))))

;; ── G8 -- native asset is never a fiat currency ──
(deftest test-g8-native-asset-non-fiat
  (is (= "credit" rails/native-asset))
  (is (not (contains? rails/fiat-currency-codes rails/native-asset))))

;; ── G9 -- no banned commercial payment/ads vendor is a valid rail ──
(deftest test-g9-no-banned-vendor-rail
  (doseq [v rails/banned-payment-vendors]
    (is (not (rails/valid-payment-rail? v)))))

;; ── G10 -- shomei-verified DID (IAL>=1) required before purchase/spend proceed ──
(deftest test-g10-did-bind-identity-gate
  (is (= 1 ig/min-assurance-level))
  (is (false? (:allowed (ig/identity-check "did:key:zTest" #{} 1720000000000))))
  (is (true? (:allowed (ig/identity-check "did:key:zTest" #{"webauthn"} 1720000000000))))
  (expect-raises "identity-gate"
    (ig/gated-preview-purchase "did:key:zTest" #{} 1720000000000 100))
  (expect-raises "identity-gate"
    (ig/gated-compute-spend-allocation "did:key:zTest" #{} 1720000000000 100 nil)))
