(ns post-quantum-compat.methods.test-suite
  "Cross-language oracle tests for post-quantum-compat.methods.suite.
  Expected values captured by running the REAL Python (methods/suite.py)."
  (:require [clojure.test :refer [deftest is]]
            [post-quantum-compat.methods.suite :as s]))

(deftest layer-registry-shape
  ;; python: len(suite.LAYERS) == 11
  (is (= 11 (count s/LAYERS)))
  ;; python: list(suite.SUITES.keys()) == [":suite/pqh-v1"]
  (is (= [":suite/pqh-v1"] (vec (keys s/SUITES))))
  ;; layer ids in source order
  (is (= [":layer/record-at-rest" ":layer/vault-at-rest" ":layer/hashes"
          ":layer/key-wrap" ":layer/did-signal-binding" ":layer/did-doc-attestation"
          ":layer/password-kdf" ":layer/production-pq-keys" ":layer/governance-signature"
          ":layer/libsignal-path" ":layer/passkey-signature"]
         (mapv #(get % ":layer/id") s/LAYERS)))
  ;; status enum sets
  (is (= #{":migrated" ":adequate"} s/MIGRATION-DONE))
  (is (= #{":operator-pending" ":chain-blocked" ":upstream-pending" ":deferred"} s/GATED)))

(deftest suite-constants
  (let [pqh (get s/SUITES ":suite/pqh-v1")
        kem (get pqh ":suite/kem")
        sig (get pqh ":suite/sig")
        kdf (get pqh ":suite/kdf")]
    (is (= "pqh-v1" (get pqh ":suite/id")))
    (is (= "ML-KEM-768" (get kem ":kem/pq")))
    (is (= 1184 (get kem ":kem/pq-public-bytes")))
    (is (= 1088 (get kem ":kem/pq-ciphertext-bytes")))
    (is (= 32 (get kem ":kem/shared-secret-bytes")))
    (is (= 0x120C (get kem ":kem/pq-multicodec")))   ; 4620
    (is (= "ML-DSA-65" (get sig ":sig/pq")))
    (is (= 1952 (get sig ":sig/pq-public-bytes")))
    (is (= 3309 (get sig ":sig/pq-signature-bytes")))
    (is (= 0x1211 (get sig ":sig/pq-multicodec")))    ; 4625
    (is (= "argon2id-v1" (get kdf ":kdf/id")))
    (is (= 19456 (get kdf ":kdf/default-m-kib")))
    (is (= 2 (get kdf ":kdf/default-t")))
    (is (= 1 (get kdf ":kdf/default-p")))))

(deftest math-helpers
  ;; python: grover_effective_bits(128) == 64, (256) == 128
  (is (= 64 (s/grover-effective-bits 128)))
  (is (= 128 (s/grover-effective-bits 256)))
  ;; python: mosca(10,2,15) -> {act-now False, slack 3}; mosca(5,2,15) -> slack 8
  (is (= {":mosca/act-now" false ":mosca/slack-years" 3} (s/mosca 10 2 15)))
  (is (= {":mosca/act-now" false ":mosca/slack-years" 8} (s/mosca 5 2 15)))
  (is (= true (get (s/mosca 10 10 15) ":mosca/act-now")))
  ;; shor-applies
  (is (true? (s/shor-applies {":layer/quantum-attack" ":shor"})))
  (is (false? (s/shor-applies {":layer/quantum-attack" ":grover"}))))

(deftest coverage-report-oracle
  ;; python: suite.coverage_report() ==
  ;; {layers-total 11, shor-vulnerable 7, migrated 3, gated 4, unknown 0,
  ;;  migrated-fraction 0.4286, gated-ids [...sorted...]}
  (let [c (s/coverage-report)]
    (is (= 11 (get c ":coverage/layers-total")))
    (is (= 7 (get c ":coverage/shor-vulnerable")))
    (is (= 3 (get c ":coverage/migrated")))
    (is (= 4 (get c ":coverage/gated")))
    (is (= 0 (get c ":coverage/unknown")))
    (is (= 0.4286 (get c ":coverage/migrated-fraction")))
    (is (= [":layer/governance-signature" ":layer/libsignal-path"
            ":layer/passkey-signature" ":layer/production-pq-keys"]
           (get c ":coverage/gated-ids")))))
