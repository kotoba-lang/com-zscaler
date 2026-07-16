#!/usr/bin/env bash
# meyasu 目安 — run the whole cljc test suite with one command.
# The Python agent/viz/tests were pruned once fully ported to .cljc (clj-port migration,
# ADR-2606160842); the cljc namespaces are the SSoT. Runs them via babashka from the repo root
# (bb.edn :paths includes 20-actors). Exits non-zero on any failure (deploy-gate friendly).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

bb -e "(def nss '(meyasu.methods.test-agent meyasu.methods.test-autorun meyasu.methods.test-pipeline-cid meyasu.methods.test-cohort-e2e meyasu.viz.test-build-viz))
       (apply require nss)
       (let [r (apply clojure.test/run-tests nss)]
         (println \"==> meyasu:\" (select-keys r [:test :pass :fail :error]))
         (System/exit (if (or (pos? (:fail r)) (pos? (:error r))) 1 0)))"
