#!/usr/bin/env bash
# 樋 toi — clj-native test runner (babashka).
set -uo pipefail
cd "$(dirname "$0")/../.."

SUITES=(
  "20-actors/toi/methods/test_toi_edn.cljc"
  "20-actors/toi/methods/test_analyze.cljc"
  "20-actors/toi/methods/test_kotoba.cljc"
  "20-actors/toi/methods/test_autorun.cljc"
  "20-actors/toi/methods/test_claim.cljc"
)

fail=0
for s in "${SUITES[@]}"; do
  echo "== $s =="
  if bb --classpath 20-actors "$s"; then :; else echo "FAILED: $s"; fail=1; fi
done
exit $fail
