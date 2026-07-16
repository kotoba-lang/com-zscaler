#!/usr/bin/env bb
;; ugachi 穿ち — stewardship-ledger persistence tests.
;; Run:  bb --classpath 20-actors 20-actors/ugachi/methods/test_kotoba.cljc
(ns ugachi.methods.test-kotoba
  (:require [ugachi.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/ugachi-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "ugachi-project:x" ":ugachi.gate/verdict" ":refuse")
              (k/add "ugachi-project:x" ":ugachi/derived" true)])
(defn- d2 [] [(k/add "ugachi-project:y" ":ugachi.gate/verdict" ":propose-r0")
              (k/add "ugachi-project:y" ":ugachi/derived" true)])

(deftest tx-cid-deterministic-and-content-sensitive
  (is (= (k/tx-cid (d1) "") (k/tx-cid (d1) "")) "same input → same cid")
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d2) "")) "different datoms → different cid")
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d1) "bdeadbeef")) "different prev → different cid")
  (is (clojure.string/starts-with? (k/tx-cid (d1) "") "b")))

(deftest append-read-roundtrip
  (let [p (tmp)]
    (try
      (let [tx (k/make-tx (d1) "t1" "as1" "")
            cid (k/append-tx tx p)]
        (is (= cid (get tx ":tx/cid")))
        (let [txs (k/read-log p)]
          (is (= 1 (count txs)))
          (is (= cid (get (first txs) ":tx/cid")))
          (is (= (d1) (get (first txs) ":tx/datoms")) "datoms round-trip byte-faithfully")))
      (finally (io/delete-file p true)))))

(deftest chaining-and-verify
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)
            c2 (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)]
        (is (not= c1 c2) "two distinct txs")
        (is (= c2 (k/head-cid p)) "head = last tx cid")
        (let [v (k/verify-chain p)]
          (is (:ok v))
          (is (= 2 (:length v)))
          (is (= -1 (:broken-at v)))))
      (finally (io/delete-file p true)))))

(deftest tamper-detected
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)]
        (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)
        ;; corrupt the 2nd tx's datoms in place (cid no longer matches)
        (let [corrupted (clojure.string/replace (slurp p) ":propose-r0" ":refuse")]
          (spit p corrupted)
          (let [v (k/verify-chain p)]
            (is (not (:ok v)) "tamper must break the chain")
            (is (= 1 (:broken-at v)) "2nd tx (index 1) is the break"))))
      (finally (io/delete-file p true)))))

(deftest resume-safe-deterministic
  ;; same datoms + same prev → identical cid across runs (no wall clock)
  (let [prev "bcafef00d"]
    (is (= (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")
           (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'ugachi.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
