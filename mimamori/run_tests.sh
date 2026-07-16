#!/usr/bin/env bash
# mimamori — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote mimamori.tests.test-bond) (quote mimamori.tests.test-cell) (quote mimamori.tests.test-coverage) (quote mimamori.tests.test-kotoba-autorun) (quote mimamori.tests.test-ontology-parity) (quote mimamori.tests.test-shakai) (quote mimamori.tests.test-wasm-app))(let [r (apply clojure.test/run-tests (quote [mimamori.tests.test-bond mimamori.tests.test-cell mimamori.tests.test-coverage mimamori.tests.test-kotoba-autorun mimamori.tests.test-ontology-parity mimamori.tests.test-shakai mimamori.tests.test-wasm-app]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
