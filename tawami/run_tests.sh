#!/usr/bin/env bash
# 撓 tawami — clj-native test runner (babashka).
set -uo pipefail
cd "$(dirname "$0")/../.."   # → repo root (classpath base = 20-actors)

SUITES=(
  "20-actors/tawami/methods/test_tawami_edn.cljc"
  "20-actors/tawami/methods/test_analyze.cljc"
  "20-actors/tawami/methods/test_kotoba.cljc"
  "20-actors/tawami/methods/test_autorun.cljc"
  "20-actors/tawami/methods/test_claim.cljc"
)

fail=0
for s in "${SUITES[@]}"; do
  echo "== $s =="
  if bb --classpath 20-actors "$s"; then :; else echo "FAILED: $s"; fail=1; fi
done
exit $fail
