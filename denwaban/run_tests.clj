#!/usr/bin/env bb
;; denwaban — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb 20-actors/denwaban/run_tests.clj      ; run from anywhere
;;
;; Contract test (R0): pipeline composition + G2 booking-delegation + G7 gate.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

(cp/add-classpath (str (fs/file (fs/parent (fs/absolutize *file*)) "cells")))

(require 'denwaban.test-session)

(let [{:keys [fail error]} (t/run-tests 'denwaban.test-session)]
  (System/exit (if (pos? (+ fail error)) 1 0)))
