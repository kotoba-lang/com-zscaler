#!/usr/bin/env bb
(ns umisachi.tests.test-autorun
  "umisachi 海幸 — autonomous heartbeat invariants (ADR-2606074200 / 2605312345).

  The marine-bounty observe→analyze→persist heartbeat on the append-only kotoba Datom log:
  autonomy, deterministic/resume-safe persistence, append-only chaining, and the G1
  restoration-not-target-list stance.

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_autorun.clj"
  (:require [umisachi.methods.autorun :as autorun]
            [umisachi.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]))

(defn- tmp [] (let [f (java.io.File/createTempFile "umi-auto-" ".kotoba.edn")] (.delete f) (str f)))

(deftest heartbeat-persists
  (let [log (tmp)]
    (try
      (let [r (autorun/run-autonomous :cycles 3 :log-path log)]
        (is (= 3 (:log-length r)))
        (is (every? #(pos? (:datoms %)) (:beats r)))
        (is (:ok (:chain r)))
        (is (re-matches #"b[0-9a-f]{64}" (:head-cid r))))
      (finally (.delete (java.io.File. log))))))

(deftest deterministic-resume-safe
  ;; two independent runs into separate logs → identical per-cycle CIDs (cycle drives tx-id/as-of).
  (let [a (tmp) b (tmp)]
    (try
      (is (= (mapv :cid (:beats (autorun/run-autonomous :cycles 3 :log-path a)))
             (mapv :cid (:beats (autorun/run-autonomous :cycles 3 :log-path b)))))
      (finally (.delete (java.io.File. a)) (.delete (java.io.File. b))))))

(deftest append-only-prev-threading
  (let [log (tmp)]
    (try
      (autorun/run-cycle 1 :log-path log)
      (let [after1 (k/read-log log)]
        (autorun/run-cycle 2 :log-path log)
        (let [after2 (k/read-log log)]
          (is (= (inc (count after1)) (count after2)) "append-only, never rewrites")
          (is (= (:tx/cid (nth after1 0)) (:tx/prev (nth after2 1))) "cycle-2 prev = cycle-1 cid")))
      (finally (.delete (java.io.File. log))))))

(deftest emits-the-full-marine-graph
  ;; the seed yields a substantial non-degenerate graph (227 graph + 50 derived = 277 datoms/cycle).
  (let [log (tmp)]
    (try
      (let [bt (first (:beats (autorun/run-autonomous :cycles 1 :log-path log)))]
        (is (= 277 (:datoms bt)))
        (is (pos? (:nourishment-bearers bt)))
        (is (pos? (:edges bt))))
      (finally (.delete (java.io.File. log))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-autorun)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
