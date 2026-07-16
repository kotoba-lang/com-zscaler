#!/usr/bin/env bb
;; atsurae 誂え — ledger + heartbeat tests (content-addressed, idempotent-by-content).
;; Run:  bb --classpath 20-actors 20-actors/atsurae/methods/test_autorun.cljc
(ns atsurae.methods.test-autorun
  (:require [atsurae.methods.feature-model :as fm]
            [atsurae.methods.autorun :as a]
            [atsurae.methods.kotoba :as k]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])))

(def seed-path "20-actors/atsurae/kotoba/seed.edn")
(defn- model [] (fm/classify (fm/load-edn seed-path)))

#?(:clj
   (defn- tmp-log []
     (str (io/file (System/getProperty "java.io.tmpdir")
                   (str "atsurae-test-" (System/nanoTime) ".kotoba.edn")))))

(deftest content-addressed-append-and-verify
  (testing "one beat appends a content-addressed tx; verify-chain holds"
    (let [log (tmp-log)
          r (a/beat {:model (model) :tx-id "t1" :as-of "a1" :log-path log})]
      (is (:appended r))
      (is (str/starts-with? (:head r) "b"))
      (is (= 176 (:n-variants r)))
      (is (:ok (k/verify-chain log)))
      (io/delete-file log true))))

(deftest idempotent-by-content
  (testing "a second beat with identical datoms is a NO-OP"
    (let [log (tmp-log)
          r1 (a/beat {:model (model) :tx-id "t1" :as-of "a1" :log-path log})
          r2 (a/beat {:model (model) :tx-id "t2" :as-of "a2" :log-path log})]
      (is (:appended r1))
      (is (not (:appended r2)))
      (is (= :no-change (:reason r2)))
      (is (= 1 (:length (k/verify-chain log))))
      (io/delete-file log true))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'atsurae.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
