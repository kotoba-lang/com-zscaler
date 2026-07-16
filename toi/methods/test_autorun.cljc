#!/usr/bin/env bb
;; 樋 toi — heartbeat (idempotent-by-content) tests.
;; Run:  bb --classpath 20-actors 20-actors/toi/methods/test_autorun.cljc
(ns toi.methods.test-autorun
  (:require [toi.methods.toi-edn :as te]
            [toi.methods.autorun :as ar]
            [toi.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/toi/kotoba/seed.edn")
(def ^:private tmp "20-actors/toi/data/test-autorun.kotoba.edn")
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))
(defn- jobs [] (te/jobs seed-path))
(defn- sites [] (te/sites seed-path))

(deftest first-beat-appends
  (clean!)
  (let [r (ar/beat {:jobs (jobs) :sites (sites) :tx-id "b1" :as-of "a1" :log-path tmp})]
    (is (:appended r))
    (is (= 5 (:routed r)) "five movable jobs route")
    (is (pos? (:avoided-carbon-kg r)))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

(deftest second-identical-beat-is-noop
  (clean!)
  (ar/beat {:jobs (jobs) :sites (sites) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:jobs (jobs) :sites (sites) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (not (:appended r2)))
    (is (= :no-change (:reason r2)))
    (is (= 1 (count (k/read-log tmp))))
    (clean!)))

(deftest changed-jobs-append-new-tx
  (clean!)
  (ar/beat {:jobs (jobs) :sites (sites) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:jobs (vec (rest (jobs))) :sites (sites) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (:appended r2))
    (is (= 2 (count (k/read-log tmp))))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'toi.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
