#!/usr/bin/env bb
;; kanjo 勘定 — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh; this
;; replaces the former run_tests.sh (ADR-2606160842 py->clj port wave).
;;
;;   bb 20-actors/kanjo/run_tests.clj      ; run from anywhere
;;
;; methods/test_kotoba_cid.clj + test_pipeline_cid.clj -- fixed (same CID-determinism /
;; byte-parity pin class as kabuto's fix: keyword-vs-string key-access bugs against
;; kotoba.cljc's string-keyed EAVT convention, a stale empty-cid pin -- re-verified against
;; kabuto's independently-pinned literal -- and test_pipeline_cid.clj calling
;; autorun/run-autonomous with kabuto's keyword-args convention instead of kanjo's own real
;; POSITIONAL signature / underscore-keyed return map). NOTE: test_pipeline_cid.clj is SLOW
;; (minutes -- it exercises the full EDGAR-merged graph twice in-process plus once in a
;; spawned child `bb`, the strongest determinism stress in the set).
;;
;; methods/test_analyze.clj was DELETED (a genuine orphan duplicate): its own docstring
;; predates concept_map.cljc/analyze.cljc landing, and its 4 deftest names + asserted
;; values were byte-identical to tests/test_kanjo.cljc's already-working analyze section
;; -- just with the wrong keyword-vs-string key-access convention (never fixed, never
;; wired anywhere, never run until bb test:actors's auto-discovery found it).
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kanjo/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites '[kanjo.methods.test-autorun
              kanjo.tests.test-invariants
              kanjo.tests.test-kanjo
              kanjo.methods.test-concept-map
              kanjo.methods.test-ingest
              kanjo.methods.test-kotoba-cid
              kanjo.methods.test-pipeline-cid])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
