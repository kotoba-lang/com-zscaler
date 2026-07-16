#!/usr/bin/env bash
# busshi 物資 — clj-native test runner (babashka).
set -uo pipefail
cd "$(dirname "$0")/../.."   # → repo root (classpath base = 20-actors)

# the ie-flow embedding suite needs the shared lib (70-tools/src) + kotoba.datom
# (20-actors/kotodama/src); harmless for the rest.
CP="20-actors:70-tools/src:20-actors/kotodama/src"
SUITES=(
  "20-actors/busshi/methods/test_busshi_edn.cljc"
  "20-actors/busshi/methods/test_analyze.cljc"
  "20-actors/busshi/methods/test_kotoba.cljc"
  "20-actors/busshi/methods/test_autorun.cljc"
  "20-actors/busshi/methods/test_ie_flow.cljc"
)

fail=0
for s in "${SUITES[@]}"; do
  echo "== $s =="
  if bb --classpath "$CP" "$s"; then :; else echo "FAILED: $s"; fail=1; fi
done
exit $fail
