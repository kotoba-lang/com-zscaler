#!/usr/bin/env bb
;; kuni-umi 国産み — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb 20-actors/kuni-umi/run_tests.clj      ; run from anywhere
;;
;; kuni-umi's real implementation lives in cells/*/cell.cljc (a 6-cell Pregel
;; deployment pipeline: site_survey -> deployment_planning ->
;; construction_orchestration -> commissioning -> audit_witness ->
;; decommission), NOT in methods/ — this repo has no top-level run_tests.clj
;; because none existed, which made the etzhayyim.vitals scanner see zero
;; runnable code and classify kuni-umi as 死 (stub) despite ~2000 lines of
;; real, tested cljc. This runner closes that gap.
;;
;; Classpath root (the absolute 20-actors/ dir) is derived from THIS file's location, so
;; the kuni-umi.cells.*.cell namespaces resolve without a wrapper or --classpath flag.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kuni-umi/run_tests.clj -> classpath root is its parent's parent (20-actors/)
;; (resolved via the kuni_umi/ symlink so kuni-umi.* namespace segments munge correctly)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

;; commissioning has cell.cljc but no test_cell.cljc yet (no test suite to run).
(def suites
  '[kuni-umi.cells.audit-witness.test-cell
    kuni-umi.cells.construction-orchestration.test-cell
    kuni-umi.cells.decommission.test-cell
    kuni-umi.cells.deployment-planning.test-cell
    kuni-umi.cells.site-survey.test-cell])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── kuni-umi 国産み: ALL suites green ──")
    (do (println "── kuni-umi 国産み: FAILURES above ──")
        (System/exit 1))))
