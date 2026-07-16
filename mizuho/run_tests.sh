#!/usr/bin/env bash
# mizuho — clj/bb test suite (ADR-2606160842 py->clj port wave; Python pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote mizuho.methods.test-charter-gates) (quote mizuho.methods.test-chlorination) (quote mizuho.methods.test-water-supply) (quote mizuho.methods.test-pid-parity) (quote mizuho.cells.test-state-machines))(let [r (clojure.test/run-tests (quote mizuho.methods.test-charter-gates) (quote mizuho.methods.test-chlorination) (quote mizuho.methods.test-water-supply) (quote mizuho.methods.test-pid-parity) (quote mizuho.cells.test-state-machines))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
