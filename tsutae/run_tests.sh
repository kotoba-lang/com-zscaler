#!/usr/bin/env bash
# tsutae — clj/bb test suite (ADR-2606160842 py→clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root (for the :paths
# config in bb.edn). Covers the 8 cell state machines (assembly order), the charter-gate suite,
# and the ported manufacturing-actor agent.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(def nss (quote [tsutae.cells.tsutae-pcb-smt.test-state-machine
                             tsutae.cells.tsutae-chassis-assembly.test-state-machine
                             tsutae.cells.tsutae-display-attachment.test-state-machine
                             tsutae.cells.tsutae-firmware-load.test-state-machine
                             tsutae.cells.tsutae-final-qc.test-state-machine
                             tsutae.cells.tsutae-packaging.test-state-machine
                             tsutae.cells.tsutae-device-attestation.test-state-machine
                             tsutae.cells.tsutae-recycling-intake.test-state-machine
                             tsutae.methods.test-charter-gates
                             tsutae.methods.test-agent]))
              (apply require (quote clojure.test) nss)
              (let [r (apply clojure.test/run-tests nss)]
                (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
