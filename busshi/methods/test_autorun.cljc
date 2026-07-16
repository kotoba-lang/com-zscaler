#!/usr/bin/env bb
;; busshi 物資 — heartbeat tests (analyze → persist → verify, idempotent-by-content).
;; Run:  bb --classpath 20-actors 20-actors/busshi/methods/test_autorun.cljc
(ns busshi.methods.test-autorun
  (:require [busshi.methods.busshi-edn :as be]
            [busshi.methods.autorun :as ar]
            [busshi.methods.analyze :as a]
            [busshi.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def seed "20-actors/busshi/kotoba/seed.edn")
(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/busshi-autorun-test-" (gensym) ".edn"))
(defn- commodities [] (be/commodities seed))

(deftest datoms-vector-matches-render
  (let [as (a/analyze (commodities))
        ds (a/datoms as)]
    (is (vector? ds))
    (is (every? #(= ":db/add" (first %)) ds))
    (is (some #(= ":busshi.obs/multigen-risk" (nth % 2)) ds))
    (is (not-any? #(= ":busshi/trade" (nth % 2)) ds) "G1: no trade datom persisted")
    (is (not-any? #(= ":busshi/signal" (nth % 2)) ds) "G3: no signal datom persisted")))

(deftest beat-persists-and-verifies
  (let [p (tmp)]
    (try
      (let [r (ar/beat {:commodities (commodities) :tx-id "t1" :as-of "a1" :log-path p})]
        (is (string? (:head r)))
        (is (pos? (:count r)))
        (is (true? (:appended r)))
        (is (= 26 (:commodities r)))
        (is (= 5 (:classes r)))
        (is (= 1 (count (k/read-log p))))
        (is (:ok (k/verify-chain p))))
      (finally (io/delete-file p true)))))

(deftest second-identical-beat-is-noop
  (let [p (tmp)]
    (try
      (let [r1 (ar/beat {:commodities (commodities) :tx-id "t1" :as-of "a1" :log-path p})
            r2 (ar/beat {:commodities (commodities) :tx-id "t2" :as-of "a2" :log-path p})]
        (is (true? (:appended r1)))
        (is (false? (:appended r2)) "identical beat must NOT append")
        (is (= :no-change (:reason r2)))
        (is (= (:head r1) (:head r2)))
        (is (= 1 (:length (k/verify-chain p)))))
      (finally (io/delete-file p true)))))

(deftest changed-observations-append
  (let [p (tmp)
        all (commodities)
        subset (vec (remove #(= "ga" (:id %)) all))]
    (try
      (let [r1 (ar/beat {:commodities all :tx-id "t1" :as-of "a1" :log-path p})
            r2 (ar/beat {:commodities subset :tx-id "t2" :as-of "a2" :log-path p})]
        (is (true? (:appended r1)))
        (is (true? (:appended r2)) "different commodity set → append")
        (let [v (k/verify-chain p)] (is (:ok v)) (is (= 2 (:length v)))))
      (finally (io/delete-file p true)))))

(deftest beat-deterministic
  (let [p1 (tmp) p2 (tmp)]
    (try
      (let [r1 (ar/beat {:commodities (commodities) :tx-id "t" :as-of "a" :log-path p1})
            r2 (ar/beat {:commodities (commodities) :tx-id "t" :as-of "a" :log-path p2})]
        (is (= (:head r1) (:head r2)) "deterministic: same datoms+prev → same head cid"))
      (finally (io/delete-file p1 true) (io/delete-file p2 true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'busshi.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
