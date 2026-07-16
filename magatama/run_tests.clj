#!/usr/bin/env bb
;; magatama — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb 20-actors/magatama/run_tests.clj      ; run from anywhere
;;
;; Runs the bb/cljc cell tests (shionome_core + every shionome/suimin cell run-chain +
;; Council gate), per the repo py→cljc rule.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs])

(cp/add-classpath (str (fs/parent (fs/absolutize *file*))))

(require 'magatama.cells.test-cells)

(println "=== magatama cljc cell tests ===")
(magatama.cells.test-cells/-main)
