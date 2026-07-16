(ns shomei.methods.test-verify
  "test_verify.cljc — verify wiring policy (challenge binding, single-use, gov gate, crypto, sig).
  1:1 Clojure port of `methods/test_verify.py`. ADR-2606072100. clojure.test;
  `expect_raises(contains=…)` → `shomei.methods._t/expect-raises`.

  `_ref(secret, msg)` = HMAC-SHA256(secret, msg) hex — byte-identical to the host HMAC the
  `reference-verifier` uses internally (so a `_mint`ed proof/sig verifies). The host HMAC lives
  behind #?(:clj …); a :cljs/wasm edge would slot a portable HMAC here, matching verify.cljc.
  `seen_nonces` (a mutable set) → an atom holding a set (the cljc single-use ledger). The
  `__main__` demo (`run(\"verify\", CASES)`) is omitted (clojure.test drives the suite)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [shomei.methods._t :refer [expect-raises]]
            [shomei.methods.claims :refer [build-claim canonical-claim-bytes external-subject-hash]]
            [shomei.methods.verify :as v]
            [shomei.methods.factors :refer [PROOF_KINDS FACTOR_CLASS]]))

(def SUBJECT "did:web:etzhayyim.com:actor:t")
(def SALT "salt-t")
(def TS 1781000000)

;; HMAC-SHA256 → lowercase hex, matching verify.cljc's internal `hmac-sha256-hex`.
#?(:clj
   (defn- ref* [^String secret ^bytes msg]
     (let [mac (javax.crypto.Mac/getInstance "HmacSHA256")
           key (javax.crypto.spec.SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256")]
       (.init mac key)
       (let [^bytes out (.doFinal mac msg)
             sb (StringBuilder.)]
         (dotimes [i (alength out)]
           (let [val (bit-and (long (aget out i)) 0xff)
                 s (.toString (java.math.BigInteger/valueOf val) 16)]
             (when (< val 16) (.append sb "0"))
             (.append sb s)))
         (.toString sb)))))

(defn- ref-str [secret ^String s]
  #?(:clj (ref* secret (.getBytes s "UTF-8")) :cljs s))

(defn- mint
  [factor-kind proof-kind identifier secret & {:keys [handle cid nonce]
                                               :or {nonce "nonce-t"}}]
  (let [esh (external-subject-hash SALT identifier)
        skel {"subjectDid" SUBJECT "factorKind" factor-kind
              "factorClass" (get FACTOR_CLASS factor-kind)
              "proofKind" proof-kind "challengeNonce" nonce "externalSubjectHash" esh
              "verified" true "issuedAt" TS}
        sig #?(:clj (ref* secret (canonical-claim-bytes skel))
               :cljs (ref-str secret (canonical-claim-bytes skel)))
        claim (build-claim
                {:subject-did SUBJECT :factor-kind factor-kind :proof-kind proof-kind
                 :challenge-nonce nonce :external-subject-hash esh :issued-at TS
                 :subject-sig sig :verified true :external-handle handle :encrypted-payload-cid cid})
        challenge {"subjectDid" SUBJECT "factorKind" factor-kind "nonce" nonce
                   "issuedAt" TS "expiresAt" (+ TS 600)}
        pm {"proof" (ref-str secret (str nonce "|" esh))}]
    [claim challenge pm]))

(deftest test-routing-table-covers-every-proof-kind
  (doseq [p PROOF_KINDS]
    (is (contains? v/PROOF_ROUTING p) (str "proofKind " p " not in PROOF_ROUTING wiring table"))))

(deftest test-valid-claim-verifies
  (let [[claim ch pm] (mint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        vr (v/reference-verifier {[SUBJECT "wallet-evm"] "sec1"})
        res (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10))]
    (is (and (get res "verified") (= "ok" (get res "reason"))))))

(deftest test-bad-proof-fails
  (let [[claim ch pm] (mint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        pm (assoc pm "proof" "deadbeef")
        vr (v/reference-verifier {[SUBJECT "wallet-evm"] "sec1"})
        res (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10))]
    (is (and (not (get res "verified")) (= "proof verification failed" (get res "reason"))))))

(deftest test-wrong-subject-secret-fails-signature
  (let [[claim ch pm] (mint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        vr (v/reference-verifier {[SUBJECT "wallet-evm"] "WRONG"})
        res (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10))]
    (is (not (get res "verified")))))

(deftest test-challenge-subject-mismatch
  (let [[claim ch pm] (mint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        ch (assoc ch "subjectDid" "did:web:someone-else")
        vr (v/reference-verifier {[SUBJECT "wallet-evm"] "sec1"})
        res (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10))]
    (is (= "challenge subjectDid mismatch" (get res "reason")))))

(deftest test-challenge-expired
  (let [[claim ch pm] (mint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        vr (v/reference-verifier {[SUBJECT "wallet-evm"] "sec1"})
        res (v/verify-claim claim ch vr :proof-material pm :now (+ TS 999999))]
    (is (= "challenge expired" (get res "reason")))))

(deftest test-nonce-single-use
  (let [[claim ch pm] (mint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        vr (v/reference-verifier {[SUBJECT "wallet-evm"] "sec1"})
        seen (atom #{})
        first* (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10) :seen-nonces seen)
        second* (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10) :seen-nonces seen)]
    (is (get first* "verified"))
    (is (= "nonce already consumed (replay)" (get second* "reason")))))

(deftest test-gov-proof-gated-raises
  (let [[claim ch pm] (mint "gov-mynumber" "nfc-jpki" "MY" "sec1" :cid "bafyenc")
        vr (v/reference-verifier {[SUBJECT "gov-mynumber"] "sec1"})]  ;; allow-gated=false
    (expect-raises "Council-gated"
      (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10)))))

(deftest test-gov-proof-passes-when-gate-open
  (let [[claim ch pm] (mint "gov-mynumber" "nfc-jpki" "MY" "sec1" :cid "bafyenc")
        vr (v/reference-verifier {[SUBJECT "gov-mynumber"] "sec1"} true)
        res (v/verify-claim claim ch vr :proof-material pm :now (+ TS 10))]
    (is (get res "verified"))))

(deftest test-kotoba-auth-verifier-documents-canonical-call
  (let [vr (v/kotoba-auth-verifier)
        [claim _ch pm] (mint "wallet-btc" "bip322" "bc1q" "s" :handle "bc1q")]
    (expect-raises "kotoba_auth::btc::verify_message"
      (v/verify-proof vr "bip322" claim pm))))

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-verify)))
