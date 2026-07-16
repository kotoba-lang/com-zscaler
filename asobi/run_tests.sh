#!/usr/bin/env bash
# asobi — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote asobi.methods.test-datom-emit) (quote asobi.tests.test-analyze) (quote asobi.tests.test-coverage) (quote asobi.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [asobi.methods.test-datom-emit asobi.tests.test-analyze asobi.tests.test-coverage asobi.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
