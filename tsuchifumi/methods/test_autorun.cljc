#!/usr/bin/env bb
;; tsuchifumi 土踏み — heartbeat tests (append + idempotent-by-content + resume-safe).
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_autorun.cljc
(ns tsuchifumi.methods.test-autorun
  (:require [tsuchifumi.methods.tsuchifumi-edn :as te]
            [tsuchifumi.methods.autorun :as ar]
            [tsuchifumi.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tsuchifumi/kotoba/seed.edn")
(defn- inputs []
  (let [s (te/load-seed seed-path)]
    {:regions (:regions s) :evidence (:evidence s) :drivers (:drivers s)}))

(deftest beat-appends-then-idempotent
  (let [tmp (str (System/getProperty "java.io.tmpdir") "/tsuchifumi-beat-test.edn")
        {:keys [regions evidence drivers]} (inputs)]
    (io/delete-file tmp true)
    (let [b1 (ar/beat {:regions regions :evidence evidence :drivers drivers
                       :tx-id "t1" :as-of "a1" :log-path tmp})
          b2 (ar/beat {:regions regions :evidence evidence :drivers drivers
                       :tx-id "t2" :as-of "a2" :log-path tmp})]
      (is (true? (:appended b1)) "first beat appends")
      (is (pos? (:count b1)))
      (is (false? (:appended b2)) "identical second beat is a no-op (idempotent-by-content)")
      (is (= :no-change (:reason b2)))
      (is (= (:head b1) (:head b2)) "head unchanged on a no-op")
      (is (true? (:ok (k/verify-chain tmp))))
      (is (= 1 (count (k/read-log tmp))) "only one tx persisted")
      (io/delete-file tmp true))))

(deftest beat-records-verdicts-and-severity
  (let [tmp (str (System/getProperty "java.io.tmpdir") "/tsuchifumi-beat-tally.edn")
        {:keys [regions evidence drivers]} (inputs)]
    (io/delete-file tmp true)
    (let [b (ar/beat {:regions regions :evidence evidence :drivers drivers
                      :tx-id "t1" :as-of "a1" :log-path tmp})]
      (is (map? (:verdicts b)))
      (is (map? (:severity b)))
      (io/delete-file tmp true))))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-autorun)]
  (when (pos? (+ fail error)) (System/exit 1)))
