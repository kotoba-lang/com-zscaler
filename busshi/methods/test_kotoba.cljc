#!/usr/bin/env bb
;; busshi 物資 — observation-ledger persistence tests.
;; Run:  bb --classpath 20-actors 20-actors/busshi/methods/test_kotoba.cljc
(ns busshi.methods.test-kotoba
  (:require [busshi.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/busshi-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "busshi-commodity:au" ":busshi.obs/chokepoint-risk" ":low")
              (k/add "busshi-commodity:au" ":busshi/derived" true)])
(defn- d2 [] [(k/add "busshi-commodity:ga" ":busshi.obs/chokepoint-risk" ":critical")
              (k/add "busshi-commodity:ga" ":busshi/derived" true)])

(deftest tx-cid-deterministic-and-content-sensitive
  (is (= (k/tx-cid (d1) "") (k/tx-cid (d1) "")))
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d2) "")))
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d1) "bdeadbeef")))
  (is (clojure.string/starts-with? (k/tx-cid (d1) "") "b")))

(deftest append-read-roundtrip
  (let [p (tmp)]
    (try
      (let [tx (k/make-tx (d1) "t1" "as1" "")
            cid (k/append-tx tx p)]
        (is (= cid (get tx ":tx/cid")))
        (let [txs (k/read-log p)]
          (is (= 1 (count txs)))
          (is (= (d1) (get (first txs) ":tx/datoms")))))
      (finally (io/delete-file p true)))))

(deftest chaining-and-verify
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)
            c2 (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)]
        (is (not= c1 c2))
        (is (= c2 (k/head-cid p)))
        (let [v (k/verify-chain p)] (is (:ok v)) (is (= 2 (:length v)))))
      (finally (io/delete-file p true)))))

(deftest tamper-detected
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)]
        (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)
        (spit p (clojure.string/replace (slurp p) ":critical" ":low"))
        (let [v (k/verify-chain p)]
          (is (not (:ok v)))
          (is (= 1 (:broken-at v)))))
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'busshi.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
