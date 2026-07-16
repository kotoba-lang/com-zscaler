(ns jinushi.methods.test-cid
  "jinushi 地主 — CIDv1 content-addressing tests (R1)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.cid :as cid]))

;; computed at LOAD time (when *file* is bound), like the other test namespaces
(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def np-snapshot (io/file repo-root "80-data" "jinushi-land" "wikidata-national-parks.kotoba.edn"))

(deftest test-known-empty-vector
  ;; the canonical CIDv1 (raw/sha2-256) of zero bytes — verifies the multihash + base32 wiring
  ;; is byte-exact (this is the value `ipfs add --raw-leaves` yields for an empty raw block).
  (is (= "bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku"
         (cid/string->cidv1 ""))))

(deftest test-raw-prefix
  ;; every CIDv1 raw/sha2-256 renders with the bafkrei… prefix.
  (is (str/starts-with? (cid/string->cidv1 "jinushi") "bafkrei"))
  (is (str/starts-with? (cid/string->cidv1 "地主") "bafkrei")))

(deftest test-deterministic-and-sensitive
  (is (= (cid/string->cidv1 "same bytes") (cid/string->cidv1 "same bytes")) "same bytes → same CID")
  (is (not= (cid/string->cidv1 "a") (cid/string->cidv1 "b")) "different bytes → different CID"))

(deftest test-snapshot-cid
  (is (.exists np-snapshot) "national-park snapshot exists")
  (is (str/starts-with? (cid/file->cidv1 np-snapshot) "bafkrei") "snapshot content-addresses to a CIDv1")
  (is (= (cid/file->cidv1 np-snapshot) (cid/file->cidv1 np-snapshot)) "file CID is stable"))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-cid)]
    (System/exit (+ (or fail 0) (or error 0)))))
