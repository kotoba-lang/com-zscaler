#!/usr/bin/env bash
# watari — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote watari.methods.test-ingest) (quote watari.methods.test-analyze) (quote watari.methods.test-autorun) (quote watari.methods.test-charter-gates) (quote watari.methods.test-kotoba-cid) (quote watari.methods.test-pipeline-cid))(let [r (apply clojure.test/run-tests (quote [watari.methods.test-ingest watari.methods.test-analyze watari.methods.test-autorun watari.methods.test-charter-gates watari.methods.test-kotoba-cid watari.methods.test-pipeline-cid]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
