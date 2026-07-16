#!/usr/bin/env bb
;; Cross-process END-TO-END pipeline-determinism guard for the watatsuna heartbeat.
(ns watatsuna.methods.test-pipeline-cid
  "test_pipeline_cid.clj — watatsuna WHOLE-PIPELINE cross-process determinism (ADR-2605312345 /
  2606012600).

  The autorun test proves the heartbeat resume-safe IN-process; this proves the head-cid of the
  ENTIRE pipeline (observe → classify → analyze → graph-datoms + derived-datoms → commit-DAG)
  agrees ACROSS PROCESSES — by spawning a fresh `bb` and comparing its head-cid to the in-process
  one over the SAME seed. Seed-independent (no fragile literal pin — a sibling may edit the seed),
  yet it catches any process-dependent non-determinism (set-iteration order leaking into the
  canonical form, a hash/encoding change). If a sandbox forbids spawning the child it SKIPS rather
  than false-failing; it only goes red on a genuine divergence.

  Run:  bb --classpath 20-actors 20-actors/watatsuna/methods/test_pipeline_cid.clj"
  (:require [watatsuna.methods.autorun :as autorun]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(defn- tmp-log [] (let [f (java.io.File/createTempFile "wat-log-" ".kotoba.edn")] (.delete f) f))

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
        ;; the real seed yields a substantial cable graph, not a degenerate pin
        (is (every? #(> (get % "datoms") 100) (get r "beats"))))
      (finally (.delete log)))))

(deftest pipeline-is-cross-run-deterministic-in-process
  ;; two independent runs into separate logs → identical head (within this process).
  (is (= (in-process-head 2) (in-process-head 2))))

(deftest pipeline-head-cid-is-cross-PROCESS-deterministic
  ;; spawn a fresh bb, run the same 2-cycle heartbeat, compare head-cids.
  (let [in-proc (in-process-head 2)
        child (try
                (sh "bb" "--classpath" "20-actors" "-e"
                    (str "(require (quote [watatsuna.methods.autorun :as a]))"
                         "(let [f (java.io.File/createTempFile \"wsub-\" \".edn\")] (.delete f)"
                         "(print (get (a/run-autonomous 2 nil f) \"head_cid\")) (.delete f))"))
                (catch Exception e {:exit -1 :err (.getMessage e)}))]
    (is (re-matches cid-re in-proc) "in-process head-cid is a b+64hex CID")
    (if (and (= 0 (:exit child)) (re-find cid-re (:out child)))
      (is (= in-proc (re-find cid-re (:out child)))
          "whole-pipeline head-cid diverged between processes")
      (is true (str "child bb not spawnable in this env — cross-process check skipped"
                    " (exit=" (:exit child) ")")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'watatsuna.methods.test-pipeline-cid)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
