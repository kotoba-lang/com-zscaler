#!/usr/bin/env bb
;; tsuchifumi 土踏み — content-addressed ledger tests (tamper-evidence).
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_kotoba.cljc
(ns tsuchifumi.methods.test-kotoba
  (:require [tsuchifumi.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def ds1 [[":db/add" "r:1" ":tsuchifumi.rel/verdict" ":relief-priority"]
          [":db/add" "r:1" ":tsuchifumi/derived" true]])
(def ds2 [[":db/add" "r:2" ":tsuchifumi.rel/verdict" ":monitor"]
          [":db/add" "r:2" ":tsuchifumi.rel/earthing-deficit" 0.42]])

(deftest tx-cid-deterministic
  (is (= (k/tx-cid ds1 "") (k/tx-cid ds1 ""))
      "same datoms + prev → same CID")
  (is (not= (k/tx-cid ds1 "") (k/tx-cid ds1 "bprev"))
      "prev-cid is part of the content address"))

(deftest append-and-verify-chain
  (let [tmp (str (System/getProperty "java.io.tmpdir") "/tsuchifumi-ledger-" (hash ds1) ".edn")]
    (io/delete-file tmp true)
    (let [t1 (k/make-tx ds1 "tx1" "as-of-1" "")
          c1 (k/append-tx t1 tmp)
          t2 (k/make-tx ds2 "tx2" "as-of-2" c1)
          _  (k/append-tx t2 tmp)
          chk (k/verify-chain tmp)]
      (is (= 2 (count (k/read-log tmp))))
      (is (true? (:ok chk)))
      (is (= 2 (:length chk)))
      (is (= c1 (get (first (k/read-log tmp)) ":tx/cid")))
      (io/delete-file tmp true))))

(deftest tamper-detected
  (let [tmp (str (System/getProperty "java.io.tmpdir") "/tsuchifumi-tamper-" (hash ds2) ".edn")]
    (io/delete-file tmp true)
    (let [t1 (k/make-tx ds1 "tx1" "a1" "")
          c1 (k/append-tx t1 tmp)
          ;; forge a tx whose prev does not match the real head
          forged (k/make-tx ds2 "tx2" "a2" "bWRONGPREV")]
      (k/append-tx forged tmp)
      (is (false? (:ok (k/verify-chain tmp))) "a broken prev-link is detected")
      (io/delete-file tmp true))))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-kotoba)]
  (when (pos? (+ fail error)) (System/exit 1)))
