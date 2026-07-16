#!/usr/bin/env bash
# shiori — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote shiori.methods.test-datom-emit) (quote shiori.tests.test-analyze) (quote shiori.tests.test-coverage) (quote shiori.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [shiori.methods.test-datom-emit shiori.tests.test-analyze shiori.tests.test-coverage shiori.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
