(ns shomei.methods.test-blake2b
  "test_blake2b.cljc — BLAKE2b parity vectors vs hashlib.blake2b. ADR-2606072100.

  Pins the hand-ported `shomei.methods.blake2b` byte-for-byte against
  `python3 -c \"import hashlib; print(hashlib.blake2b(b'...', digest_size=N).hexdigest())\"`
  across empty / short / >128-byte multi-block / both shomei digest_sizes (12, 32) + the
  default 64. This is the whole ballgame — if these pass, claims.external-subject-hash /
  aggregate.did-hash are byte-identical to Python."
  (:require [clojure.test :refer [deftest is run-tests]]
            [shomei.methods.blake2b :as b2]))

(defn- hx [^String s ^long ds]
  #?(:clj (b2/hexdigest (.getBytes s "UTF-8") ds)
     :cljs (b2/hexdigest (vec (map #(.charCodeAt % 0) s)) ds)))

(defn- hx-bytes [bs ^long ds] (b2/hexdigest bs ds))

;; vectors generated with hashlib.blake2b on CPython (verbatim).
(deftest test-empty-32
  (is (= "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8"
         (hx "" 32))))

(deftest test-abc-32
  (is (= "bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319"
         (hx "abc" 32))))

(deftest test-abc-12  ;; the nonce digest_size used by analyze._mint
  (is (= "48b4f1f9042a2d5033b382b5" (hx "abc" 12))))

(deftest test-did-32  ;; aggregate.did_hash over a real seed DID
  (is (= "97ca51fb846dc53355886009163eb32e16e6dc751728380f55d40fcdd40a4b78"
         (hx "did:web:etzhayyim.com:actor:demo-aaron" 32))))

;; >128-byte multi-block: bytes 0..255 (256 bytes = 2 full blocks).
(def big256 (byte-array (map #(unchecked-byte %) (range 256))))

(deftest test-big-256-64
  (is (= (str "1ecc896f34d3f9cac484c73f75f6a5fb58ee6784be41b35f46067b9c65c63a67"
              "94d3d744112c653f73dd7deb6666204c5a9bfa5b46081fc10fdbe7884fa5cbf8")
         (hx-bytes big256 64))))

(deftest test-big-256-32
  (is (= "39a7eb9fedc19aabc83425c6755dd90e6f9d0c804964a1f4aaeea3b9fb599835"
         (hx-bytes big256 32))))

;; block-boundary cases (127 / 128 / 129 bytes of 'x').
(deftest test-x127-32
  (is (= "32afe327aece2cc8275e868be2ea929410f47bcfbac87d8197d92c972a290d3d"
         (hx (apply str (repeat 127 "x")) 32))))

(deftest test-x128-32  ;; exact one-block multiple — final-block flag must land on block 0
  (is (= "164ffb7089bae6f5a62fb0795e751dc9e88eac92e1a5b2fafe93a25abf2d9c3b"
         (hx (apply str (repeat 128 "x")) 32))))

(deftest test-x129-32
  (is (= "03b0758fa71d249c846c2304a2b9996e6ae66ffa7e5528f9e612e089a360fc8d"
         (hx (apply str (repeat 129 "x")) 32))))

(deftest test-empty-64
  (is (= (str "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419"
              "d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce")
         (hx "" 64))))

(deftest test-abc-64
  (is (= (str "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d1"
              "7d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923")
         (hx "abc" 64))))

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-blake2b)))
