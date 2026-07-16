#!/usr/bin/env bash
# watatsuna — clj/bb test suite (ADR-2606160842 py->clj port wave; Python methods pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote watatsuna.methods.test-analyze) (quote watatsuna.methods.test-autorun) (quote watatsuna.methods.test-ingest) (quote watatsuna.methods.test-plan) (quote watatsuna.methods.test-kotoba-cid) (quote watatsuna.methods.test-pipeline-cid) (quote watatsuna.viz.test-build-viz-data))(let [r (apply clojure.test/run-tests (quote [watatsuna.methods.test-analyze watatsuna.methods.test-autorun watatsuna.methods.test-ingest watatsuna.methods.test-plan watatsuna.methods.test-kotoba-cid watatsuna.methods.test-pipeline-cid watatsuna.viz.test-build-viz-data]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
