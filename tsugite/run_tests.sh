#!/usr/bin/env bash
# tsugite — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote tsugite.methods.test-datom-emit) (quote tsugite.tests.test-analyze) (quote tsugite.tests.test-coverage) (quote tsugite.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [tsugite.methods.test-datom-emit tsugite.tests.test-analyze tsugite.tests.test-coverage tsugite.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
