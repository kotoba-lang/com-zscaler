#!/usr/bin/env bb
;; uzu 渦 — information-log (content-addressed commit-DAG) tests.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_kotoba.cljc
(ns uzu.methods.test-kotoba
  (:require [uzu.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def ds1 [[":db/add" "uzu:organism/a" ":uzu.organism/alive" true]
          [":db/add" "uzu:organism/a" ":uzu.organism/final-energy" 6.6]])
(def ds2 [[":db/add" "uzu:organism/b" ":uzu.organism/alive" false]])

(defn tmp [] (str (System/getProperty "java.io.tmpdir") "/uzu-test-" (gensym) ".kotoba.edn"))

(deftest cid-deterministic-and-chained
  (is (= (k/tx-cid ds1 "") (k/tx-cid ds1 "")) "same datoms+prev ⇒ same CID")
  (is (not= (k/tx-cid ds1 "") (k/tx-cid ds1 "bXYZ")) "prev-cid chains into the CID")
  (is (.startsWith (k/tx-cid ds1 "") "b") "multibase-ish 'b' prefix"))

(deftest append-read-roundtrip
  (let [p (tmp)
        c1 (k/append-tx (k/make-tx ds1 "t1" "as-of-1" (k/head-cid p)) p)
        c2 (k/append-tx (k/make-tx ds2 "t2" "as-of-2" (k/head-cid p)) p)
        txs (k/read-log p)]
    (is (= 2 (count txs)))
    (is (= c2 (k/head-cid p)) "head is the last tx")
    (is (= "" (get (first txs) ":tx/prev")) "genesis tx has empty prev")
    (is (= c1 (get (second txs) ":tx/prev")) "second tx chains to the first's CID via prev")
    (is (= ds1 (get (first txs) ":tx/datoms")) "datoms survive the EDN roundtrip")
    (.delete (io/file p))))

(deftest verify-chain-detects-tamper
  (let [p (tmp)]
    (k/append-tx (k/make-tx ds1 "t1" "a1" "") p)
    (k/append-tx (k/make-tx ds2 "t2" "a2" (k/head-cid p)) p)
    (is (:ok (k/verify-chain p)) "a well-formed chain verifies")
    ;; corrupt the file: flip a value
    (spit p (clojure.string/replace (slurp p) "6.6" "9.9"))
    (is (false? (:ok (k/verify-chain p))) "a tampered datom breaks the content-address")
    (.delete (io/file p))))

(deftest empty-log-is-clean
  (let [p (tmp)]
    (is (= "" (k/head-cid p)))
    (is (:ok (k/verify-chain p)))
    (is (= [] (k/read-log p)))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-kotoba)]
  (when (pos? (+ fail error)) (System/exit 1)))
