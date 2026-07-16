#!/usr/bin/env bash
# credits — clj/bb test suite (R0 first slice, 2026-07-10; G10 identity-gate
# added iteration #5, 2026-07-10).
# Self-contained: requires the namespaces directly rather than a named bb.edn task
# (avoids the #2043 broken-shim class the test-health audit tracks — see
# 70-tools/scripts/test-health/README.md). Auto-discovered by `bb test:actors` too.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote credits.methods.test-purchase) (quote credits.methods.test-spend-allocation) (quote credits.methods.test-anti-fraud) (quote credits.methods.test-ledger-rails) (quote credits.methods.test-identity-gate) (quote credits.methods.test-charter-gates))(let [r (clojure.test/run-tests (quote credits.methods.test-purchase) (quote credits.methods.test-spend-allocation) (quote credits.methods.test-anti-fraud) (quote credits.methods.test-ledger-rails) (quote credits.methods.test-identity-gate) (quote credits.methods.test-charter-gates))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
