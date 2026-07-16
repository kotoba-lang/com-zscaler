#!/usr/bin/env bash
# tate — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote tate.tests.test-case-actors) (quote tate.tests.test-coverage) (quote tate.tests.test-coverage-publish) (quote tate.tests.test-kotoba) (quote tate.tests.test-respond) (quote tate.tests.test-site) (quote tate.tests.test-terms))(let [r (apply clojure.test/run-tests (quote [tate.tests.test-case-actors tate.tests.test-coverage tate.tests.test-coverage-publish tate.tests.test-kotoba tate.tests.test-respond tate.tests.test-site tate.tests.test-terms]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
