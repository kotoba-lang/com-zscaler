#!/usr/bin/env bash
# funamori 舫 — run the cljc test suite with one command (babashka).
# Pure-Clojure (.cljc) methods; the repo pytest env is irrelevant here.
set -uo pipefail
cd "$(dirname "$0")"

if ! command -v bb >/dev/null 2>&1; then
  echo "babashka (bb) not found — install: brew install borkdude/brew/babashka"; exit 127
fi

# classpath root = 20-actors so funamori.methods.* AND the shared kuni-umi robotics
# substrate (hikari.methods.substrate, ADR-2606091800) resolve.
bb -cp .. -e "(require '[clojure.test :as t]
                       'funamori.methods.test-salinity-gradient
                       'funamori.methods.test-stack-robotics
                       'funamori.methods.test-plant
                       'funamori.cells.test-cells)
              (let [r (t/run-tests 'funamori.methods.test-salinity-gradient
                                   'funamori.methods.test-stack-robotics
                                   'funamori.methods.test-plant
                                   'funamori.cells.test-cells)]
                (when (or (pos? (:fail r)) (pos? (:error r))) (System/exit 1)))"

if [ $? -eq 0 ]; then
  echo "── funamori: ALL cljc suites green ──"
else
  echo "── funamori: FAILURES above ──"; exit 1
fi
