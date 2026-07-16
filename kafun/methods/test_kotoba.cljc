#!/usr/bin/env bb
;; kafun 花粉 — remediation-ledger (content-addressed commit-DAG) tests.
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_kotoba.cljc
(ns kafun.methods.test-kotoba
  (:require [kafun.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private tmp
  (str (System/getProperty "java.io.tmpdir") "/kafun-test-ledger.kotoba.edn"))

(defn- fresh! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))) tmp)

(def d1 [[":db/add" "kafun-stand:x" ":kafun.rem/verdict" ":reforest-priority"]
         [":db/add" "kafun-stand:x" ":kafun/derived" true]])
(def d2 [[":db/add" "kafun-stand:y" ":kafun.rem/verdict" ":refuse"]
         [":db/add" "kafun-stand:y" ":kafun/derived" true]])

(deftest tx-cid-deterministic-and-prev-sensitive
  (is (= (k/tx-cid d1 "") (k/tx-cid d1 "")) "same datoms + prev → same CID")
  (is (not= (k/tx-cid d1 "") (k/tx-cid d1 "babc")) "prev-cid changes the CID")
  (is (not= (k/tx-cid d1 "") (k/tx-cid d2 "")) "different datoms → different CID")
  (is (.startsWith (k/tx-cid d1 "") "b") "CID is 'b' + sha256-hex"))

(deftest append-read-head-roundtrip
  (let [path (fresh!)
        c1 (k/append-tx (k/make-tx d1 "t1" "as-of-1" "") path)
        c2 (k/append-tx (k/make-tx d2 "t2" "as-of-2" c1) path)
        txs (k/read-log path)]
    (is (= 2 (count txs)))
    (is (= c2 (k/head-cid path)) "head = last tx CID")
    (is (= c1 (get (second txs) ":tx/prev")) "tx2 chains to tx1")
    (is (= d1 (get (first txs) ":tx/datoms")) "datoms round-trip through EDN")))

(deftest verify-chain-detects-tamper
  (let [path (fresh!)
        c1 (k/append-tx (k/make-tx d1 "t1" "as-of-1" "") path)]
    (k/append-tx (k/make-tx d2 "t2" "as-of-2" c1) path)
    (is (:ok (k/verify-chain path)) "intact chain verifies")
    (is (= 2 (:length (k/verify-chain path))))
    ;; tamper: append a tx with a wrong prev → chain breaks
    (spit path (str (k/tx->edn (assoc (k/make-tx d1 "t3" "as-of-3" "WRONGPREV")
                                      ":tx/prev" "WRONGPREV")) "\n") :append true)
    (is (not (:ok (k/verify-chain path))) "broken prev-cid is detected")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
