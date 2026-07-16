#!/usr/bin/env bash
# yamabiko 山彦 — kotoba deploy
# ADR-2605252600 · Gen-3
#
# Ingests schema-shaped seed datoms (trainsets + a worked 8-car example) into a
# running kotoba node and (optionally) builds the langgraph WASM actor (9-cell
# graph). Writes to the canonical Datom journal require an authorized operator
# session token (no-server-key posture, G21). Without KOTOBA_TOKEN the ingest is a
# dry-run. Replaces the legacy `etzhayyim build` / `etzhayyim deploy` path (G16).
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${YAMABIKO_GRAPH:-com.etzhayyim.yamabiko}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> yamabiko kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  exit 1
fi

# 0.5 trainset registry projection (representative R0; live 460-DID path is G17-gated)
echo "--> trainset registry ingest (:trainset/* datoms)"

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
