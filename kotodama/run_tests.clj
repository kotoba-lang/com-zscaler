#!/usr/bin/env bb
;; kotodama — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb 20-actors/kotodama/run_tests.clj      ; run from anywhere
;;
;; Tests:
;;   - 13 cell R0 scaffold stubs (6 tadori + 7 tsukuroi) each raise ex-info on .solve
;;   - kotoba.datom Datom-log engine smoke (already-ported, determinism check)
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kotodama/run_tests.clj
(def actor-dir (fs/parent (fs/absolutize *file*)))
(def actors-root (fs/parent actor-dir))

(doseq [p [actors-root (fs/file actor-dir "src") (fs/file actor-dir "tests")]]
  (cp/add-classpath (str p)))

(def suites '[kotodama.tests.test-cells
              kotodama.tests.test-datom])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
