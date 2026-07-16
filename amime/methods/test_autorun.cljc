#!/usr/bin/env bb
;; amime 網目 — ledger + heartbeat tests (content-addressed, idempotent-by-content).
;; Run:  bb --classpath 20-actors 20-actors/amime/methods/test_autorun.cljc
(ns amime.methods.test-autorun
  (:require [amime.methods.mesh :as m]
            [amime.methods.autorun :as a]
            [amime.methods.kotoba :as k]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])))

(def seed-path "20-actors/amime/kotoba/seed.edn")
(defn- seed [] (m/classify (m/load-edn seed-path)))

#?(:clj
   (defn- tmp-log []
     (str (io/file (System/getProperty "java.io.tmpdir")
                   (str "amime-test-" (System/nanoTime) ".kotoba.edn")))))

(deftest content-addressed-append-and-verify
  (testing "one beat appends a content-addressed tx; verify-chain holds"
    (let [log (tmp-log)
          r (a/beat {:seed (seed) :tx-id "t1" :as-of "a1" :log-path log})]
      (is (:appended r))
      (is (str/starts-with? (:head r) "b"))
      (is (:ok (k/verify-chain log)))
      (is (= 1 (:length (k/verify-chain log))))
      (io/delete-file log true))))

(deftest idempotent-by-content
  (testing "a second beat with identical datoms is a NO-OP (records changes, not ticks)"
    (let [log (tmp-log)
          r1 (a/beat {:seed (seed) :tx-id "t1" :as-of "a1" :log-path log})
          r2 (a/beat {:seed (seed) :tx-id "t2" :as-of "a2" :log-path log})]
      (is (:appended r1))
      (is (not (:appended r2)))
      (is (= :no-change (:reason r2)))
      (is (= (:head r1) (:head r2)) "head unchanged")
      (is (= 1 (:length (k/verify-chain log))) "only one tx on the chain")
      (io/delete-file log true))))

(deftest beat-surfaces-critical-link
  (testing "the heartbeat reports the N-1 critical chokepoint"
    (let [log (tmp-log)
          r (a/beat {:seed (seed) :tx-id "t1" :as-of "a1" :log-path log})]
      (is (= "l-wb-ct" (:critical-link r)))
      (io/delete-file log true))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'amime.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
