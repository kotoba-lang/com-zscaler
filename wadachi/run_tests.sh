#!/usr/bin/env bash
# wadachi 轍 — run the autonomous-mobility cljc test suite with one command.
# Faithful port of py/agent.py → methods/agent.cljc (ADR-2605242000).
# From repo root: bash 20-actors/wadachi/run_tests.sh
# Exits non-zero on any failure (deploy-gate friendly).
set -uo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
rc=0

BB_CP="20-actors:20-actors/kotodama/src:70-tools/src:70-tools"

run_cljc() {
  local ns="$1"
  echo "==> wadachi [cljc] $ns"
  ( cd "$REPO_ROOT" && bb -cp "$BB_CP" -e \
    "(require (quote clojure.test) (quote ${ns}))(let [r (clojure.test/run-tests (quote ${ns}))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "wadachi.methods.test-agent"
run_cljc "wadachi.cells.route-planning.test-state-machine"
run_cljc "wadachi.cells.motion-control.test-state-machine"
run_cljc "wadachi.cells.obstacle-avoidance.test-state-machine"
run_cljc "wadachi.cells.safety-monitoring.test-state-machine"
run_cljc "wadachi.cells.telemetry-log.test-state-machine"

if [[ $rc -eq 0 ]]; then
  echo "==> wadachi: ALL GREEN"
else
  echo "==> wadachi: FAILURES (rc=$rc)" >&2
fi
exit $rc
