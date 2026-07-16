#!/usr/bin/env bash
# warifu — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test)
  (quote warifu.cells.test-refund) (quote warifu.cells.test-authorize) (quote warifu.cells.test-capture)
  (quote warifu.cells.test-settle) (quote warifu.cells.test-dispute) (quote warifu.cells.test-eavt-schema)
  (quote warifu.cells.test-guarded-substrate) (quote warifu.test-lexicons))
(let [r (apply clojure.test/run-tests (quote [warifu.cells.test-refund warifu.cells.test-authorize
  warifu.cells.test-capture warifu.cells.test-settle warifu.cells.test-dispute
  warifu.cells.test-eavt-schema warifu.cells.test-guarded-substrate warifu.test-lexicons]))]
  (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
