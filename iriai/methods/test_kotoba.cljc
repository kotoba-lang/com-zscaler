#!/usr/bin/env bb
;; iriai 入会 — commons-ledger (content-addressed commit-DAG) tests.
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_kotoba.cljc
(ns iriai.methods.test-kotoba
  (:require [iriai.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def ^:private tmp (str (System/getProperty "java.io.tmpdir") "/iriai-test-kotoba.kotoba.edn"))
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))

(defn- d [e a v] (k/add e a v))

;; ── tx-cid is deterministic + content-addressed (no wall clock) ────────────────
(deftest tx-cid-deterministic-and-content-addressed
  (let [ds [(d "e1" ":a/x" 1) (d "e1" ":a/y" "v")]]
    (is (= (k/tx-cid ds "") (k/tx-cid ds "")) "same datoms + prev → same CID")
    (is (not= (k/tx-cid ds "") (k/tx-cid ds "prev")) "prev-cid changes the CID (chaining)")
    (is (not= (k/tx-cid ds "") (k/tx-cid [(d "e1" ":a/x" 2)] "")) "different datoms → different CID")
    (is (clojure.string/starts-with? (k/tx-cid ds "") "b"))))

;; ── append → read-log → verify-chain is tamper-evident ─────────────────────────
(deftest append-read-verify-chain
  (clean!)
  (let [ds1 [(d "e1" ":iriai.infra/verdict" ":provision")]
        ds2 [(d "e2" ":iriai.infra/verdict" ":maintain")]
        c1 (k/append-tx (k/make-tx ds1 "t1" "as1" (k/head-cid tmp)) tmp)
        c2 (k/append-tx (k/make-tx ds2 "t2" "as2" (k/head-cid tmp)) tmp)]
    (is (= 2 (count (k/read-log tmp))))
    (is (= c2 (k/head-cid tmp)) "head is the last tx CID")
    (is (not= c1 c2))
    (let [vc (k/verify-chain tmp)]
      (is (:ok vc) "chain verifies")
      (is (= 2 (:length vc)))
      (is (= -1 (:broken-at vc))))
    (clean!)))

;; ── round-trip: tx->edn then parse-edn recovers the datoms ─────────────────────
(deftest edn-roundtrip
  (let [ds [(d "e1" ":iriai.fund/cash-to-consumer" 0)
            (d "e1" ":iriai.fund/binds-fund" false)
            (d "e1" ":iriai.fund/imputed-annual-usd" 450000.0)]
        tx (k/make-tx ds "t" "a" "")
        back (k/parse-edn (k/tx->edn tx))]
    (is (= (get tx ":tx/cid") (get back ":tx/cid")))
    (is (= 3 (get back ":tx/count")))
    (is (= 0 (get-in back [":tx/datoms" 0 3])) "cash-to-consumer 0 survives round-trip")
    (is (= false (get-in back [":tx/datoms" 1 3])) "binds-fund false survives round-trip")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
