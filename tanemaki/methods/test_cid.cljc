#!/usr/bin/env bb
;; tanemaki 種蒔き — CIDv1 + scorecardSha256 parity test (gold standards: `ipfs add` / `shasum`).
;; Run:  bb --classpath 20-actors 20-actors/tanemaki/methods/test_cid.cljc
(ns tanemaki.methods.test-cid
  "Pins tanemaki.methods.cid byte-for-byte against gold standards. tanemaki's grant proposals carry
  a content-addressed DD-scorecard CID (voters verify the scorecard BYTES the GrantGovernor decides
  on — the scorecard is advisory, the CID is the integrity anchor) PLUS a defense-in-depth
  scorecardSha256. Both must match the canonical tools:
    cidv1-raw    ≡  printf '%s' INPUT | ipfs add -Q --cid-version=1 --raw-leaves --only-hash
    sha256-hex   ≡  '0x' + shasum -a 256
  (verified against /opt/homebrew/bin/ipfs + shasum at authoring time). The module had no dedicated
  test (only exercised incidentally by test_propose). A drift in either silently breaks a voter's
  ability to verify a grant scorecard."
  (:require [tanemaki.methods.cid :as cid]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(defn- cid-of [s] (cid/cidv1-raw (cid/utf8-bytes s)))
(defn- sha-of [s] (cid/sha256-hex (cid/utf8-bytes s)))

(deftest cidv1-raw-matches-ipfs-add
  (is (= "bafkreibm6jg3ux5qumhcn2b3flc3tyu6dmlb4xa7u5bf44yegnrjhc4yeq" (cid-of "hello")))
  (is (= "bafkreihipbmlvwyvj6x4af6yt6lxz5zo7s6dpcx43rdfmba2qihkpsgpta" (cid-of "etzhayyim")))
  (is (= "bafkreidjlndn57rjr4ayvztlfim5lmcq7xsiompa57sz5ihpggjvlo3eva" (cid-of "rasen 螺旋 genome"))
      "UTF-8 multibyte content hashes the encoded bytes")
  (is (= "bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku" (cid-of "")) "empty raw block")
  (is (= "bafkreickfmuzn77mc5ypk54x3xjuvgvfzel5f3lle5v364alj7tmix33nm" (cid-of "tanemaki grant scorecard"))
      "a grant-scorecard artifact"))

(deftest sha256-hex-matches-shasum
  ;; the scorecardSha256 defense-in-depth hash voters cross-check (0x-prefixed lowercase hex)
  (is (= "0x2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" (sha-of "hello")))
  (is (= "0xe87858badb154fafc017d89f977cf72efcbc378afcdc4656041a820ea7c8cf98" (sha-of "etzhayyim")))
  (is (= "0x4a2b2996ffec1770f57797ddd34a9aa5c917d2ed6b276bbf700b4fe6c45f7b6b" (sha-of "tanemaki grant scorecard"))))

(deftest utf8-bytes-encodes-multibyte
  ;; 螺旋 → 6 UTF-8 bytes (3 each), unsigned 0..255 — the hash sees the ENCODED bytes, not codepoints
  (let [bs (cid/utf8-bytes "螺旋")]
    (is (= 6 (count bs)))
    (is (every? #(<= 0 % 255) bs))
    (is (= (map #(bit-and % 0xff) (.getBytes "螺旋" "UTF-8")) (seq bs)))))

(deftest cid-has-the-cidv1-raw-sha256-shape
  (let [c (cid-of "hello")]
    (is (str/starts-with? c "bafkrei"))
    (is (= 59 (count c)) "a 32-byte raw-leaf CIDv1 is exactly 59 base32 chars")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tanemaki.methods.test-cid)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
