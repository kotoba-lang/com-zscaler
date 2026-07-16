#!/usr/bin/env bb
;; sonae — charter-gate conformance test runner (bb; ADR-2606091200 R0
;; scaffold slice). Test-only / network-free / no cell execution.
;;
;; Run from this directory:      bb run_tests.clj
;; Run from the repo root:       bb 20-actors/sonae/run_tests.clj
(require '[babashka.classpath :as cp]
         '[clojure.java.io :as io])

(def ^:private here
  (.getParentFile (io/file (or (System/getProperty "babashka.file") *file*))))
(def ^:private root (.. here getParentFile getParentFile)) ;; sonae -> 20-actors -> repo root

;; Add the repo's `20-actors` source root to the classpath so the
;; `sonae.methods.test-charter-gates` namespace resolves regardless of
;; the caller's cwd (mirrors root bb.edn's :paths ["20-actors" ...]).
(cp/add-classpath (str (io/file root "20-actors")))

(require '[clojure.test :as t])
(require 'sonae.methods.test-charter-gates)

(let [r (t/run-tests 'sonae.methods.test-charter-gates)]
  (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))
