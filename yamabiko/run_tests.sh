#!/usr/bin/env bash
# yamabiko — clj/bb test suite (ADR-2605252600 py→clj port wave); wired into the fleet
# green-check. Runs all cljc test namespaces via babashka from the repo root (for the
# :paths config in bb.edn). Covers 9 cell state machines: bogie_assembly, carbody_fabrication,
# interior_hvac, traction_electrical, final_assembly, dynamic_test, emissions_acoustic_audit,
# homologation_binder, silen_rail_review; plus the agent handlers (methods/agent.cljc) and
# the constitutional-gate conformance suite (methods/test_charter_gates.cljc). The legacy
# Python agent twin (py/agent.py + py/test_agent.py) was pruned once the clj port reached parity.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(def nss (quote [yamabiko.cells.bogie-assembly.test-state-machine
                             yamabiko.cells.carbody-fabrication.test-state-machine
                             yamabiko.cells.interior-hvac.test-state-machine
                             yamabiko.cells.traction-electrical.test-state-machine
                             yamabiko.cells.final-assembly.test-state-machine
                             yamabiko.cells.dynamic-test.test-state-machine
                             yamabiko.cells.emissions-acoustic-audit.test-state-machine
                             yamabiko.cells.homologation-binder.test-state-machine
                             yamabiko.cells.silen-rail-review.test-state-machine
                             yamabiko.methods.test-agent
                             yamabiko.methods.test-charter-gates]))
              (apply require (quote clojure.test) nss)
              (let [r (apply clojure.test/run-tests nss)]
                (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
