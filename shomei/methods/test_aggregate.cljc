(ns shomei.methods.test-aggregate
  "test_aggregate.cljc — personhoodCredential + W3C VC (G3 no-PII, G8 no social-credit).
  1:1 Clojure port of `methods/test_aggregate.py`. ADR-2606072100. clojure.test. The Python
  `repr(c)` no-PII scan → `(pr-str c)` (the credential's string rendering). The `__main__`
  demo (`run(\"aggregate\", CASES)`) is omitted (clojure.test drives the suite)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [shomei.methods.aggregate :refer [aggregate assurance-label did-hash
                                              is-covenant-bound to-w3c-vc]]))

(def SUB "did:web:etzhayyim.com:actor:agg")

(deftest test-did-only-ial0
  (let [c (aggregate SUB #{} :issued-at 1)]
    (is (and (= 0 (get c "assuranceLevel")) (= false (get c "proofOfPersonhood"))))))

(deftest test-single-factor-ial1
  (let [c (aggregate SUB #{"wallet-evm"} :issued-at 1)]
    (is (and (= 1 (get c "assuranceLevel")) (= 1 (get c "distinctClasses"))))))

(deftest test-multi-class-ial2-is-pop
  (let [c (aggregate SUB #{"wallet-evm" "sns-github"} :issued-at 1)]
    (is (and (= 2 (get c "assuranceLevel")) (= true (get c "proofOfPersonhood"))))))

(deftest test-two-wallets-one-class-not-pop
  (let [c (aggregate SUB #{"wallet-evm" "wallet-btc"} :issued-at 1)]
    (is (and (= 1 (get c "distinctClasses")) (= 1 (get c "assuranceLevel"))
             (= false (get c "proofOfPersonhood"))))))

(deftest test-covenant-bound-ial3-self-issued
  (let [c (aggregate SUB #{"webauthn" "etz-adherent-sbt"} :issued-at 1)]
    (is (and (= 3 (get c "assuranceLevel")) (= SUB (get c "issuer"))))))  ;; self-issued ≤ IAL3

(deftest test-gov-ial4-council-issued
  (let [c (aggregate SUB #{"webauthn" "gov-mynumber"} :issued-at 1)]
    (is (and (= 4 (get c "assuranceLevel"))
             (str/ends-with? (get c "issuer") "council:attestor")))))

(deftest test-no-pii-in-credential
  ;; G3: no EXTERNAL identifiers (handles/addresses/gov numbers/names). The subject's own DID
  ;; legitimately appears as `issuer` of a self-issued VC; linkage uses subjectDidHash.
  (let [c (aggregate SUB #{"wallet-evm" "sns-x"} :issued-at 1)
        blob (pr-str c)]
    (is (and (not (str/includes? blob "0x")) (not (str/includes? blob "@"))))  ;; no handles/addresses
    (is (= (did-hash SUB) (get c "subjectDidHash")))
    (is (= SUB (get c "issuer")))))  ;; self-issued ≤ IAL3

(deftest test-no-social-credit-fields
  (let [c (aggregate SUB #{"wallet-evm" "sns-x"} :issued-at 1)]
    (doseq [forbidden ["score" "rank" "reputation" "trustScore" "worth" "behavior"]]
      (is (not (contains? c forbidden))
          (str "G8: " forbidden " must not exist in a personhoodCredential")))))

(deftest test-w3c-vc-shape
  (let [c (aggregate SUB #{"wallet-evm" "sns-x"} :issued-at 1)
        vc (to-w3c-vc SUB c)]
    (is (= ["VerifiableCredential" "EtzhayyimPersonhoodCredential"] (get vc "type")))
    (is (= SUB (get-in vc ["credentialSubject" "id"])))
    (is (contains? (get vc "credentialSubject") "proofOfPersonhood"))))

(deftest test-helpers
  (is (= "government-verified" (assurance-label 4)))
  (is (= true (is-covenant-bound #{"etz-at-oath"})))
  (is (= false (is-covenant-bound #{"wallet-evm"}))))

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-aggregate)))
