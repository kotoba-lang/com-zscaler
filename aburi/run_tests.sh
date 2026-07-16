#!/usr/bin/env bash
# aburi — clj/bb test suite (ADR-2606161630 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote aburi.tests.test-analyze) (quote aburi.tests.test-coverage) (quote aburi.tests.test-kotoba) (quote aburi.tests.test-ingest) (quote aburi.tests.test-kotoba-bridge))(let [r (apply clojure.test/run-tests (quote [aburi.tests.test-analyze aburi.tests.test-coverage aburi.tests.test-kotoba aburi.tests.test-ingest aburi.tests.test-kotoba-bridge]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
