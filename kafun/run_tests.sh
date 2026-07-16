#!/usr/bin/env bash
# kafun 花粉 — clj-native test runner (babashka).
set -uo pipefail
cd "$(dirname "$0")/../.."   # → repo root (classpath base = 20-actors)
# classpath includes the shared ie-flow lib (70-tools/src) + kotoba.datom
# (20-actors/kotodama/src) for the SoS embedding suite; harmless for the rest.
CP="20-actors:70-tools/src:20-actors/kotodama/src"
SUITES=(
  "20-actors/kafun/methods/test_kafun_edn.cljc"
  "20-actors/kafun/methods/test_remediate.cljc"
  "20-actors/kafun/methods/test_kotoba.cljc"
  "20-actors/kafun/methods/test_autorun.cljc"
  "20-actors/kafun/methods/test_ie_flow.cljc"
  "20-actors/kafun/methods/test_digest.cljc"
  "20-actors/kafun/methods/test_bottleneck.cljc"
  "20-actors/kafun/methods/test_dynamics.cljc"
  "20-actors/kafun/methods/test_react_loop.cljc"
)
fail=0
for s in "${SUITES[@]}"; do
  echo "== $s =="
  if bb --classpath "$CP" "$s"; then :; else echo "FAILED: $s"; fail=1; fi
done
exit $fail
