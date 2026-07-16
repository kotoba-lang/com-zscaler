#!/usr/bin/env bb
;; 撓 tawami — flexibility-ledger (content-addressed commit-DAG) tests.
;; Run:  bb --classpath 20-actors 20-actors/tawami/methods/test_kotoba.cljc
(ns tawami.methods.test-kotoba
  (:require [tawami.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private tmp "20-actors/tawami/data/test-ledger.kotoba.edn")
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))

(def d1 [[":db/add" "tawami-asset:x" ":tawami.obs/tier" ":fast-flex"]
         [":db/add" "tawami-asset:x" ":tawami/derived" true]])
(def d2 [[":db/add" "tawami-asset:y" ":tawami.obs/tier" ":slow-flex"]
         [":db/add" "tawami-asset:y" ":tawami/derived" true]])

(deftest cid-deterministic-and-prev-sensitive
  (is (= (k/tx-cid d1) (k/tx-cid d1)))
  (is (not= (k/tx-cid d1 "") (k/tx-cid d1 "bdeadbeef")))
  (is (.startsWith (k/tx-cid d1) "b")))

(deftest append-read-roundtrip-and-chain
  (clean!)
  (let [c1 (k/append-tx (k/make-tx d1 "t1" "as-of-1" (k/head-cid tmp)) tmp)
        c2 (k/append-tx (k/make-tx d2 "t2" "as-of-2" (k/head-cid tmp)) tmp)
        log (k/read-log tmp)]
    (is (= 2 (count log)))
    (is (= c2 (k/head-cid tmp)))
    (is (= c1 (get (second log) ":tx/prev")))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

(deftest verify-chain-detects-tampering
  (clean!)
  (k/append-tx (k/make-tx d1 "t1" "as-of-1" "") tmp)
  (k/append-tx (k/make-tx d2 "t2" "as-of-2" (k/head-cid tmp)) tmp)
  (spit tmp (str (k/tx->edn (assoc (k/make-tx d1 "t3" "as-of-3" "bwrongprev")
                                   ":tx/prev" "bwrongprev")) "\n") :append true)
  (let [v (k/verify-chain tmp)]
    (is (not (:ok v)))
    (is (= 2 (:broken-at v))))
  (clean!))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tawami.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
