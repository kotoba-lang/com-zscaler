#!/usr/bin/env bash
# iyashi — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote iyashi.methods.test-charter-gates))(let [r (apply clojure.test/run-tests (quote [iyashi.methods.test-charter-gates]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
