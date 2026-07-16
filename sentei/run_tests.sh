#!/usr/bin/env bash
# sentei 剪定 — the pruning-engine test suite (ADR-2606072000).
# MIGRATED to Clojure (ADR-2606160842): methods/prune.py → methods/prune.cljc; the Python
# source + test were pruned once the cljc port was verified. Run the cljc suite via bb from
# the repo root (registered in bb.edn test:pywasm as sentei.methods.test-prune).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e "(require 'clojure.test 'sentei.methods.test-prune)(let [r (clojure.test/run-tests 'sentei.methods.test-prune)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))"
