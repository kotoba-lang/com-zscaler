(ns shomei.methods.test-charter-invariants
  "test_charter_invariants.cljc — the load-bearing gates, enforced in THREE places each
  (factors SSoT + lexicon enum + Python ValueError). 1:1 Clojure port of
  `methods/test_charter_invariants.py`. ADR-2606072100. clojure.test;
  `expect_raises(contains=…)` → `shomei.methods._t/expect-raises`. The `__main__`
  demo (`run(\"charter_invariants\", CASES)`) is omitted (clojure.test drives the suite)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [shomei.methods._t :refer [expect-raises]]
            [shomei.methods.aggregate :refer [aggregate]]
            [shomei.methods.claims :refer [build-claim validate-claim]]
            [shomei.methods.factors :refer [GATED_PROOFS PROOF_KINDS]]
            [shomei.methods.verify :refer [PROOF_ROUTING reference-verifier verify-claim]]))

(defn- g [& {:as kw}]
  (let [base {:subject-did "did:web:etzhayyim.com:actor:i" :factor-kind "wallet-evm"
              :proof-kind "eip191" :challenge-nonce "n" :external-subject-hash "h"
              :issued-at 1 :subject-sig "s"}]
    (build-claim (merge base kw))))

;; G1 self-sovereign / DID-primary
(deftest test-g1-did-required
  (expect-raises "must be a DID" (g :subject-did "aaron")))

;; G2 own-identity-only is structural: there is NO field to assert a THIRD party's identity.
(deftest test-g2-no-third-party-subject-field
  (let [c (g :external-handle "0xabc")]
    (is (and (not (contains? c "assertedAboutDid")) (not (contains? c "targetDid"))))
    ;; the only identity field is subjectDid (== the signer)
    (is (= "did:web:etzhayyim.com:actor:i" (get c "subjectDid")))))

;; G3 PII-never-plaintext
(deftest test-g3-gov-requires-encrypted-cid
  (expect-raises "encryptedPayloadCid"
    (g :factor-kind "gov-mynumber" :proof-kind "nfc-jpki")))

(deftest test-g3-gov-rejects-plaintext-handle
  (expect-raises "plaintext externalHandle"
    (g :factor-kind "gov-license" :proof-kind "nfc-jpki"
       :encrypted-payload-cid "bafy" :external-handle "DL-123")))

;; G4 cryptographic-proof-mandatory — every proofKind is wired to a kotoba-auth call
(deftest test-g4-routing-total
  (is (set/superset? (set (keys PROOF_ROUTING)) (set PROOF_KINDS))))

(deftest test-g4-wrong-proof-for-factor-unrepresentable
  (expect-raises "G4" (g :factor-kind "wallet-btc" :proof-kind "eip191")))

;; G7 no-server-key — no server signature field can exist on a claim
(deftest test-g7-server-sig-unrepresentable
  (let [c (assoc (g :external-handle "0xabc") "operatorSig" "x")]
    (expect-raises "G7 no-server-key" (validate-claim c))))

;; G8 identity-assurance-not-social-credit — credential never carries a worth/score field,
;; and assurance is a deterministic function of factor classes (no behavioral input).
(deftest test-g8-no-score-field-and-deterministic
  (let [a (aggregate "did:web:x" #{"wallet-evm" "sns-x"} :issued-at 1)
        b (aggregate "did:web:x" #{"sns-x" "wallet-evm"} :issued-at 1)]  ;; order-independent
    (is (= (get a "assuranceLevel") (get b "assuranceLevel")))
    (doseq [forbidden ["score" "rank" "reputation" "worth" "behavior"]]
      (is (not (contains? a forbidden))))))

;; G11 gov gate — gov L2 proof is Council-gated; verify raises at R0
(deftest test-g11-gov-gate-in-force
  (is (= #{"nfc-jpki"} GATED_PROOFS))
  (let [c (g :factor-kind "gov-passport" :proof-kind "nfc-jpki" :encrypted-payload-cid "bafy")
        ch {"subjectDid" (get c "subjectDid") "factorKind" "gov-passport"
            "nonce" "n" "issuedAt" 1 "expiresAt" 10000000000}
        vr (reference-verifier {} false)]
    (expect-raises "Council-gated"
      (verify-claim c ch vr :proof-material {} :now 2))))

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-charter-invariants)))
