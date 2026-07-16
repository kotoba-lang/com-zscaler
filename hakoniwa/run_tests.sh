#!/usr/bin/env bash
# hakoniwa — clj/bb test suite (ADR-2606160842 / ADR-2606111500). Standalone-runnable via babashka
# from the repo root (the actor's namespaces are on the bb classpath); runs all four test
# namespaces (distribution/ingest/runtime/simulate) and wires them into the fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote hakoniwa.tests.test-distribution) (quote hakoniwa.tests.test-ingest) (quote hakoniwa.tests.test-runtime) (quote hakoniwa.tests.test-simulate))(let [r (clojure.test/run-tests (quote hakoniwa.tests.test-distribution) (quote hakoniwa.tests.test-ingest) (quote hakoniwa.tests.test-runtime) (quote hakoniwa.tests.test-simulate))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
