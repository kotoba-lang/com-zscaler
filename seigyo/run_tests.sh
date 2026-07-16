#!/usr/bin/env bash
# seigyo 制御 — run the whole test suite with one command.
# Canonical Clojure (.cljc) suite, bb/clj (ADR-2606160842; py pruned).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"    # 20-actors/seigyo
REPO="$(dirname "$(dirname "$HERE")")"   # repo root (or worktree root)
cd "$REPO"

if ! command -v bb >/dev/null 2>&1; then
  echo "── seigyo: babashka (bb) not found — skipping ──"
  exit 0
fi

bb --classpath 20-actors -e '
(require (quote [clojure.test :as t]))
(require (quote seigyo.methods.test-charter-gates))
(let [r (t/run-tests (quote seigyo.methods.test-charter-gates))]
  (println "── seigyo:" (:test r) "tests /" (:pass r) "assertions green,"
           (:fail r) "fail," (:error r) "error ──")
  (when (or (pos? (:fail r)) (pos? (:error r))) (System/exit 1)))
'
