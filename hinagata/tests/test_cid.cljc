(ns hinagata.tests.test-cid
  "hinagata 雛形 — content-address (CIDv1 raw/sha2-256) parity tests (ADR-2606111954).
  No Python test_cid.py exists; these pin the byte-identity of the ported cid.cljc against the
  known `ipfs add --cid-version=1 --raw-leaves` vectors (verified equal to python3 methods/cid.py).

  CIDv1 raw/sha2-256: sha256 → multihash 0x12 0x20 → CIDv1 raw 0x55 → base32 'b' lowercase."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [hinagata.methods.cid :as cid]))

;; (input → [cidv1-raw  sha256-hex]) golden vectors, byte-identical to python3 / `ipfs add`.
(def vectors
  {""     ["bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku"
           "0xe3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"]
   "hello" ["bafkreibm6jg3ux5qumhcn2b3flc3tyu6dmlb4xa7u5bf44yegnrjhc4yeq"
            "0x2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"]
   "hinagata" ["bafkreigxletofjycso42drtsgdn3o7jsmnlqsy22vlnrapsfurwt6fdtf4"
               "0xd75926e2a70293b9a1c67230dbb77d32635709635aaadb103e45a46d3f14732f"]
   "雛形" ["bafkreigamiav4d6nh2r35r5plbdmm57wdt5xubozuruxhpny2ywftb4x6e"
          "0xc062015e0fcd3ea3bec7af5846c677f61cfb7a05d9a46973bdb8d62c598797f1"]})

(deftest test-cidv1-raw-and-sha256-vectors
  (testing "CIDv1 raw + sha256-hex byte-identical to python3 (incl. multibyte UTF-8 雛形)"
    (doseq [[s [cid-want sha-want]] vectors]
      (let [b (cid/utf8-bytes s)]
        (is (= cid-want (cid/cidv1-raw b)) (str "CID mismatch for " (pr-str s)))
        (is (= sha-want (cid/sha256-hex b)) (str "sha256 mismatch for " (pr-str s)))))))

(deftest test-cid-shape
  (testing "every CIDv1 raw/sha2-256 starts 'bafkrei' and sha256-hex is 0x + 64 hex chars"
    (let [b (cid/utf8-bytes "any-template-body")]
      (is (clojure.string/starts-with? (cid/cidv1-raw b) "bafkrei"))
      (is (= 66 (count (cid/sha256-hex b))))
      (is (clojure.string/starts-with? (cid/sha256-hex b) "0x")))))

(deftest test-base32-no-padding
  (testing "base32 alphabet is RFC4648 lower with no '=' padding"
    (let [b (cid/utf8-bytes "abc")]
      (is (not (clojure.string/includes? (cid/cidv1-raw b) "="))))))
