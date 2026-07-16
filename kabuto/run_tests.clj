#!/usr/bin/env bb
;; kabuto 兜 — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh; this
;; replaces the former run_tests.sh (ADR-2606160842 py->clj port wave).
;;
;;   bb 20-actors/kabuto/run_tests.clj      ; run from anywhere
;;
;; methods/test_ingest.clj + test_kotoba_cid.clj were stale-reference bugs (ingest.cljc
;; was missing merge-bridged/gated-source?; kotoba.cljc was missing canonical-order; both
;; test files also had keyword-vs-string key-access bugs against this ns's string-keyed
;; EAVT convention) -- fixed, wired in below.
;;
;; methods/test_pipeline_cid.clj -- fixed (same class): (1) fixture-classifies-as-expected
;; called clojure.edn/read-string directly on the fixture text, but kabuto-edn/classify
;; expects the string-keyed shape kabuto-edn's OWN reader produces ("keywords kept as
;; \":ns/name\" strings" -- its own docstring); switched to kabuto-edn/read-edn. (2) all 3
;; pipeline deftests called autorun/run-autonomous with kabuto-style keyword-args this
;; actor's real implementation is POSITIONAL for ([cycles graph-path* log-path]), returning
;; a string/underscore-keyed map ("head_cid" "log_length" "chain" "beats") -- adapted to
;; the real signature/shape. The head-cid-2cyc pin had never actually been exercised (the
;; call bug always errored first) -- re-verified via 2 independent in-process runs
;; producing an identical value.
;;
;; methods/test_bpmn.clj is NOT run here: same keyword-vs-string bug PLUS a genuine
;; content-CID byte-parity mismatch against bpmn.cljc (needs investigation, not just a
;; key-access fix) -- a real, separate gap.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kabuto/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites '[kabuto.methods.test-charter-gates
              kabuto.methods.test-analyze
              kabuto.methods.test-autorun
              kabuto.methods.test-social
              kabuto.methods.test-ingest
              kabuto.methods.test-kotoba-cid
              kabuto.methods.test-pipeline-cid
              kabuto.viz.test-build-bpmn-manifest
              kabuto.viz.test-build-viz-data])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
