#!/usr/bin/env bash
# musubi — charter-gate + methods suite, bb/clj (ADR-2606160842; py pruned).
# methods.ceremony-recognition-resolver (iteration #8) added: musubi's first real
# computed-logic method, following the credits G10 identity-gate self-contained
# run_tests.sh precedent.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote musubi.methods.test-charter-gates) (quote musubi.methods.test-ceremony-recognition-resolver))(let [r (clojure.test/run-tests (quote musubi.methods.test-charter-gates) (quote musubi.methods.test-ceremony-recognition-resolver))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
