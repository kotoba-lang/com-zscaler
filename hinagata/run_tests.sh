#!/usr/bin/env bash
# hinagata — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote hinagata.tests.test-analyze) (quote hinagata.tests.test-coverage) (quote hinagata.tests.test-esign) (quote hinagata.tests.test-kotoba) (quote hinagata.tests.test-maturity) (quote hinagata.tests.test-query) (quote hinagata.tests.test-validate) (quote hinagata.tests.test-wasm))(let [r (apply clojure.test/run-tests (quote [hinagata.tests.test-analyze hinagata.tests.test-coverage hinagata.tests.test-esign hinagata.tests.test-kotoba hinagata.tests.test-maturity hinagata.tests.test-query hinagata.tests.test-validate hinagata.tests.test-wasm]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
