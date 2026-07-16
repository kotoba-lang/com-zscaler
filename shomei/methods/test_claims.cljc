(ns shomei.methods.test-claims
  "test_claims.cljc — identityClaim structural gates (G1/G2/G3/G4/G7).
  1:1 Clojure port of `methods/test_claims.py`. ADR-2606072100. clojure.test;
  `expect_raises(contains=…)` → `shomei.methods._t/expect-raises`. `build_claim(**kwargs)` →
  `(build-claim {:kebab-key …})`. The `canonical_claim_bytes` byte-membership checks read the
  bytes as a UTF-8 string. The `__main__` demo (`run(\"claims\", CASES)`) is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [shomei.methods._t :refer [expect-raises]]
            [shomei.methods.claims :refer [build-claim canonical-claim-bytes
                                           external-subject-hash validate-claim]]))

(defn- canon-str [claim]
  (let [b (canonical-claim-bytes claim)]
    #?(:clj (String. ^bytes b "UTF-8") :cljs b)))

(defn- valid-evm []
  (build-claim
    {:subject-did "did:web:etzhayyim.com:actor:x"
     :factor-kind "wallet-evm"
     :proof-kind "eip191"
     :challenge-nonce "nonce-1"
     :external-subject-hash (external-subject-hash "salt" "0xabc")
     :issued-at 1781000000
     :subject-sig "sig"
     :verified true
     :external-handle "0xabc…"}))

(deftest test-valid-evm-claim-builds
  (let [c (valid-evm)]
    (is (= "key" (get c "factorClass")))
    (validate-claim c)
    (is true)))

(deftest test-external-subject-hash-stable-and-case-insensitive
  (let [a (external-subject-hash "s" "0xABC")
        b (external-subject-hash "s" "0xabc ")]
    (is (and (= a b) (not (str/includes? a "="))))))

(deftest test-g4-wrong-proof-for-factor-raises
  (expect-raises "G4"
    (build-claim {:subject-did "did:web:x" :factor-kind "wallet-evm" :proof-kind "bip322"
                  :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"})))

(deftest test-g3-gov-requires-encrypted-cid
  (expect-raises "encryptedPayloadCid"
    (build-claim {:subject-did "did:web:x" :factor-kind "gov-mynumber" :proof-kind "nfc-jpki"
                  :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"})))

(deftest test-g3-gov-must-not-carry-plaintext-handle
  (expect-raises "plaintext externalHandle"
    (build-claim {:subject-did "did:web:x" :factor-kind "gov-passport" :proof-kind "nfc-jpki"
                  :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"
                  :external-handle "PASSPORT-NO-123" :encrypted-payload-cid "bafyenc"})))

(deftest test-g3-gov-valid-with-cid-no-handle
  (let [c (build-claim {:subject-did "did:web:x" :factor-kind "gov-mynumber" :proof-kind "nfc-jpki"
                        :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"
                        :encrypted-payload-cid "bafyenc" :verified true})]
    (is (= "government" (get c "factorClass")))))

(deftest test-g3-covenant-factor-cannot-carry-handle
  (expect-raises "may not carry a plaintext externalHandle"
    (build-claim {:subject-did "did:web:x" :factor-kind "etz-at-oath" :proof-kind "at-record-sig"
                  :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"
                  :external-handle "leak"})))

(deftest test-g7-no-server-sig-field
  (let [c (assoc (valid-evm) "serverSig" "platform-key-signed")]
    (expect-raises "G7 no-server-key" (validate-claim c))))

(deftest test-g7-subject-sig-mandatory
  (expect-raises "subjectSig"
    (build-claim {:subject-did "did:web:x" :factor-kind "wallet-evm" :proof-kind "eip191"
                  :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig ""})))

(deftest test-g1-did-required
  (expect-raises "subjectDid must be a DID"
    (build-claim {:subject-did "aaron" :factor-kind "wallet-evm" :proof-kind "eip191"
                  :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"})))

(deftest test-canonical-bytes-exclude-sig-and-handle
  (let [c (valid-evm)
        b (canon-str c)]
    (is (and (not (str/includes? b "subjectSig")) (not (str/includes? b "externalHandle"))))
    (is (str/includes? b "externalSubjectHash"))))  ;; the hash IS signed

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-claims)))
