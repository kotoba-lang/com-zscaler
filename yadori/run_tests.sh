#!/usr/bin/env bash
# yadori — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote yadori.cells.availability-check.test-state-machine) (quote yadori.cells.reservation.test-state-machine) (quote yadori.methods.test-availability))(let [r (apply clojure.test/run-tests (quote [yadori.cells.availability-check.test-state-machine yadori.cells.reservation.test-state-machine yadori.methods.test-availability]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
