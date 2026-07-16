#!/usr/bin/env bash
# 朱 (ake) — run the whole test suite with one command.
# The .py mirrors this used to run standalone have been pruned (ADR-2606160842); the
# canonical Clojure (.cljc) suite (methods + cells) is now the only path.
set -uo pipefail
cd "$(dirname "$0")"

if ./run_tests_cljc.sh; then
  echo "── ake: ALL suites green ──"
else
  echo "── ake: FAILURES above ──"; exit 1
fi
