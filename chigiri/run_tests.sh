#!/usr/bin/env bash
# chigiri — charter-gate suite, bb/clj (ADR-2606160842 py→clj port wave; py pruned).
# methods.dispute-mediation (iteration #9) added: chigiri's first real computed-logic
# method (G10 Mediation-First Rule), following the credits G10 identity-gate /
# musubi ceremony-recognition-resolver self-contained run_tests.sh precedent.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote chigiri.methods.test-charter-gates) (quote chigiri.methods.test-datom-kotoba) (quote chigiri.methods.test-dispute-mediation))(let [r (apply clojure.test/run-tests (quote [chigiri.methods.test-charter-gates chigiri.methods.test-datom-kotoba chigiri.methods.test-dispute-mediation]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
