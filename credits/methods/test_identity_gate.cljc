(ns credits.methods.test-identity-gate
  "credits -- unit tests for methods.identity-gate (DID-bind gate, G10). Synthetic
  DIDs + synthetic factor sets only -- no real identity, no live shomei call, no I/O.
  Also the parity/composition proof: identity-gate genuinely calls shomei's own
  aggregation function rather than reimplementing the IAL ladder."
  (:require [clojure.test :refer [deftest is]]
            [credits.methods._t :refer [expect-raises]]
            [credits.methods.identity-gate :as ig]
            [shomei.methods.aggregate :as shomei-agg]
            [shomei.methods.factors :as shomei-f]))

(def synthetic-did "did:key:z6MkSyntheticCreditsGateTestDid0001")
(def synthetic-now 1720000000000)

;; ── G10 -- no verified factor -> denied (IAL 0, did-only) ──
(deftest test-g10-no-factors-denied
  (let [r (ig/identity-check synthetic-did #{} synthetic-now)]
    (is (false? (:allowed r)))
    (is (= 0 (:assurance-level r)))
    (is (false? (:proof-of-personhood? r)))
    (is (re-find #"assuranceLevel=0" (:reason r)))))

;; ── G10 -- one verified factor -> allowed (IAL 1, self-attested) ──
(deftest test-g10-one-factor-allowed
  (let [r (ig/identity-check synthetic-did #{"webauthn"} synthetic-now)]
    (is (true? (:allowed r)))
    (is (= 1 (:assurance-level r)))
    (is (nil? (:reason r)))))

;; ── G10 -- two factors across two classes -> allowed (IAL 2, still passes the credits floor) ──
(deftest test-g10-multi-factor-allowed
  (let [r (ig/identity-check synthetic-did #{"wallet-evm" "sns-github"} synthetic-now)]
    (is (true? (:allowed r)))
    (is (= 2 (:assurance-level r)))
    (is (true? (:proof-of-personhood? r)))))

;; ── G10 -- an unrecognized factor string never raises the level (mirrors shomei's own filter) ──
(deftest test-g10-unknown-factor-ignored-not-erroring
  (let [r (ig/identity-check synthetic-did #{"not-a-real-shomei-factor"} synthetic-now)]
    (is (false? (:allowed r)))
    (is (= 0 (:assurance-level r)))))

;; ── G10 -- blank/nil subject-did rejected structurally ──
(deftest test-g10-rejects-blank-subject-did
  (expect-raises "subject_did" (ig/identity-check nil #{"webauthn"} synthetic-now))
  (expect-raises "subject_did" (ig/identity-check "" #{"webauthn"} synthetic-now)))

;; ── require-identity! throws :credits/identity-gate-denied when the gate denies ──
(deftest test-require-identity-throws-when-denied
  (expect-raises "assuranceLevel=0" (ig/require-identity! synthetic-did #{} synthetic-now))
  (is (true? (:allowed (ig/require-identity! synthetic-did #{"webauthn"} synthetic-now)))))

;; ── gated-preview-purchase: denies before ever computing the fee ──
(deftest test-gated-preview-purchase-denies-unverified-did
  (expect-raises "identity-gate" (ig/gated-preview-purchase synthetic-did #{} synthetic-now 100)))

(deftest test-gated-preview-purchase-allows-verified-did-and-preserves-g1-math
  (let [r (ig/gated-preview-purchase synthetic-did #{"webauthn"} synthetic-now 100)]
    (is (= 100 (:gross r)))
    (is (= 30 (:fee r)))
    (is (= 70 (:net r)))
    (is (= synthetic-did (:subject-did r)))
    (is (= 1 (:assurance-level r)))))

;; ── gated-compute-spend-allocation: denies before ever computing the allocation ──
(deftest test-gated-spend-allocation-denies-unverified-did
  (expect-raises "identity-gate"
    (ig/gated-compute-spend-allocation synthetic-did #{} synthetic-now 100 nil)))

(deftest test-gated-spend-allocation-allows-verified-did-and-preserves-g2-g3
  (let [r (ig/gated-compute-spend-allocation synthetic-did #{"wallet-btc"} synthetic-now 100
                                              "public-fund:education-family")]
    (is (= 100 (:amount r)))
    (is (= 10 (:public-fund-amount r)))
    (is (= "public-fund:education-family" (:destination-id r)))
    (is (= synthetic-did (:subject-did r)))))

;; ── genuine composition proof: identity-gate's numbers ARE shomei's numbers, not a
;;    reimplementation (parity test in the ainori/todoke house style) ──
(deftest test-parity-with-shomei-aggregate
  (doseq [factors [#{} #{"webauthn"} #{"wallet-evm" "sns-x"}
                   #{"etz-adherent-sbt" "gov-passport"}]]
    (let [ours (ig/identity-check synthetic-did factors synthetic-now)
          theirs (shomei-agg/aggregate synthetic-did factors :issued-at synthetic-now)]
      (is (= (:assurance-level ours) (get theirs "assuranceLevel")))
      (is (= (:proof-of-personhood? ours) (get theirs "proofOfPersonhood"))))))

;; ── min-assurance-level is IAL-1 self-attested, named via shomei's own ladder labels ──
(deftest test-min-assurance-level-is-self-attested
  (is (= 1 ig/min-assurance-level))
  (is (= "self-attested" (shomei-agg/assurance-label ig/min-assurance-level)))
  (is (contains? shomei-f/FACTOR_KINDS "webauthn")))
