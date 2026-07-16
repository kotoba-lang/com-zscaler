#!/usr/bin/env bash
# sarutahiko — full test suite: charter-gate conformance + agent handler tests.
# ADR-2606160842 (charter gates, existing) + clj-port-pilot (agent.cljc / test_agent.cljc).
# Exits non-zero on any failure (deploy-gate friendly).
set -uo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BB_CP="20-actors"
rc=0

run_cljc() {
  local ns="$1"
  echo "==> sarutahiko [cljc] $ns"
  ( cd "$REPO_ROOT" && bb -cp "$BB_CP" -e "(require (quote clojure.test) (quote $ns))(let [r (clojure.test/run-tests (quote $ns))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "sarutahiko.methods.test-charter-gates"
run_cljc "sarutahiko.methods.test-agent"
run_cljc "sarutahiko.cells.frame-fabrication.test-state-machine"
run_cljc "sarutahiko.cells.cab-body-forming.test-state-machine"
run_cljc "sarutahiko.cells.powertrain-assembly.test-state-machine"
run_cljc "sarutahiko.cells.electrical-integration.test-state-machine"

if [[ $rc -eq 0 ]]; then
  echo "==> sarutahiko: ALL GREEN"
else
  echo "==> sarutahiko: FAILURES (rc=$rc)" >&2
fi
exit $rc
