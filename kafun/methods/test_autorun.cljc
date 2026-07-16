#!/usr/bin/env bb
;; kafun 花粉 — heartbeat tests (持続永続化: append-on-change, idempotent, resume-safe).
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_autorun.cljc
(ns kafun.methods.test-autorun
  (:require [kafun.methods.autorun :as a]
            [kafun.methods.kafun-edn :as ke]
            [kafun.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/kafun/kotoba/seed.edn")
(def ^:private tmp
  (str (System/getProperty "java.io.tmpdir") "/kafun-test-autorun.kotoba.edn"))
(defn- fresh! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))) tmp)
(defn- stands [] (ke/stands seed-path))

(deftest first-beat-appends
  (let [path (fresh!)
        r (a/beat {:stands (stands) :tx-id "b0" :as-of "t0" :log-path path})]
    (is (:appended r) "first beat appends")
    (is (pos? (:count r)) "verdict datoms emitted")
    (is (map? (:verdicts r)) "tally present")
    (is (= 1 (count (k/read-log path))))
    (is (:ok (k/verify-chain path)))))

(deftest second-identical-beat-is-noop
  (let [path (fresh!)]
    (a/beat {:stands (stands) :tx-id "b0" :as-of "t0" :log-path path})
    (let [r2 (a/beat {:stands (stands) :tx-id "b1" :as-of "t1" :log-path path})]
      (is (not (:appended r2)) "identical assessment → no-op")
      (is (= :no-change (:reason r2)) "idempotent-by-content: ledger records CHANGES")
      (is (= 1 (count (k/read-log path))) "chain did not grow"))))

(deftest changed-stands-append-new-tx
  (let [path (fresh!)]
    (a/beat {:stands (stands) :tx-id "b0" :as-of "t0" :log-path path})
    ;; flip one stand's consent → its verdict changes → a real change to persist
    (let [mutated (map (fn [s] (if (= "sugi-akita-d" (:id s)) (assoc s :consent true) s)) (stands))
          r (a/beat {:stands (vec mutated) :tx-id "b2" :as-of "t2" :log-path path})]
      (is (:appended r) "a changed assessment appends")
      (is (= 2 (count (k/read-log path))))
      (is (:ok (k/verify-chain path)) "chain stays intact across beats"))))

(deftest resume-safe-head-chaining
  (let [path (fresh!)
        r0 (a/beat {:stands (stands) :tx-id "b0" :as-of "t0" :log-path path})
        mutated (vec (map (fn [s] (if (= "sugi-akita-d" (:id s)) (assoc s :consent true) s)) (stands)))
        r1 (a/beat {:stands mutated :tx-id "b1" :as-of "t1" :log-path path})]
    (is (= (:head r0) (get (first (k/read-log path)) ":tx/cid")))
    (is (= (:head r1) (k/head-cid path)) "beat head = ledger head after each append")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
