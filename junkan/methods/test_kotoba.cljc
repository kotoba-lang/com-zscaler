#!/usr/bin/env bb
;; junkan 循環 — findings-ledger (content-addressed commit-DAG) tests.
;; Run:  bb --classpath 20-actors 20-actors/junkan/methods/test_kotoba.cljc
(ns junkan.methods.test-kotoba
  (:require [junkan.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir")
                   "/junkan-ledger-" (hash (str (gensym))) ".edn"))

(deftest cid-deterministic
  (let [ds [[":db/add" "e1" ":a" "v1"] [":db/add" "e1" ":b" 2]]]
    (is (= (k/tx-cid ds "") (k/tx-cid ds "")) "same datoms+prev → same CID")
    (is (not= (k/tx-cid ds "") (k/tx-cid ds "prev")) "prev changes the CID")
    (is (.startsWith (k/tx-cid ds "") "b") "CID is 'b'+hex")))

(deftest append-and-verify-chain
  (let [p (tmp)]
    (try
      (let [d1 [[":db/add" "i:a" ":junkan/derived" true]]
            d2 [[":db/add" "i:b" ":junkan/derived" true]]
            c1 (k/append-tx (k/make-tx d1 "t1" "as1" (k/head-cid p)) p)
            c2 (k/append-tx (k/make-tx d2 "t2" "as2" (k/head-cid p)) p)]
        (is (= c2 (k/head-cid p)) "head is the latest CID")
        (is (= 2 (count (k/read-log p))))
        (let [v (k/verify-chain p)]
          (is (:ok v) "chain verifies")
          (is (= 2 (:length v)))))
      (finally (io/delete-file p true)))))

(deftest tamper-evident
  (let [p (tmp)]
    (try
      (k/append-tx (k/make-tx [[":db/add" "i:a" ":x" 1]] "t1" "a1" "") p)
      ;; corrupt by appending a tx whose prev is wrong
      (spit p (str (k/tx->edn {":tx/id" "bad" ":tx/as-of" "a2" ":tx/prev" "WRONG"
                               ":tx/cid" "bdeadbeef" ":tx/count" 1
                               ":tx/datoms" [[":db/add" "i:b" ":x" 2]]}) "\n") :append true)
      (let [v (k/verify-chain p)]
        (is (false? (:ok v)) "tampered chain fails verification")
        (is (= 1 (:broken-at v)) "break located at index 1"))
      (finally (io/delete-file p true)))))

(deftest roundtrip-string-and-keyword-values
  (let [p (tmp)]
    (try
      (let [ds [[":db/add" "i:a" ":junkan.gov.instr/polarity" ":widen"]
                [":db/add" "i:a" ":junkan.gov.instr/year" 1917]
                [":db/add" "i:a" ":junkan.gov.instr/contribution" 0.36]]
            _ (k/append-tx (k/make-tx ds "t1" "a1" "") p)
            back (get (first (k/read-log p)) ":tx/datoms")]
        (is (= ds back) "datoms round-trip through EDN log byte-faithfully"))
      (finally (io/delete-file p true)))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-kotoba)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
