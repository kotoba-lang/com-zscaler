#!/usr/bin/env bash
# hataori — clj/bb test suite (ADR-2606160842 py->clj port wave); wired into the fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote hataori.cells.finishing-packing.test-state-machine))(let [r (clojure.test/run-tests (quote hataori.cells.finishing-packing.test-state-machine))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
