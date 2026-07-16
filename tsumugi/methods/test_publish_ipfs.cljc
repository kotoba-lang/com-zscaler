(ns tsumugi.methods.test-publish-ipfs
  "Cross-language oracle tests for tsumugi.methods.publish-ipfs — IPFS-pin persistence.
  Ported from the REAL Python tests/test_publish_ipfs.py.

  The cross-language ANCHOR is byte-exact: cidv1-raw(b\"hello\") == the known `ipfs add
  --cid-version=1 --raw-leaves` vector. pin/verify run against a TEMP dir (so no tracked
  80-data / apex artifact is touched); the gz-artifact CIDs are checked structurally (valid
  single-block CIDv1 bafkrei…, <256KiB, round-trip) — not byte-equal to Python's, since the
  java gzip encoder differs from Python's zlib (documented in the ns)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [tsumugi.methods.publish-ipfs :as P]
            [tsumugi.methods.publish :as publish]))

(defn- tmp-dir []
  (let [f (java.io.File/createTempFile "tsumugi-pub-test" "")] (.delete f) (.mkdirs f) (.getAbsolutePath f)))
(defn- b [s] (.getBytes (str s) "UTF-8"))

(deftest cidv1-raw-matches-ipfs-add-vector
  (is (= "bafkreibm6jg3ux5qumhcn2b3flc3tyu6dmlb4xa7u5bf44yegnrjhc4yeq" (P/cidv1-raw (b "hello")))))

(deftest gzip-is-deterministic
  (is (java.util.Arrays/equals (P/gz* (b (apply str (repeat 1000 "x"))))
                               (P/gz* (b (apply str (repeat 1000 "x")))))))

(deftest gz-roundtrips
  (is (= "hello 日本語" (String. (P/gunzip* (P/gz* (b "hello 日本語"))) "UTF-8"))))

(deftest pin-content-addresses-three-single-block-artifacts
  (let [data (tmp-dir) apex (tmp-dir)
        pm (P/pin :data-dir data :apex-dir apex)
        a (get pm "artifacts")]
    (is (= 3 (count a)))
    (is (every? #(clojure.string/starts-with? (get % "cid") "bafkrei") (vals a)))
    (is (every? #(< (get % "bytes") (* 256 1024)) (vals a)))
    ;; the gz on disk decompresses byte-identical to the seed
    (let [seed (.getBytes (slurp "20-actors/tsumugi/data/seed-scale-power.kotoba.edn") "UTF-8")
          got (P/gunzip* (with-open [in (io/input-stream (str data "/" (get-in a ["graph" "file"])))
                                     bos (java.io.ByteArrayOutputStream.)]
                           (io/copy in bos) (.toByteArray bos)))]
      (is (java.util.Arrays/equals seed got)))))

(deftest verify-roundtrips
  (let [data (tmp-dir) apex (tmp-dir)]
    (P/pin :data-dir data :apex-dir apex)
    (is (= 0 (P/verify :data-dir data)))))

(deftest manifest-and-descriptors-valid
  (let [data (tmp-dir) apex (tmp-dir)
        pm (P/pin :data-dir data :apex-dir apex)]
    (is (clojure.string/includes? (get pm "license") "Charter"))
    (is (= publish/PUBLISHER-DID (get pm "publisher")))
    (is (clojure.string/starts-with? (get pm "contentHash_ntriples") "sha256:"))
    (is (>= (count (get pm "gateways")) 2))
    ;; /ns/power vocabulary
    (let [vocab (json/parse-string (slurp (str apex "/ns/power")))]
      (is (= publish/NS (get-in vocab ["@context" "@vocab"])))
      (is (some #(clojure.string/includes? % "concentration") (keys (get vocab "terms")))))
    ;; /dataset descriptor carries CIDs + fetch links
    (let [dsd (json/parse-string (slurp (str apex "/dataset/tsumugi-power.json")))]
      (is (clojure.string/starts-with? (get-in dsd ["artifacts" "graph" "cid"]) "bafkrei"))
      (is (clojure.string/ends-with? (first (get-in dsd ["fetch" "graph"]))
                                     (get-in dsd ["artifacts" "graph" "cid"]))))))
