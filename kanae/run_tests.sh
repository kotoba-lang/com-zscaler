#!/usr/bin/env bash
# kanae — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote kanae.methods.test-charter-gates) (quote kanae.methods.test-pipeline) )(let [r (clojure.test/run-tests (quote kanae.methods.test-charter-gates) (quote kanae.methods.test-pipeline) )](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
