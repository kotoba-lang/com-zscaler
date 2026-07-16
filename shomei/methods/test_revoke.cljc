(ns shomei.methods.test-revoke
  "test_revoke.cljc — append-only revocation (G5 owner-only, G10 as-of).
  1:1 Clojure port of `methods/test_revoke.py`. ADR-2606072100. clojure.test;
  `expect_raises(contains=…)` → `shomei.methods._t/expect-raises`. The Python `__main__`
  demo (`run(\"revoke\", CASES)`) is omitted (clojure.test drives the suite)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [shomei.methods._t :refer [expect-raises]]
            [shomei.methods.revoke :refer [active-verified-factors validate-revocation]]))

(def SUB "did:web:etzhayyim.com:actor:rev")

(defn- claim*
  ([kind cid] (claim* kind cid 100 true))
  ([kind cid issued] (claim* kind cid issued true))
  ([kind cid issued verified]
   {"subjectDid" SUB "factorKind" kind "verified" verified
    "issuedAt" issued "cid" cid}))

(defn- rev*
  [kind claim-ref & {:keys [at reason subject] :or {at 200 reason "key-rotated" subject SUB}}]
  {"subjectDid" subject "claimRef" claim-ref "factorKind" kind
   "reason" reason "revokedAt" at "subjectSig" "sig"})

(deftest test-valid-revocation
  (validate-revocation (rev* "wallet-evm" "claim:1"))
  (is true))

(deftest test-g5-only-owner-revokes
  (let [claim (claim* "wallet-evm" "claim:1")]
    (expect-raises "only the owner revokes"
      (validate-revocation (rev* "wallet-evm" "claim:1" :subject "did:web:other") claim))))

(deftest test-unknown-reason-raises
  (expect-raises "unknown revocation reason"
    (validate-revocation (rev* "wallet-evm" "c" :reason "nuke"))))

(deftest test-active-excludes-revoked-by-ref
  (let [claims [(claim* "wallet-evm" "claim:1") (claim* "sns-x" "claim:2")]
        revs [(rev* "wallet-evm" "claim:1")]]
    (is (= #{"sns-x"} (active-verified-factors claims revs)))))

(deftest test-active-excludes-unverified
  (let [claims [(claim* "wallet-evm" "claim:1" 100 false)]]
    (is (= #{} (active-verified-factors claims [])))))

(deftest test-revocation-does-not-delete-history
  ;; The claim object is untouched; only aggregation excludes it (as-of, 永久記憶).
  (let [claims [(claim* "wallet-evm" "claim:1")]
        revs [(rev* "wallet-evm" "claim:1")]]
    (active-verified-factors claims revs)
    (is (and (= "wallet-evm" (get (first claims) "factorKind"))
             (= true (get (first claims) "verified"))))))

(deftest test-revoke-by-kind-when-no-ref-link
  (let [claims [(claim* "sns-github" nil 100)]
        revs [(rev* "sns-github" "missing-ref" :at 150)]]
    (is (= #{} (active-verified-factors claims revs)))))

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-revoke)))
