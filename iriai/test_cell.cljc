#!/usr/bin/env bb
;; iriai 入会 — fleet cell tests.
;; Run:  bb --classpath 20-actors 20-actors/iriai/test_cell.cljc
(ns iriai.test-cell
  (:require [iriai.cell :as cell]
            [iriai.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def ^:private tmp (str (System/getProperty "java.io.tmpdir") "/iriai-test-cell.kotoba.edn"))
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))

(deftest fire-runs-a-beat
  (clean!)
  (let [r (cell/fire {:log-path tmp :tx-id "t1" :as-of "a1"})]
    (is (= "IriaiCommonsHeartbeatCell" (:cell r)))
    (is (:appended r) "first fire appends")
    (is (pos? (:datoms r)) "all five layers emit datoms")
    (is (= 11 (:fund r)) "11 fundable cells (incl. kibou road)")
    (is (= 11 (:gov r)))
    (is (= 11 (:twin r)) "11 deployed assets")
    (is (false? (:server-held-key r)) "no-server-key")
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

(deftest fire-is-idempotent
  (clean!)
  (let [r1 (cell/fire {:log-path tmp :tx-id "t1" :as-of "a1"})
        r2 (cell/fire {:log-path tmp :tx-id "t2" :as-of "a2"})]
    (is (:appended r1))
    (is (not (:appended r2)) "unchanged beat → no-op")
    (is (= :no-change (:reason r2)))
    (is (= 1 (count (k/read-log tmp))))
    (clean!)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.test-cell)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
