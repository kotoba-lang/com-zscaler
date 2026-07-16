#!/usr/bin/env bash
# mitsuho 瑞穂 — run the whole test suite with one command.
# Fully ported py→cljc (ADR-2605261015 / 2606160842): the canonical impl + suite is
# clojure on babashka over the kotoba Datom log. Exits non-zero on any failure.
set -uo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BB_CP="20-actors:20-actors/kotodama/src:50-infra/etzhayyim-moyai-credit/src:70-tools/src:70-tools"
rc=0

run_cljc() {
  local ns="$1"
  echo "==> mitsuho [cljc] $ns"
  ( cd "$REPO_ROOT" && bb -cp "$BB_CP" -e "(require (quote clojure.test) (quote $ns))(let [r (clojure.test/run-tests (quote $ns))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "mitsuho.methods.test-agent"
run_cljc "mitsuho.methods.test-charter-gates"
run_cljc "mitsuho.cells.test-cells"

[ "$rc" -eq 0 ] && echo "── mitsuho: ALL suites green ──" || { echo "── mitsuho: FAILURES ──"; exit 1; }
