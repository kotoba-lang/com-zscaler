#!/usr/bin/env bash
# abaki — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote abaki.methods.test-analyze) (quote abaki.methods.test-live))(let [r (apply clojure.test/run-tests (quote [abaki.methods.test-analyze abaki.methods.test-live]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
