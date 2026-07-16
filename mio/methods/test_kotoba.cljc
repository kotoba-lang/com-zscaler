#!/usr/bin/env bb
;; 澪 mio — verification-ledger (content-addressed commit-DAG) tests.
;; Run:  bb --classpath 20-actors 20-actors/mio/methods/test_kotoba.cljc
(ns mio.methods.test-kotoba
  (:require [mio.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private tmp "20-actors/mio/data/test-ledger.kotoba.edn")

(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))

(def d1 [[":db/add" "mio-claim:x" ":mio.obs/verdict" ":verified"]
         [":db/add" "mio-claim:x" ":mio/derived" true]])
(def d2 [[":db/add" "mio-claim:y" ":mio.obs/verdict" ":rejected-leakage"]
         [":db/add" "mio-claim:y" ":mio/derived" true]])

(deftest cid-is-deterministic-and-prev-sensitive
  (is (= (k/tx-cid d1) (k/tx-cid d1)) "same datoms → same cid")
  (is (not= (k/tx-cid d1 "") (k/tx-cid d1 "bdeadbeef")) "prev changes the cid")
  (is (.startsWith (k/tx-cid d1) "b")))

(deftest append-read-roundtrip-and-chain
  (clean!)
  (let [c1 (k/append-tx (k/make-tx d1 "t1" "as-of-1" (k/head-cid tmp)) tmp)
        c2 (k/append-tx (k/make-tx d2 "t2" "as-of-2" (k/head-cid tmp)) tmp)
        log (k/read-log tmp)]
    (is (= 2 (count log)))
    (is (= c2 (k/head-cid tmp)) "head is the last tx cid")
    (is (= "" (get (first log) ":tx/prev")) "first tx has empty prev")
    (is (= c1 (get (second log) ":tx/prev")) "second tx chains to the first")
    (is (:ok (k/verify-chain tmp)) "intact chain verifies")
    (is (= 2 (:length (k/verify-chain tmp))))
    (clean!)))

(deftest verify-chain-detects-tampering
  (clean!)
  (k/append-tx (k/make-tx d1 "t1" "as-of-1" "") tmp)
  (k/append-tx (k/make-tx d2 "t2" "as-of-2" (k/head-cid tmp)) tmp)
  ;; corrupt the file: append a forged line whose prev does not match the head
  (spit tmp (str (k/tx->edn (assoc (k/make-tx d1 "t3" "as-of-3" "bwrongprev")
                                   ":tx/prev" "bwrongprev")) "\n") :append true)
  (let [v (k/verify-chain tmp)]
    (is (not (:ok v)) "tampered chain fails")
    (is (= 2 (:broken-at v)) "break detected at the forged tx"))
  (clean!))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'mio.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
