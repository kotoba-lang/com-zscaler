#!/usr/bin/env bash
# yakushi 薬師 — kotoba deploy
# ADR-2605250500 · migration plan Phase 3
#
# Ingests schema-shaped seed datoms (Wave 1 OTC pharmaceutical reference) into a
# running kotoba node and (optionally) builds the langgraph WASM actor (6-cell
# graph). Writes to the canonical Datom journal require an authorized operator
# session token (no-server-key posture, G18). Without KOTOBA_TOKEN the ingest is a
# dry-run. Replaces the legacy Gen-2 cell path (G14).
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${YAKUSHI_GRAPH:-com.etzhayyim.yakushi}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> yakushi kotoba deploy -> ${KOTOBA_URL} (graph ${GRAPH})"

if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  exit 1
fi

# seed ingest (representative R0; real WIT attestation path is G3-gated)
echo "--> pharmaceutical manufacturing seed ingest (:rawMaterialAttestation/* + :apiSynthesisAttestation/* datoms)"
bb "${ACTOR_DIR}/kotoba/ingest_mcp.cljc" --url "${KOTOBA_URL}" --graph "${GRAPH}" \
  $([[ -z "${KOTOBA_TOKEN:-}" ]] && echo --dry-run)

if [[ -z "${KOTOBA_TOKEN:-}" ]]; then
  echo "--> KOTOBA_TOKEN unset — DRY RUN (no writes). Set an operator AT-session-JWT to ingest."
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
