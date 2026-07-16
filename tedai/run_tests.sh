#!/usr/bin/env bash
# tedai — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote tedai.cells.intent-plan.test-state-machine) (quote tedai.cells.test-state-machine) (quote tedai.methods.test-desktop) (quote tedai.methods.test-t2-vision-and-live-and-datom))(let [r (apply clojure.test/run-tests (quote [tedai.cells.intent-plan.test-state-machine tedai.cells.test-state-machine tedai.methods.test-desktop tedai.methods.test-t2-vision-and-live-and-datom]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
