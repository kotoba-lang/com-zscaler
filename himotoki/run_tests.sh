#!/usr/bin/env bash
# himotoki — clj/bb test suite (ADR-2606160842 py->clj port wave); ALL test namespaces, fleet green-check.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote himotoki.methods.test-charter-gates) (quote himotoki.methods.test-request))(let [r (apply clojure.test/run-tests (quote [himotoki.methods.test-charter-gates himotoki.methods.test-request]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
