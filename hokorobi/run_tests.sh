#!/usr/bin/env bash
# hokorobi — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote hokorobi.methods.test-datom-emit) (quote hokorobi.tests.test-analyze) (quote hokorobi.tests.test-coverage) (quote hokorobi.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [hokorobi.methods.test-datom-emit hokorobi.tests.test-analyze hokorobi.tests.test-coverage hokorobi.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
