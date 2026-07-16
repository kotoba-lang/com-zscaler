#!/usr/bin/env bash
# omise — clj/bb test suite (ADR-2606160842 py->clj port wave); wired into the fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote omise.methods.test-agent))(let [r (clojure.test/run-tests (quote omise.methods.test-agent))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
