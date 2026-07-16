#!/usr/bin/env bash
# subaru — clj/bb test suite (ADR-2606162355). Auto-wired into the fleet green-check;
# runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote subaru.tests.test-subaru) (quote subaru.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [subaru.tests.test-subaru subaru.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
