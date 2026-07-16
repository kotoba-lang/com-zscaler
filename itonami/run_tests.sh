#!/usr/bin/env bash
# itonami — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote itonami.methods.test-datom-emit) (quote itonami.tests.test-alert) (quote itonami.tests.test-analyze) (quote itonami.tests.test-digest) (quote itonami.tests.test-fleet) (quote itonami.tests.test-ingest) (quote itonami.tests.test-inspect) (quote itonami.tests.test-optimize) (quote itonami.tests.test-plan) (quote itonami.tests.test-kotoba) (quote itonami.tests.test-trend))(let [r (apply clojure.test/run-tests (quote [itonami.methods.test-datom-emit itonami.tests.test-alert itonami.tests.test-analyze itonami.tests.test-digest itonami.tests.test-fleet itonami.tests.test-ingest itonami.tests.test-inspect itonami.tests.test-optimize itonami.tests.test-plan itonami.tests.test-kotoba itonami.tests.test-trend]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
