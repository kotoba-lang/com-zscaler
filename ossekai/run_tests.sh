#!/usr/bin/env bash
# ossekai — clj/bb test suite (ADR-2606160842).
# charter-gates (lexicon conformance) + the ported agent suite (cljc) are
# the AUTHORITATIVE gate. The former py/test_agent.py-derived "twin"
# (ossekai.py.test-agent, a duplicate of the already-canonical
# ossekai.methods.test-agent, kept as belt-and-suspenders confirmation) is
# gone — py/ was a byte-for-byte-structure clone of methods/agent.cljc under
# the old X.py.agent namespace, not a distinct implementation.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

BB_CP="20-actors"
rc=0

run_cljc() {
  local ns="$1"
  echo "==> ossekai [cljc] $ns"
  ( cd "$REPO_ROOT" && bb -cp "$BB_CP" -e "(require (quote clojure.test) (quote $ns))(let [r (clojure.test/run-tests (quote $ns))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "ossekai.methods.test-charter-gates"
run_cljc "ossekai.methods.test-agent"

if [[ $rc -eq 0 ]]; then
  echo "==> ossekai: ALL GREEN"
else
  echo "==> ossekai: FAILURES (rc=$rc)" >&2
fi
exit $rc
