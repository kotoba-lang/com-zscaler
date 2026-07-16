#!/usr/bin/env bb
;; tsubasa 翼 — self-did:key identity tests (keygen / did:key / present-only sign+verify).
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_identity.cljc
(ns tsubasa.methods.test-identity
  (:require [tsubasa.methods.identity :as id]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(deftest did-key-encodes-ed25519-multicodec
  ;; known vector: 32 zero bytes → did:key:z6Mk… (Ed25519 multicodec 0xed01)
  (let [did (id/did-key (byte-array 32))]
    (is (str/starts-with? did "did:key:z6Mk"))))

(deftest actor-generates-its-own-keypair
  (let [{:keys [did public private]} (id/gen-keypair)]
    (is (str/starts-with? did "did:key:z6Mk"))   ; a real Ed25519 did:key
    (is (= 32 (alength public)))                  ; raw public key
    (is (some? private))
    ;; the did is a pure function of the public key (self-certifying)
    (is (= did (id/did-key public)))))

(deftest sign-then-verify-roundtrip   ; present-only: the actor signs with its sealed key
  (let [{:keys [public private]} (id/gen-keypair)
        msg (.getBytes "did:key|bafkreitsubasadiddoc" "UTF-8")
        sig (id/sign private msg)]
    (is (id/verify public msg sig))                          ; valid signature verifies
    (is (not (id/verify public (.getBytes "tampered" "UTF-8") sig)))))  ; tamper detected

(deftest different-keys-do-not-cross-verify
  (let [a (id/gen-keypair) b (id/gen-keypair)
        msg (.getBytes "x" "UTF-8")
        sig (id/sign (:private a) msg)]
    (is (not= (:did a) (:did b)))                ; each actor mint is unique
    (is (not (id/verify (:public b) msg sig)))))  ; B cannot verify A's signature

(deftest attest-message-shape
  (is (= "did:key:zABC|bafcid" (id/attest-did-doc "did:key:zABC" "bafcid"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-identity)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
