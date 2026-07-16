#!/usr/bin/env bb
;; 撓 tawami — heartbeat (idempotent-by-content) tests.
;; Run:  bb --classpath 20-actors 20-actors/tawami/methods/test_autorun.cljc
(ns tawami.methods.test-autorun
  (:require [tawami.methods.tawami-edn :as te]
            [tawami.methods.autorun :as ar]
            [tawami.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tawami/kotoba/seed.edn")
(def ^:private tmp "20-actors/tawami/data/test-autorun.kotoba.edn")
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))
(defn- assets [] (te/assets seed-path))

(deftest first-beat-appends
  (clean!)
  (let [r (ar/beat {:assets (assets) :tx-id "b1" :as-of "a1" :log-path tmp})]
    (is (:appended r))
    (is (= 12 (:assets r)) "twelve assets in the seed")
    (is (pos? (:flex-value r)))
    (is (pos? (:fast-count r)))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

(deftest second-identical-beat-is-noop
  (clean!)
  (ar/beat {:assets (assets) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:assets (assets) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (not (:appended r2)))
    (is (= :no-change (:reason r2)))
    (is (= 1 (count (k/read-log tmp))))
    (clean!)))

(deftest changed-assets-append-new-tx
  (clean!)
  (ar/beat {:assets (assets) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:assets (vec (rest (assets))) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (:appended r2))
    (is (= 2 (count (k/read-log tmp))))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tawami.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
