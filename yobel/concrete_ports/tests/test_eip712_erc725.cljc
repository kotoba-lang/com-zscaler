(ns yobel.concrete-ports.tests.test-eip712-erc725
  "Tests for the EIP-712 signed-consent verifier port. Cross-consistency with the
  Python signer is anchored by:
    1. canonical-json byte-identical to Python json.dumps(sort_keys, (\",\",\":\"))
    2. eth-crypto's eip712-digest+ecrecover proven against the EIP-712 spec vector
       (see test-eth-crypto)
  Here we add a full sign→verify round trip over a CreditorConsent struct using a
  test-only ECDSA signer, exercising the port's message construction, resolver,
  checksum compare, and accept/reject paths."
  (:require [clojure.test :refer [deftest is testing]]
            [eth-crypto.core :as eth]
            [yobel.concrete-ports.eip712-erc725 :as port]
            [yobel.ports :as ports]))

;; ── test-only secp256k1 signer (verify-only port has no sign) ──
(def ^BigInteger N (BigInteger. "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141" 16))
(def ^BigInteger P (BigInteger. "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F" 16))
(def ^BigInteger Gx (BigInteger. "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798" 16))
(def ^BigInteger Gy (BigInteger. "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8" 16))
(def G [Gx Gy])

(defn- padl [^bytes b]
  (let [n (alength b) o (byte-array 32)]
    (cond (= n 32) b
          (< n 32) (do (System/arraycopy b 0 o (- 32 n) n) o)
          :else (java.util.Arrays/copyOfRange b (- n 32) n))))

(defn- padd [p1 p2]
  (cond (nil? p1) p2 (nil? p2) p1
        :else (let [[x1 y1] p1 [x2 y2] p2]
                (if (and (= x1 x2) (= (.mod (.add ^BigInteger y1 ^BigInteger y2) P) BigInteger/ZERO)) nil
                    (let [m (if (and (= x1 x2) (= y1 y2))
                              (.mod (.multiply (.multiply (BigInteger/valueOf 3) (.multiply x1 x1))
                                               (.modInverse (.multiply (BigInteger/valueOf 2) y1) P)) P)
                              (.mod (.multiply (.subtract y2 y1) (.modInverse (.subtract x2 x1) P)) P))
                          x3 (.mod (.subtract (.subtract (.multiply m m) x1) x2) P)
                          y3 (.mod (.subtract (.multiply m (.subtract x1 x3)) y1) P)]
                      [x3 y3])))))

(defn- pmul [^BigInteger k pt]
  (loop [k k acc nil base pt]
    (if (= k BigInteger/ZERO) acc
        (recur (.shiftRight k 1) (if (.testBit k 0) (padd acc base) acc) (padd base base)))))

(defn- address-of ^String [^BigInteger d]
  (let [[qx qy] (pmul d G)
        pub (byte-array 64)]
    (System/arraycopy (padl (.toByteArray qx)) 0 pub 0 32)
    (System/arraycopy (padl (.toByteArray qy)) 0 pub 32 32)
    (eth/eip55-checksum (java.util.Arrays/copyOfRange (eth/keccak256 pub) 12 32))))

(defn- sign ^bytes [^bytes digest ^BigInteger d ^BigInteger k]
  (let [e (BigInteger. 1 digest)
        R (pmul k G)
        [rx ry] R
        r (.mod rx N)
        s0 (.mod (.multiply (.modInverse k N) (.add e (.multiply r d))) N)
        rec0 (if (.testBit ry 0) 1 0)
        halfn (.shiftRight N 1)
        low? (<= (.compareTo s0 halfn) 0)
        s (if low? s0 (.subtract N s0))
        rec (if low? rec0 (bit-xor rec0 1))
        sig (byte-array 65)]
    (System/arraycopy (padl (.toByteArray r)) 0 sig 0 32)
    (System/arraycopy (padl (.toByteArray s)) 0 sig 32 32)
    (aset-byte sig 64 (unchecked-byte (+ 27 rec)))
    sig))

;; ── round trip ──
(def chain-id 31337)
(def verifying-contract "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC")
(def creditor-did "did:web:creditor.example")
(def priv (BigInteger. "1111111111111111111111111111111111111111111111111111111111111111" 16))
(def debts [{"debt_id" "d1" "principal_micro_usdc" 100 "origination_date" "2022-01-01T00:00:00Z"}])
(def rite-id-hex (str "0x" (eth/bytes->hex (eth/keccak256 (eth/utf8 "shmita-5786")))))

(defn- consent-digest []
  (let [domain {"name" "YobelCreditorConsent" "version" "1"
                "chainId" chain-id "verifyingContract" verifying-contract}
        types {"CreditorConsent" [{:name "riteId" :type "bytes32"}
                                  {:name "creditorDidHash" :type "bytes32"}
                                  {:name "debtsRootHash" :type "bytes32"}]}
        message {"riteId" (eth/hex->bytes rite-id-hex)
                 "creditorDidHash" (eth/keccak256 (eth/utf8 creditor-did))
                 "debtsRootHash" (eth/keccak256 (eth/utf8 (port/canonical-json debts)))}]
    (eth/eip712-digest domain types "CreditorConsent" message)))

(deftest verify-accepts-genuine-signature
  (let [owner (address-of priv)
        p (port/make-eip712-erc725-port
           chain-id verifying-contract
           (fn [did] (when (= did creditor-did) owner)))
        sig (sign (consent-digest) priv (BigInteger. "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" 16))
        payload {:rite-id rite-id-hex :creditor-did creditor-did :debts debts}]
    (is (ports/verify-eip712-signed-consent p creditor-did payload (str "0x" (eth/bytes->hex sig)))
        "genuine signature from the resolved owner verifies")))

(deftest verify-rejects-forged-signer
  (let [real-owner (address-of priv)
        forger (BigInteger. "3333333333333333333333333333333333333333333333333333333333333333" 16)
        p (port/make-eip712-erc725-port
           chain-id verifying-contract
           (fn [_] real-owner))
        sig (sign (consent-digest) forger (BigInteger. "0fedcba9876543210fedcba9876543210fedcba9876543210fedcba987654321" 16))
        payload {:rite-id rite-id-hex :creditor-did creditor-did :debts debts}]
    (is (not (ports/verify-eip712-signed-consent p creditor-did payload (str "0x" (eth/bytes->hex sig))))
        "a signature from a different key does NOT match the registered owner")))

(deftest verify-rejects-unknown-did
  (let [p (port/make-eip712-erc725-port chain-id verifying-contract (fn [_] nil))
        sig (sign (consent-digest) priv (BigInteger. "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" 16))
        payload {:rite-id rite-id-hex :creditor-did creditor-did :debts debts}]
    (is (not (ports/verify-eip712-signed-consent p "did:web:unknown" payload (str "0x" (eth/bytes->hex sig))))
        "unresolved DID → false")))

(deftest verify-rejects-tampered-debts
  (testing "changing debts after signing breaks debtsRootHash → reject"
    (let [owner (address-of priv)
          p (port/make-eip712-erc725-port
             chain-id verifying-contract
             (fn [did] (when (= did creditor-did) owner)))
          sig (sign (consent-digest) priv (BigInteger. "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" 16))
          tampered {:rite-id rite-id-hex :creditor-did creditor-did
                    :debts [{"debt_id" "d1" "principal_micro_usdc" 999999 "origination_date" "2022-01-01T00:00:00Z"}]}]
      (is (not (ports/verify-eip712-signed-consent p creditor-did tampered (str "0x" (eth/bytes->hex sig))))))))
