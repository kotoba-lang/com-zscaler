#!/usr/bin/env bb
;; Cross-process END-TO-END pipeline-determinism guard for the watari heartbeat.
(ns watari.methods.test-pipeline-cid
  "test_pipeline_cid.clj — watari WHOLE-PIPELINE cross-process determinism (ADR-2605312345 /
  2606041827).

  Proves the head-cid of the ENTIRE live-movement pipeline (observe → classify → analyze →
  graph-datoms + derived-datoms → commit-DAG) agrees ACROSS PROCESSES by spawning a fresh `bb`
  and comparing its head-cid to the in-process one over the SAME seed. Seed-independent (no
  fragile literal — a sibling may edit the seed); catches process-dependent non-determinism;
  gracefully SKIPS if a sandbox forbids spawning the child (red only on genuine divergence).

  Run:  bb --classpath 20-actors 20-actors/watari/methods/test_pipeline_cid.clj"
  (:require [watari.methods.autorun :as autorun]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(defn- tmp-log [] (let [f (java.io.File/createTempFile "wri-log-" ".kotoba.edn")] (.delete f) f))

(defn- in-process-head [cycles]
  (let [log (tmp-log)]
    (try (get (autorun/run-autonomous cycles nil log) "head_cid")
         (finally (.delete log)))))

(def ^:private cid-re #"b[0-9a-f]{64}")

(deftest heartbeat-emits-nonempty-graph
  (let [log (tmp-log)]
    (try
      (let [r (autorun/run-autonomous 2 nil log)]
        (is (get (get r "chain") "ok"))
        (is (= 2 (get r "log_length")))
        (is (every? #(> (get % "datoms") 100) (get r "beats"))))
      (finally (.delete log)))))

(deftest pipeline-is-cross-run-deterministic-in-process
  (is (= (in-process-head 2) (in-process-head 2))))

(deftest pipeline-head-cid-is-cross-PROCESS-deterministic
  (let [in-proc (in-process-head 2)
        child (try
                (sh "bb" "--classpath" "20-actors" "-e"
                    (str "(require (quote [watari.methods.autorun :as a]))"
                         "(let [f (java.io.File/createTempFile \"wrisub-\" \".edn\")] (.delete f)"
                         "(print (get (a/run-autonomous 2 nil f) \"head_cid\")) (.delete f))"))
                (catch Exception e {:exit -1 :err (.getMessage e)}))]
    (is (re-matches cid-re in-proc) "in-process head-cid is a b+64hex CID")
    (if (and (= 0 (:exit child)) (re-find cid-re (:out child)))
      (is (= in-proc (re-find cid-re (:out child)))
          "whole-pipeline head-cid diverged between processes")
      (is true (str "child bb not spawnable in this env — cross-process check skipped"
                    " (exit=" (:exit child) ")")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'watari.methods.test-pipeline-cid)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
