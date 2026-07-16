(ns yobel.concrete-ports.tests.test-web3-ports
  "Offline tests for the web3 registry adapters: ABI codec correctness (known
  Ethereum 4-byte selectors + word encode/decode round trips) and the pure
  rites-struct → Rite decode. A LIVE EVM roundtrip stays in the Python
  tests_integration harness (anvil), which is out of scope for this bb pass."
  (:require [clojure.test :refer [deftest is testing]]
            [yobel.concrete-ports.web3-rpc :as rpc]
            [yobel.concrete-ports.web3-rite-registry :as rite]))

(deftest abi-selectors-known
  (testing "4-byte selectors match canonical Ethereum values"
    (is (= "a9059cbb" (rpc/function-selector "transfer(address,uint256)")))
    (is (= "70a08231" (rpc/function-selector "balanceOf(address)")))
    (is (= "18160ddd" (rpc/function-selector "totalSupply()")))))

(deftest abi-word-encode-decode
  (testing "uint round trips through 32-byte words"
    (is (= 64 (count (rpc/enc-uint 0))))
    (is (= 64 (count (rpc/enc-uint 250000000))))
    (is (= (biginteger 250000000) (rpc/dec-uint (rpc/enc-uint 250000000))))
    (is (= (biginteger 0) (rpc/dec-uint (rpc/enc-uint 0)))))
  (testing "bytes32 + address widths"
    (is (= 64 (count (rpc/enc-bytes32 "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"))))
    (is (= 64 (count (rpc/enc-address "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))))
    (is (= "0xcccccccccccccccccccccccccccccccccccccccc"
           (rpc/dec-address-hex (rpc/enc-address "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))))))

(deftest words-splits-blob
  (let [blob (str "0x" (rpc/enc-uint 1) (rpc/enc-uint 2) (rpc/enc-uint 3))]
    (is (= 3 (count (rpc/words blob))))
    (is (= [1 2 3] (map #(.intValue (rpc/dec-uint %)) (rpc/words blob))))))

(defn- b32-of [hex64] hex64)

;; A synthetic `rites(bytes32)` return: 15 words matching the Solidity struct.
(def ^:private rite-words
  (let [zero (apply str (repeat 64 \0))
        u (fn [n] (rpc/enc-uint n))
        b (fn [h] (rpc/enc-bytes32 h))]
    [(b "0xaa11000000000000000000000000000000000000000000000000000000000000")  ; 0 riteId
     (u 0)                                                                      ; 1 riteType=shmita_7yr
     (b "0xbb22000000000000000000000000000000000000000000000000000000000000")  ; 2 doctrinalBasisHash
     (b "0xcc33000000000000000000000000000000000000000000000000000000000000")  ; 3 scopeHash
     (u 1758551400)                                                            ; 4 effectiveDate
     (u 1789142400)                                                            ; 5 expiryDate
     (rpc/enc-address "0x00000000000000000000000000000000000000aa")            ; 6 declarer
     (b "0xdd44000000000000000000000000000000000000000000000000000000000000")  ; 7 issuerDidHash
     (u 2)                                                                      ; 8 status=active
     zero (u 4) (u 0) (u 0) (u 0) zero]))                                       ; 9..14

(deftest rite-from-words-decode
  (let [r (rite/rite-from-words rite-words)]
    (is (= "shmita_7yr" (:rite-type r)))
    (is (= "active" (:status r)))
    (is (= [] (:scope-jurisdictions r)))
    (is (.startsWith (:effective-date r) "2025-"))     ; 1758551400 = 2025-09-22
    (is (.startsWith (:scope r) "sha256:cc33"))
    (is (.startsWith (:issuer-did r) "hash:dd44"))
    (is (.startsWith (:doctrinal-basis r) "sha256:bb22"))
    (is (string? (:expiry-date r)))))

(deftest rite-from-words-uninitialized
  (let [zero-status (assoc rite-words 8 (rpc/enc-uint 0))]
    (is (nil? (rite/rite-from-words zero-status))
        "Status.Unknown(0) → nil (uninitialized rite)")))
