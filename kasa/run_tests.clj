#!/usr/bin/env bb
;; kasa 嵩 — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh; this
;; replaces the former run_tests.sh (ADR-2606160842 py→clj port wave, ADR-2606072000).
;;
;;   bb 20-actors/kasa/run_tests.clj      ; run from anywhere
;;
;; methods/test_ingest.clj -- fixed and wired in below. It had never once run: requiring
;; it threw at load time (Unable to resolve symbol: ing/fetch-epoch-gate — the test
;; expected a pure, testable G7 gate-check function that was never implemented; only
;; fetch-epoch itself existed, reading System/getenv and throwing directly). Extracted
;; fetch-epoch-gate as a pure [gate-value -> nil|refusal-string] fn, with fetch-epoch now
;; delegating to it. That load-failure had been masking 6 further errors underneath: all
;; 5 merge-with-seed call sites were missing the (required, first) seed argument
;; entirely, and offline-ingest was called with 0 args instead of 1 (here). Fixed all 6
;; call sites to match the real 3-arg/1-arg signatures.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kasa/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites '[kasa.tests.test-invariants
              kasa.tests.test-kasa
              kasa.methods.test-ingest])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
