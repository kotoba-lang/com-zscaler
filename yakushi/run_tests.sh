#!/usr/bin/env bash
# yakushi — clj/bb test suite (ADR-2606160842 py->clj port wave); wired into the fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote yakushi.methods.test-charter-gates) (quote yakushi.methods.test-agent))(let [r (clojure.test/run-tests (quote yakushi.methods.test-charter-gates) (quote yakushi.methods.test-agent))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
