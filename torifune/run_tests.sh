#!/usr/bin/env bash
# torifune — clj/bb test suite (ADR-2606162355). Auto-wired into the fleet green-check;
# runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote torifune.tests.test-torifune) (quote torifune.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [torifune.tests.test-torifune torifune.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
