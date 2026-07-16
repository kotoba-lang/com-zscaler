#!/usr/bin/env bash
# rasen — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote rasen.tests.test-analyze) (quote rasen.tests.test-coverage) (quote rasen.tests.test-datom-emit) (quote rasen.tests.test-ingest) (quote rasen.tests.test-wasm) (quote rasen.tests.test-kotoba) (quote rasen.tests.test-publish))(let [r (apply clojure.test/run-tests (quote [rasen.tests.test-analyze rasen.tests.test-coverage rasen.tests.test-datom-emit rasen.tests.test-ingest rasen.tests.test-wasm rasen.tests.test-kotoba rasen.tests.test-publish]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
