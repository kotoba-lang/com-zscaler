#!/usr/bin/env bash
# yotei — clj/bb test suite (ADR-2606160842 py->clj port wave); wired into the fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote yotei.methods.test-agent))(let [r (apply clojure.test/run-tests (quote [yotei.methods.test-agent]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
