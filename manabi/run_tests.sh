#!/usr/bin/env bash
# manabi 学び — run all test suites with one command.
# Exits non-zero on any failure (deploy-gate friendly).
set -uo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BB_CP="20-actors"
rc=0

run_cljc() {
  local ns="$1"
  echo "==> manabi [cljc] $ns"
  ( cd "$REPO_ROOT" && bb -cp "$BB_CP" -e "(require (quote clojure.test) (quote $ns))(let [r (clojure.test/run-tests (quote $ns))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "manabi.methods.test-charter-gates"
run_cljc "manabi.methods.test-agent"
run_cljc "manabi.cells.cert-prep.test-cert-prep"

if [[ $rc -eq 0 ]]; then
  echo "==> manabi: ALL GREEN"
else
  echo "==> manabi: FAILURES (rc=$rc)" >&2
fi
exit $rc
