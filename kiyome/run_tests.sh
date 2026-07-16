#!/usr/bin/env bash
# kiyome — clj/bb test suite (ADR-2606160842 py->clj port wave); wired into the fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote kiyome.cells.surface-cleaning.test-state-machine))(let [r (clojure.test/run-tests (quote kiyome.cells.surface-cleaning.test-state-machine))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
