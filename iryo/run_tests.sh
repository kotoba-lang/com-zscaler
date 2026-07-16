#!/usr/bin/env bash
# iryo 医療 — run the レセプト engine test suite with one command.
# Fully ported py→cljc (ADR-2606074000): the canonical impl + suite is clojure on
# babashka over the kotoba Datom log. Exits non-zero on any failure (deploy-gate friendly).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
rc=0

# cljc (babashka) tests — py→cljc port (canonical)
BB_CP="20-actors:20-actors/kotodama/src:50-infra/etzhayyim-moyai-credit/src:70-tools/src:70-tools"
run_cljc() {
  local ns="$1"
  echo "==> iryo [cljc] $ns"
  ( cd "$REPO_ROOT" && bb -cp "$BB_CP" -e "(require (quote clojure.test) (quote $ns))(let [r (clojure.test/run-tests (quote $ns))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || rc=1
}

run_cljc "iryo.methods.test-masters"
run_cljc "iryo.methods.test-master-loader"
run_cljc "iryo.methods.test-insurance"
run_cljc "iryo.methods.test-kogaku"
run_cljc "iryo.methods.test-rezept"
run_cljc "iryo.methods.test-karte"
run_cljc "iryo.methods.test-handoff"
run_cljc "iryo.methods.test-receden"
run_cljc "iryo.methods.test-coverage"
run_cljc "iryo.methods.test-e2e"
run_cljc "iryo.methods.test-datoms"
run_cljc "iryo.methods.test-kotoba"

if [[ $rc -eq 0 ]]; then
  echo "==> iryo: ALL GREEN"
else
  echo "==> iryo: FAILURES (rc=$rc)" >&2
fi
exit $rc
