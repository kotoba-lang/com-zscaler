#!/usr/bin/env bash
# kamado — clj/bb test suite (ADR-2606160842 py->clj port wave); ALL test namespaces, fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(def nss (quote [kamado.methods.test-charter-gates
                             kamado.methods.test-ingest
                             kamado.methods.test-kamado
                             kamado.cells.feedstock-guard.test-state-machine
                             kamado.cells.decommission-plan.test-state-machine
                             kamado.cells.decommission-robot.test-state-machine
                             kamado.cells.asset-observation.test-state-machine
                             kamado.cells.synthesis-control.test-state-machine
                             kamado.cells.transition-bridge.test-state-machine]))
            (apply require (quote clojure.test) nss)
            (let [r (apply clojure.test/run-tests nss)]
              (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
