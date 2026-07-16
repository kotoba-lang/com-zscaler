#!/usr/bin/env bb
;; 委 yudane — heartbeat (idempotent-by-content) tests.
;; Run:  bb --classpath 20-actors 20-actors/yudane/methods/test_autorun.cljc
(ns yudane.methods.test-autorun
  (:require [yudane.methods.yudane-edn :as ye]
            [yudane.methods.autorun :as ar]
            [yudane.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/yudane/kotoba/seed.edn")
(def ^:private tmp "20-actors/yudane/data/test-autorun.kotoba.edn")
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))
(defn- offers [] (ye/offers seed-path))

(deftest first-beat-appends
  (clean!)
  (let [r (ar/beat {:offers (offers) :tx-id "b1" :as-of "a1" :log-path tmp})]
    (is (:appended r))
    (is (= 8 (:offers r)) "eight offers in the seed")
    (is (= 4 (:consented r)) "four consent")
    (is (pos? (:flex-kwh r)))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

(deftest second-identical-beat-is-noop
  (clean!)
  (ar/beat {:offers (offers) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:offers (offers) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (not (:appended r2)))
    (is (= :no-change (:reason r2)))
    (is (= 1 (count (k/read-log tmp))))
    (clean!)))

(deftest changed-offers-append-new-tx
  (clean!)
  (ar/beat {:offers (offers) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:offers (vec (rest (offers))) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (:appended r2))
    (is (= 2 (count (k/read-log tmp))))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'yudane.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
