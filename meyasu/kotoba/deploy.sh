#!/usr/bin/env bash
# meyasu 目安 — kotoba deploy (self-driving)
#
# Runs the agent test gate, then (when a kotoba node is reachable) notes the seed-ingest
# path. meyasu is an orchestrator: it consumes sibling actor outputs (kakaku/mitooshi) at
# runtime; live ingest + live publication are operator-gated (no-server-key). Without
# KOTOBA_TOKEN the ingest path is a dry-run.
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
#   SKIP_TESTS=1 ./deploy.sh
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${MEYASU_GRAPH:-com.etzhayyim.meyasu}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> meyasu kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

# 0. test gate — deploy is blocked on a green agent suite (deploy-autonomy invariant).
if [[ "${SKIP_TESTS:-0}" != "1" ]]; then
  echo "--> agent test gate"
  bash "${ACTOR_DIR}/run_tests.sh"
fi

# 1. health
if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  echo "   (test gate still ran above)"
  exit 1
fi

# 2. orchestration note (G6: live sibling-ingest + publication are operator-gated)
if [[ -z "${KOTOBA_TOKEN:-}" ]]; then
  echo "--> KOTOBA_TOKEN unset → DRY RUN. meyasu fuses live kakaku/mitooshi outputs only with an operator token."
else
  echo "--> operator token present — live sibling-output fusion path is R1-gated (G6)."
fi

# 3. wasm actor build
echo "--> langgraph actor build (componentize-py)"
if command -v componentize-py >/dev/null 2>&1; then
  ( cd "${ACTOR_DIR}/py" && componentize-py -w kotoba-actor componentize agent -o agent.wasm )
  echo "    built py/agent.wasm — deploy via the node's invoke.run with an operator token"
else
  echo "    (componentize-py absent — skipping wasm build)"
fi

echo "==> done"
