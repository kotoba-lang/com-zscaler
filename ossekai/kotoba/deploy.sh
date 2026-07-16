#!/usr/bin/env bash
# ossekai 御節介 — kotoba deploy (self-driving)
# ADR-2605264000
#
# Runs the agent test gate, then (when a kotoba node is reachable) ingests the actor's
# seed datoms. Writes to the canonical Datom journal require an authorized operator session
# token (no-server-key posture). Without KOTOBA_TOKEN the ingest is a dry-run. Live archive
# ingest and live AT-Proto broadcast stay operator-gated (not performed here).
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
#   SKIP_TESTS=1 ./deploy.sh        # skip the test gate (not recommended)
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${OSSEKAI_GRAPH:-com.etzhayyim.ossekai}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> ossekai kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

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

# 2. seed ingest (G11: live writes need an operator token)
if [[ -z "${KOTOBA_TOKEN:-}" ]]; then
  echo "--> KOTOBA_TOKEN unset → DRY RUN (no writes). Set an operator AT-session-JWT to ingest."
else
  echo "--> operator token present — seed ingest path is R1-gated (wire ingest_mcp.py before use, G11)."
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
