#!/usr/bin/env bash
# tokigusuri — clj/bb test suite (ADR-2606171300). Sibling of hokorobi/run_tests.sh; auto-wired
# into the fleet green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote tokigusuri.methods.test-datom-emit) (quote tokigusuri.tests.test-analyze) (quote tokigusuri.tests.test-coverage) (quote tokigusuri.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [tokigusuri.methods.test-datom-emit tokigusuri.tests.test-analyze tokigusuri.tests.test-coverage tokigusuri.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
