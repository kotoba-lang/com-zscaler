#!/usr/bin/env bash
# haraedo 祓戸 — kotoba deploy
# ADR-2606010200
#
# Ingests schema-shaped seed datoms into a running kotoba node and (optionally)
# builds + serves the langgraph WASM actor. The kotoba node enforces OPERATOR
# AUTH on writes (verified 2026-06-01 against the live :8077 node: `quad put`
# → 401, MCP tools/call → "requires Authorization: Bearer <AT-session-JWT>").
# This is the correct no-server-key posture (ADR substrate boundary): writing to
# the canonical Datom journal requires an authorized operator session token.
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
#
# Without KOTOBA_TOKEN the ingest is a dry-run (parse + datom count only).
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${HARAEDO_GRAPH:-com.etzhayyim.haraedo}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> haraedo kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

# 0. node reachable?
if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  exit 1
fi

# 1. ingest seed.edn → datoms (schema.edn is the design-time attribute registry;
#    this kotoba build's compatibility layer asserts datoms directly)
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

# 2. langgraph WASM actor (intake + dispatch) — needs componentize-py + an
#    operator token; LLM nodes additionally need Murakumo LiteLLM at 127.0.0.1:4000
echo "--> langgraph actor build (componentize-py)"
if command -v componentize-py >/dev/null 2>&1; then
  ( cd "${ACTOR_DIR}/py" && componentize-py -w kotoba-actor componentize agent -o agent.wasm )
  echo "    built py/agent.wasm — deploy via the node's invoke.run with an operator token"
else
  echo "    (componentize-py absent — skipping wasm build)"
fi

echo "==> done"
