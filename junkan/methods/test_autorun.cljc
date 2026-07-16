#!/usr/bin/env bb
;; junkan 循環 — heartbeat tests (idempotent-by-content, resume-safe).
;; Run:  bb --classpath 20-actors 20-actors/junkan/methods/test_autorun.cljc
(ns junkan.methods.test-autorun
  (:require [junkan.methods.junkan-edn :as je]
            [junkan.methods.autorun :as ar]
            [junkan.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
(defn- is* [] (je/instruments seed-path))
(defn- tmp [] (str (System/getProperty "java.io.tmpdir")
                   "/junkan-autorun-" (hash (str (gensym))) ".edn"))

(deftest beat-appends-then-idempotent
  (let [p (tmp) insts (is*)]
    (try
      (let [r1 (ar/beat {:instruments insts :tx-id "b1" :as-of "a1" :log-path p})
            r2 (ar/beat {:instruments insts :tx-id "b2" :as-of "a2" :log-path p})]
        (is (:appended r1) "first beat appends")
        (is (pos? (:count r1)) "emits findings datoms")
        (is (= (:instruments r1) (count insts)))
        (is (not (:appended r2)) "second beat over identical seed is a NO-OP")
        (is (= :no-change (:reason r2)))
        (is (= (:head r1) (:head r2)) "head unchanged on no-op")
        (is (= 1 (count (k/read-log p))) "only one tx on the chain"))
      (finally (io/delete-file p true)))))

(deftest beat-appends-on-change
  (let [p (tmp) insts (is*)]
    (try
      (ar/beat {:instruments insts :tx-id "b1" :as-of "a1" :log-path p})
      ;; add a new instrument → findings change → a new tx must append
      (let [is2 (conj insts {:type :instrument :id "test-new-x" :name "Test instrument"
                          :jurisdiction "ZZ" :kind :law :year 2026
                          :enactor "Test body" :origin "Test origin" :stakeholders ["x"]
                          :stock :information-asymmetry :polarity :widen :magnitude 0.5
                          :reversibility :statutory :meadows 5 :basis "test" :sourcing :synthetic
                          :confidence 0.5})
            r (ar/beat {:instruments is2 :tx-id "b2" :as-of "a2" :log-path p})]
        (is (:appended r) "a changed finding set appends a new tx")
        (is (= 2 (count (k/read-log p)))))
      (finally (io/delete-file p true)))))

(deftest beat-regimes-reported
  (let [p (tmp)]
    (try
      (let [r (ar/beat {:instruments (is*) :tx-id "b1" :as-of "a1" :log-path p})]
        (is (map? (:regimes r)))
        (is (= 5 (count (:regimes r))) "regime reported for all five stocks")
        (is (:ok (k/verify-chain p)) "ledger verifies after a beat"))
      (finally (io/delete-file p true)))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-autorun)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
