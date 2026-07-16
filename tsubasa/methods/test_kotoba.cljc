#!/usr/bin/env bb
;; tsubasa 翼 — fare-observation-ledger persistence tests.
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_kotoba.cljc
(ns tsubasa.methods.test-kotoba
  (:require [tsubasa.methods.kotoba :as k]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/tsubasa-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "tsubasa-route:JFK-NRT" ":tsubasa.obs/concentration" ":competitive")
              (k/add "tsubasa-route:JFK-NRT" ":tsubasa/derived" true)])
(defn- d2 [] [(k/add "tsubasa-route:JNB-LHR" ":tsubasa.obs/concentration" ":monopoly")
              (k/add "tsubasa-route:JNB-LHR" ":tsubasa/derived" true)])

(deftest tx-cid-deterministic-and-content-sensitive
  (is (= (k/tx-cid (d1) "") (k/tx-cid (d1) "")))
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d2) "")))
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d1) "bdeadbeef")))
  (is (str/starts-with? (k/tx-cid (d1) "") "b")))

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
        (spit p (str/replace (slurp p) ":monopoly" ":competitive"))
        (let [v (k/verify-chain p)]
          (is (not (:ok v)))
          (is (= 1 (:broken-at v)))))
      (finally (io/delete-file p true)))))

(deftest roundtrip-preserves-floats-and-bools
  ;; co2-kg + derived flag survive the self-contained EDN reader/writer intact
  (let [p (tmp)
        ds [(k/add "tsubasa-route:LHR-CDG" ":tsubasa.obs/greenest-co2-kg" 105.0)
            (k/add "tsubasa-route:LHR-CDG" ":tsubasa/derived" true)]]
    (try
      (k/append-tx (k/make-tx ds "t1" "as1" "") p)
      (let [back (get (first (k/read-log p)) ":tx/datoms")]
        (is (= 105.0 (nth (first back) 3)))
        (is (true? (nth (second back) 3))))
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
