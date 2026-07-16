#!/usr/bin/env bash
# business-manager — run the cljc test suite with one command.
# Faithful py→cljc port of agent.py (ADR-2606072000).
# Exits non-zero on any failure (deploy-gate friendly).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
rc=0

# business-manager directory has a hyphen; bb maps ns hyphens → underscores in path
# resolution. A symlink 20-actors/business_manager → 20-actors/business-manager is
# created once so that bb --classpath 20-actors can locate the ns correctly.
SYMLINK="$REPO_ROOT/20-actors/business_manager"
if [ ! -L "$SYMLINK" ]; then
  ln -sf business-manager "$SYMLINK"
fi

BB_CP="20-actors"
run_cljc() {
  local ns="$1"
  echo "==> business-manager [cljc] $ns"
  ( cd "$REPO_ROOT" && bb -cp "$BB_CP" -e \
    "(require (quote clojure.test) (quote $ns))(let [r (clojure.test/run-tests (quote $ns))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "business-manager.methods.test-agent"

if [[ $rc -eq 0 ]]; then
  echo "==> business-manager: ALL GREEN"
else
  echo "==> business-manager: FAILURES (rc=$rc)" >&2
fi
exit $rc
