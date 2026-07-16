#!/usr/bin/env bash
# tanemaki — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote tanemaki.methods.test-datom-emit) (quote tanemaki.tests.test-analyze) (quote tanemaki.tests.test-coverage) (quote tanemaki.tests.test-kotoba) (quote tanemaki.tests.test-propose) (quote tanemaki.tests.test-wasm))(let [r (apply clojure.test/run-tests (quote [tanemaki.methods.test-datom-emit tanemaki.tests.test-analyze tanemaki.tests.test-coverage tanemaki.tests.test-kotoba tanemaki.tests.test-propose tanemaki.tests.test-wasm]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
