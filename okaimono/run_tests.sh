#!/usr/bin/env bash
# okaimono 御買物 — bb/clj test suite (ADR-2606160842 py->clj port wave; py agent pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote okaimono.methods.test-agent))(let [r (clojure.test/run-tests (quote okaimono.methods.test-agent))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
