#!/usr/bin/env bash
# amime 網目 — clj-native test runner (babashka).
set -uo pipefail
cd "$(dirname "$0")/../.."   # → repo root (classpath base = 20-actors)
SUITES=(
  "20-actors/amime/methods/test_mesh.cljc"
  "20-actors/amime/methods/test_autorun.cljc"
)
fail=0
for s in "${SUITES[@]}"; do
  echo "== $s =="
  if bb --classpath 20-actors "$s"; then :; else echo "FAILED: $s"; fail=1; fi
done
exit $fail
