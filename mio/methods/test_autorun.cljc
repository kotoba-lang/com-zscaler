#!/usr/bin/env bb
;; 澪 mio — heartbeat (idempotent-by-content) tests.
;; Run:  bb --classpath 20-actors 20-actors/mio/methods/test_autorun.cljc
(ns mio.methods.test-autorun
  (:require [mio.methods.mio-edn :as me]
            [mio.methods.autorun :as ar]
            [mio.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/mio/kotoba/seed.edn")
(def ^:private tmp "20-actors/mio/data/test-autorun.kotoba.edn")
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))
(defn- claims [] (me/claims seed-path))

(deftest first-beat-appends-and-reports-flowrate
  (clean!)
  (let [r (ar/beat {:claims (claims) :tx-id "b1" :as-of "a1" :log-path tmp})]
    (is (:appended r))
    (is (= 15 (:claims r)) "fifteen claims in the seed")
    (is (= 9 (:verified r)) "nine verify")
    (is (pos? (:flowrate r)) "positive org Flowrate")
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

(deftest second-identical-beat-is-noop
  (clean!)
  (ar/beat {:claims (claims) :tx-id "b1" :as-of "a1" :log-path tmp})
  (let [r2 (ar/beat {:claims (claims) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (not (:appended r2)) "idempotent-by-content: no change → no append")
    (is (= :no-change (:reason r2)))
    (is (= 1 (count (k/read-log tmp))) "still one tx")
    (clean!)))

(deftest changed-claims-append-new-tx
  (clean!)
  (ar/beat {:claims (claims) :tx-id "b1" :as-of "a1" :log-path tmp})
  ;; drop one claim → verdicts change → a new tx must append
  (let [r2 (ar/beat {:claims (vec (rest (claims))) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (:appended r2))
    (is (= 2 (count (k/read-log tmp))))
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'mio.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
