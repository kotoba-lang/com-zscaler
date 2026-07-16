#!/usr/bin/env bash
# meisai — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote meisai.methods.test-ingest) (quote meisai.methods.test-autorun) (quote meisai.methods.test-kotoba) (quote meisai.methods.test-sources) (quote meisai.methods.test-recurring) (quote meisai.methods.test-fx) )(let [r (clojure.test/run-tests (quote meisai.methods.test-ingest) (quote meisai.methods.test-autorun) (quote meisai.methods.test-kotoba) (quote meisai.methods.test-sources) (quote meisai.methods.test-recurring) (quote meisai.methods.test-fx) )](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
