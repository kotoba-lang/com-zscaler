#!/usr/bin/env bash
# sanae — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote sanae.cells.autonomous-weeding.test-state-machine) (quote sanae.methods.test-labor-liberation) (quote sanae.methods.test-charter-gates) (quote sanae.methods.test-labor-liberation-parity))(let [r (apply clojure.test/run-tests (quote [sanae.cells.autonomous-weeding.test-state-machine sanae.methods.test-labor-liberation sanae.methods.test-charter-gates sanae.methods.test-labor-liberation-parity]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
