#!/usr/bin/env bash
# kizashi — charter-gate suite, bb/clj (ADR-2606160842; py pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote kizashi.methods.test-charter-gates))(let [r (clojure.test/run-tests (quote kizashi.methods.test-charter-gates))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
