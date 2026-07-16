#!/usr/bin/env bb
;; iriai 入会 — self-did:key identity tests (keygen / did:key / present-only sign+verify).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_identity.cljc
(ns iriai.methods.test-identity
  (:require [iriai.methods.identity :as id]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(deftest did-key-encodes-ed25519-multicodec
  ;; 32 zero bytes → did:key:z6Mk… (Ed25519 multicodec 0xed01)
  (is (str/starts-with? (id/did-key (byte-array 32)) "did:key:z6Mk")))

(deftest base58btc-self-contained
  ;; sanity vectors (no multiformats dep): leading zero → '1'; known small encodings
  (is (= "1" (id/b58btc (byte-array [0]))))
  (is (= "2g" (id/b58btc (byte-array [97]))))            ; 0x61 = 'a' → "2g"
  (is (= "1111" (id/b58btc (byte-array [0 0 0 0])))))

(deftest actor-generates-its-own-keypair
  (let [{:keys [did public private seed]} (id/gen-keypair)]
    (is (str/starts-with? did "did:key:z6Mk"))   ; a real Ed25519 did:key
    (is (= 32 (alength public)))                  ; raw public key
    (is (= 32 (alength seed)))                    ; raw private seed for Keychain sealing
    (is (some? private))
    (is (= did (id/did-key public)))))            ; did is a pure fn of the public key

(deftest sign-then-verify-roundtrip   ; present-only: the actor signs with its sealed key
  (let [{:keys [public private]} (id/gen-keypair)
        msg (.getBytes "did:key|bafkreiiriaididdoc" "UTF-8")
        sig (id/sign private msg)]
    (is (id/verify public msg sig))                                    ; valid sig verifies
    (is (not (id/verify public (.getBytes "tampered" "UTF-8") sig))))) ; tamper detected

(deftest different-keys-do-not-cross-verify
  (let [a (id/gen-keypair) b (id/gen-keypair)
        msg (.getBytes "x" "UTF-8")
        sig (id/sign (:private a) msg)]
    (is (not= (:did a) (:did b)))                  ; each actor mint is unique
    (is (not (id/verify (:public b) msg sig)))))   ; B cannot verify A's signature

(deftest attest-message-shape
  (is (= "did:key:zABC|bafcid" (id/attest-did-doc "did:key:zABC" "bafcid"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-identity)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
