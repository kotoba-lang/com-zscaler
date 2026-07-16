#!/usr/bin/env bash
# nusa — clj/bb test suite (ADR-2606160842 py->clj port wave); ALL test namespaces, fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote nusa.cells.test-state-machine) (quote nusa.methods.test-analyze) (quote nusa.methods.test-charter-gates))(let [r (apply clojure.test/run-tests (quote [nusa.cells.test-state-machine nusa.methods.test-analyze nusa.methods.test-charter-gates]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
