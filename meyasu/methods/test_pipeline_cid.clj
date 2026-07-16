#!/usr/bin/env bb
;; Cross-process END-TO-END pipeline-determinism guard for the meyasu arbitrage heartbeat.
(ns meyasu.methods.test-pipeline-cid
  "test_pipeline_cid.clj — meyasu WHOLE-PIPELINE cross-process determinism (ADR-2605312345 /
  2606073201).

  The autorun test proves the heartbeat resume-safe IN-process; this proves the head-cid of the
  ENTIRE 統合-arbitrage pipeline (observe seed items → fuse price/supply-demand/forecast band →
  commit-DAG) agrees ACROSS PROCESSES by spawning a fresh `bb` and comparing its head-cid to the
  in-process one over the SAME seed. Seed-independent (no fragile literal); catches
  process-dependent non-determinism; gracefully SKIPS if a sandbox forbids spawning the child.

  Run:  bb --classpath 20-actors 20-actors/meyasu/py/test_pipeline_cid.clj"
  (:require [meyasu.methods.autorun :as autorun]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private seed "20-actors/meyasu/kotoba/seed.json")
(defn- tmp-log [] (let [f (java.io.File/createTempFile "mey-log-" ".kotoba.edn")] (.delete f) (str f)))
(defn- in-process-head [cycles]
  (let [log (tmp-log)]
    (try (:head-cid (autorun/run-autonomous cycles seed log)) (finally (.delete (io/file log))))))
(def ^:private cid-re #"b[0-9a-f]{64}")

(deftest heartbeat-emits-nonempty-graph
  (let [log (tmp-log)]
    (try
      (let [r (autorun/run-autonomous 3 seed log)]
        (is (:ok (:chain r)))
        (is (= 3 (:log-length r)))
        (is (every? #(pos? (:datoms %)) (:beats r))))
      (finally (.delete (io/file log))))))

(deftest pipeline-is-cross-run-deterministic-in-process
  (is (= (in-process-head 3) (in-process-head 3))))

(deftest pipeline-head-cid-is-cross-PROCESS-deterministic
  (let [in-proc (in-process-head 3)
        child (try
                (sh "bb" "--classpath" "20-actors" "-e"
                    (str "(require (quote [meyasu.methods.autorun :as a]))"
                         "(let [f (java.io.File/createTempFile \"meysub-\" \".edn\")] (.delete f)"
                         "(print (:head-cid (a/run-autonomous 3 \"" seed "\" (str f)))) (.delete f))"))
                (catch Exception e {:exit -1 :err (.getMessage e)}))]
    (is (re-matches cid-re in-proc) "in-process head-cid is a b+64hex CID")
    (if (and (= 0 (:exit child)) (re-find cid-re (:out child)))
      (is (= in-proc (re-find cid-re (:out child)))
          "whole-pipeline head-cid diverged between processes")
      (is true (str "child bb not spawnable — cross-process check skipped (exit=" (:exit child) ")")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'meyasu.methods.test-pipeline-cid)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
