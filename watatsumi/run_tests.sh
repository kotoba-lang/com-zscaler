#!/usr/bin/env bash
# watatsumi — clj/bb test suite (ADR-2605252200 py->cljc port).
# Exits non-zero on any failure (deploy-gate friendly).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
rc=0

run_cljc() {
  local ns="$1"
  echo "==> watatsumi [cljc] $ns"
  ( cd "$REPO_ROOT" && bb --classpath 20-actors -e \
    "(require (quote clojure.test) (quote $ns))(let [r (clojure.test/run-tests (quote $ns))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "watatsumi.methods.test-charter-gates"
run_cljc "watatsumi.methods.test-agent"
run_cljc "watatsumi.cells.pressure-test.test-state-machine"
run_cljc "watatsumi.cells.sea-trial.test-state-machine"
run_cljc "watatsumi.cells.class-certification-binder.test-state-machine"
run_cljc "watatsumi.cells.marine-emissions-audit.test-state-machine"
run_cljc "watatsumi.cells.hull-ring-fabrication.test-state-machine"
run_cljc "watatsumi.cells.section-assembly.test-state-machine"
run_cljc "watatsumi.cells.weld-inspection.test-state-machine"
run_cljc "watatsumi.cells.system-integration.test-state-machine"
run_cljc "watatsumi.cells.section-joining.test-state-machine"

if [[ $rc -eq 0 ]]; then
  echo "==> watatsumi: ALL GREEN"
else
  echo "==> watatsumi: FAILURES (rc=$rc)" >&2
fi
exit $rc
