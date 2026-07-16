#!/usr/bin/env bash
# hodoki 解き — run the whole test suite with one command.
# Canonical Clojure (.cljc) suite, bb/clj (ADR-2606160842; py pruned).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"    # 20-actors/hodoki
REPO="$(dirname "$(dirname "$HERE")")"   # repo root (or worktree root)
cd "$REPO"

if ! command -v bb >/dev/null 2>&1; then
  echo "── hodoki: babashka (bb) not found — skipping ──"
  exit 0
fi

bb --classpath 20-actors -e '
(require (quote [clojure.test :as t]))
(def nss (quote [hodoki.methods.test-charter-gates hodoki.methods.test-agent hodoki.methods.test-agent-parity]))
(doseq [n nss] (require n))
(let [r (apply t/run-tests nss)]
  (println "── hodoki:" (:test r) "tests /" (:pass r) "assertions green,"
           (:fail r) "fail," (:error r) "error ──")
  (when (or (pos? (:fail r)) (pos? (:error r))) (System/exit 1)))
'
