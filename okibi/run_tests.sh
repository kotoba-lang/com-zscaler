#!/usr/bin/env bash
# 燠 okibi — clj-native test runner (babashka).
set -uo pipefail
cd "$(dirname "$0")/../.."

SUITES=(
  "20-actors/okibi/methods/test_okibi_edn.cljc"
  "20-actors/okibi/methods/test_analyze.cljc"
  "20-actors/okibi/methods/test_kotoba.cljc"
  "20-actors/okibi/methods/test_autorun.cljc"
  "20-actors/okibi/methods/test_claim.cljc"
)

fail=0
for s in "${SUITES[@]}"; do
  echo "== $s =="
  if bb --classpath 20-actors "$s"; then :; else echo "FAILED: $s"; fail=1; fi
done
exit $fail
