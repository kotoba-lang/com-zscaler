#!/usr/bin/env bash
# silicon — bb/clj test suite (ADR-2606160842 py->clj port wave; cell+method Python pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote silicon.cells.test-state-machine) (quote silicon.methods.test-fab-cell) (quote silicon.methods.test-wafer-handler) (quote silicon.methods.test-lot-ledger) (quote silicon.methods.test-fab-flow) (quote silicon.methods.test-agent) )(let [r (clojure.test/run-tests (quote silicon.cells.test-state-machine) (quote silicon.methods.test-fab-cell) (quote silicon.methods.test-wafer-handler) (quote silicon.methods.test-lot-ledger) (quote silicon.methods.test-fab-flow) (quote silicon.methods.test-agent) )](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
