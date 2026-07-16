#!/usr/bin/env bash
# uchiwake 内訳 — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned). ADR-2606081800.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote uchiwake.tests.test-off-adapter) (quote uchiwake.tests.test-uchiwake) (quote uchiwake.tests.test-autorun) (quote uchiwake.methods.test-crosscheck) (quote uchiwake.methods.test-bridge) (quote uchiwake.methods.test-py-round))(let [r (clojure.test/run-tests (quote uchiwake.tests.test-off-adapter) (quote uchiwake.tests.test-uchiwake) (quote uchiwake.tests.test-autorun) (quote uchiwake.methods.test-crosscheck) (quote uchiwake.methods.test-bridge) (quote uchiwake.methods.test-py-round))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
