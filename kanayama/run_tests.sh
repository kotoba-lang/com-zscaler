#!/usr/bin/env bash
# kanayama 金山 — run the whole test suite with one command.
# Canonical Clojure (.cljc) suite, bb/clj (ADR-2606160842; py pruned). Covers
# methods.test-charter-gates, py.test-agent, and all 9 cell state machines.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"    # 20-actors/kanayama
REPO="$(dirname "$(dirname "$HERE")")"   # repo root (or worktree root)
cd "$REPO"

if ! command -v bb >/dev/null 2>&1; then
  echo "── kanayama: babashka (bb) not found — skipping ──"
  exit 0
fi

bb --classpath 20-actors -e '
(require (quote [clojure.test :as t]))
(def nss (quote [kanayama.methods.test-charter-gates kanayama.methods.test-agent
                 kanayama.cells.air-emissions-audit.test-state-machine
                 kanayama.cells.cold-rolling-finishing.test-state-machine
                 kanayama.cells.dc-casting.test-state-machine
                 kanayama.cells.decoating-separation.test-state-machine
                 kanayama.cells.dross-recovery.test-state-machine
                 kanayama.cells.hot-rolling.test-state-machine
                 kanayama.cells.intake-qa.test-state-machine
                 kanayama.cells.mass-balance-binder.test-state-machine
                 kanayama.cells.melting-furnace.test-state-machine]))
(doseq [n nss] (require n))
(let [r (apply t/run-tests nss)]
  (println "── kanayama:" (:test r) "tests /" (:pass r) "assertions green,"
           (:fail r) "fail," (:error r) "error ──")
  (when (or (pos? (:fail r)) (pos? (:error r))) (System/exit 1)))
'
