#!/usr/bin/env bash
# danjo — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote danjo.methods.test-analyze) (quote danjo.methods.test-charter-gates) (quote danjo.methods.test-autorun) )(let [r (clojure.test/run-tests (quote danjo.methods.test-analyze) (quote danjo.methods.test-charter-gates) (quote danjo.methods.test-autorun) )](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
