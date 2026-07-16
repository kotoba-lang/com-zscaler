#!/usr/bin/env bash
# tatekata 建方 — kotoba deploy
# ADR-2605250715 · R0 scaffold
#
# Ingests schema-shaped seed datoms (project + site survey + material + progress + safety +
# settlement intent) into a running kotoba node and (optionally) builds the langgraph WASM actor
# (5-handler graph, one per phase). Writes to the canonical Datom journal require an authorized
# operator session token (no-server-key posture, G16). Without KOTOBA_TOKEN the ingest is a
# dry-run. Replaces the legacy legacy cell RuntimeError path (G14).
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${TATEKATA_GRAPH:-com.etzhayyim.tatekata}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> tatekata kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  exit 1
fi

# seed data ingest (representative R0; real project write is G17-gated)
echo "--> project/site/material/progress/safety/settlement ingest"
if [[ -z "${KOTOBA_TOKEN:-}" ]]; then
  echo "--> KOTOBA_TOKEN unset → DRY RUN (no writes). Set an operator AT-session-JWT to ingest."
  bb "${ACTOR_DIR}/kotoba/ingest_mcp.cljc" --url "${KOTOBA_URL}" --graph "${GRAPH}" --dry-run
else
  echo "--> ingesting seed datoms via MCP (operator token present)"
  KOTOBA_TOKEN="${KOTOBA_TOKEN}" bb "${ACTOR_DIR}/kotoba/ingest_mcp.cljc" \
    --url "${KOTOBA_URL}" --graph "${GRAPH}" --via mcp
  echo "--> sealing hot arrangement (kotoba commit)"
  kotoba --url "${KOTOBA_URL}" --token "${KOTOBA_TOKEN}" commit
fi

echo "--> langgraph actor build (componentize-py)"
if command -v componentize-py >/dev/null 2>&1; then
  ( cd "${ACTOR_DIR}/py" && componentize-py -w kotoba-actor componentize agent -o agent.wasm )
  echo "    built py/agent.wasm — deploy via the node's invoke.run with an operator token"
else
  echo "    (componentize-py absent — skipping wasm build)"
fi

echo "==> done"
