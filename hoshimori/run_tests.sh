#!/usr/bin/env bash
# hoshimori — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote hoshimori.methods.test-datom-emit) (quote hoshimori.methods.test-ingest) (quote hoshimori.tests.test-analyze) (quote hoshimori.tests.test-coverage) (quote hoshimori.tests.test-kotoba))(let [r (apply clojure.test/run-tests (quote [hoshimori.methods.test-datom-emit hoshimori.methods.test-ingest hoshimori.tests.test-analyze hoshimori.tests.test-coverage hoshimori.tests.test-kotoba]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
