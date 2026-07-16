#!/usr/bin/env bb
;; ugachi 穿ち — heartbeat tests (assess → persist → verify).
;; Run:  bb --classpath 20-actors 20-actors/ugachi/methods/test_autorun.cljc
(ns ugachi.methods.test-autorun
  (:require [ugachi.methods.ugachi-edn :as ue]
            [ugachi.methods.autorun :as ar]
            [ugachi.methods.kotoba :as k]
            [ugachi.methods.gate :as gate]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def ug-seed "20-actors/ugachi/kotoba/seed.edn")
(def bu-seed "20-actors/busshi/kotoba/seed.edn")
(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/ugachi-autorun-test-" (gensym) ".edn"))
(defn- projects [] (ue/projects ug-seed))
(defn- commodities []
  (vec (filter #(= (:type %) :commodity) (ue/normalize-rows (clojure.edn/read-string (slurp bu-seed))))))

(deftest gate-datoms-vector-matches-render
  ;; gate/datoms returns the vectors render-datoms stringifies
  (let [a (gate/assess (projects))
        ds (gate/datoms a)]
    (is (vector? ds))
    (is (every? #(= ":db/add" (first %)) ds))
    (is (some #(= ":ugachi.gate/verdict" (nth % 2)) ds))
    (is (not-any? #(= ":ugachi/actuate" (nth % 2)) ds) "G1: no actuation datom persisted")))

(deftest beat-persists-and-verifies
  (let [p (tmp)]
    (try
      (let [r (ar/beat {:projects (projects) :tx-id "t1" :as-of "a1" :log-path p})]
        (is (string? (:head r)))
        (is (pos? (:count r)))
        (is (true? (:appended r)) "first beat appends")
        (is (false? (:grounded r)) "no commodities → ungrounded")
        (is (= 1 (count (k/read-log p))))
        (is (:ok (k/verify-chain p))))
      (finally (io/delete-file p true)))))

(deftest second-identical-beat-is-noop
  ;; idempotent-by-content: same verdicts → no append (the chain records CHANGES)
  (let [p (tmp)]
    (try
      (let [r1 (ar/beat {:projects (projects) :tx-id "t1" :as-of "a1" :log-path p})
            r2 (ar/beat {:projects (projects) :tx-id "t2" :as-of "a2" :log-path p})]
        (is (true? (:appended r1)))
        (is (false? (:appended r2)) "identical beat must NOT append")
        (is (= :no-change (:reason r2)))
        (is (= (:head r1) (:head r2)) "head unchanged on no-op")
        (is (= 1 (:length (k/verify-chain p))) "chain stays length 1"))
      (finally (io/delete-file p true)))))

(deftest changed-assessment-appends
  ;; when the verdicts actually change, the next beat DOES append (length 2)
  (let [p (tmp)
        all (projects)
        subset (vec (remove #(= "deepsea-nodule-d" (:id %)) all))]
    (try
      (let [r1 (ar/beat {:projects all :tx-id "t1" :as-of "a1" :log-path p})
            r2 (ar/beat {:projects subset :tx-id "t2" :as-of "a2" :log-path p})]
        (is (true? (:appended r1)))
        (is (true? (:appended r2)) "different verdict set → append")
        (let [v (k/verify-chain p)]
          (is (:ok v))
          (is (= 2 (:length v)))))
      (finally (io/delete-file p true)))))

(deftest grounded-beat-uses-bridge
  (let [p (tmp)]
    (try
      (let [r (ar/beat {:projects (projects) :commodities (commodities)
                        :tx-id "t1" :as-of "a1" :log-path p})]
        (is (true? (:grounded r)) "commodities supplied → grounded via bridge")
        (is (:ok (k/verify-chain p))))
      (finally (io/delete-file p true)))))

(deftest beat-deterministic-resume-safe
  ;; same inputs against an empty head → identical head cid across two fresh logs
  (let [p1 (tmp) p2 (tmp)]
    (try
      (let [r1 (ar/beat {:projects (projects) :tx-id "t" :as-of "a" :log-path p1})
            r2 (ar/beat {:projects (projects) :tx-id "t" :as-of "a" :log-path p2})]
        (is (= (:head r1) (:head r2)) "deterministic: same datoms+prev → same head cid"))
      (finally (io/delete-file p1 true) (io/delete-file p2 true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'ugachi.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
