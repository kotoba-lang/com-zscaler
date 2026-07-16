#!/usr/bin/env bb
;; 燠 okibi — heartbeat (idempotent-by-content) tests.
;; Run:  bb --classpath 20-actors 20-actors/okibi/methods/test_autorun.cljc
(ns okibi.methods.test-autorun
  (:require [okibi.methods.okibi-edn :as oe]
            [okibi.methods.autorun :as ar]
            [okibi.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/okibi/kotoba/seed.edn")
(def ^:private tmp "20-actors/okibi/data/test-autorun.kotoba.edn")
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))
(defn- srcs [] (oe/sources seed-path))
(defn- snks [] (oe/sinks seed-path))

(deftest first-beat-appends
  (clean!)
  (let [r (ar/beat {:sources (srcs) :sinks (snks) :tx-id "b1" :as-of "a1" :log-path tmp})]
    (is (:appended r))
    (is (pos? (:matches r)) "at least one match")
    (is (pos? (:matched-kw r)))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

(deftest second-identical-beat-is-noop
  (clean!)
  (ar/beat {:sources (srcs) :sinks (snks) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:sources (srcs) :sinks (snks) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (not (:appended r2)))
    (is (= :no-change (:reason r2)))
    (is (= 1 (count (k/read-log tmp))))
    (clean!)))

(deftest changed-sinks-append-new-tx
  (clean!)
  (ar/beat {:sources (srcs) :sinks (snks) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:sources (srcs) :sinks (vec (rest (snks))) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (:appended r2))
    (is (= 2 (count (k/read-log tmp))))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'okibi.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
