#!/usr/bin/env bash
# mitooshi 見通し — run the whole cljc test suite with one command.
# The Python methods/tests were pruned once fully ported to .cljc (clj-port migration,
# ADR-2606160842); the cljc namespaces are the SSoT. Runs them via babashka from the repo root
# (bb.edn :paths includes 20-actors). Each ns is required then run; exits non-zero on any failure.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

NSS=(
  mitooshi.methods.test-analyze
  mitooshi.methods.test-backtest
  mitooshi.methods.test-bridge
  mitooshi.methods.test-bridge-kakaku
  mitooshi.methods.test-calibrate
  mitooshi.methods.test-clearpath
  mitooshi.methods.test-forecast
  mitooshi.methods.test-forecast-parity
  mitooshi.methods.test-forecast-quantile
  mitooshi.methods.test-horizon
  mitooshi.methods.test-ingest
  mitooshi.methods.test-kakaku-forecast-e2e
  mitooshi.methods.test-persist
  mitooshi.methods.test-promote
  mitooshi.methods.test-score
  mitooshi.methods.test-social
  mitooshi.methods.test-synthesize
  mitooshi.cells.online-update.test-state-machine
  mitooshi.cells.test-state-machine
  mitooshi.test-learning-loop
  mitooshi.test-continual-learning
  mitooshi.viz.test-build-forecast-viz
)

NAMES="${NSS[*]}"

bb -e "(def nss '($NAMES))
       (apply require nss)
       (let [r (apply clojure.test/run-tests nss)]
         (println \"── mitooshi:\" (select-keys r [:test :pass :fail :error]))
         (System/exit (if (or (pos? (:fail r)) (pos? (:error r))) 1 0)))"
