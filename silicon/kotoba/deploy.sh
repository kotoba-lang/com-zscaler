#!/usr/bin/env bash
# silicon 珪 — kotoba deploy
# ADR-2605242500 / 2605242545 · ADR-2606021139
#
# Ingests schema-shaped seed datoms (force-reviews + equipment + a 7nm wafer lot's
# 8-step process history + one leased die) into a running kotoba node. Writes to the
# canonical Datom journal require an authorized operator session token (no-server-key,
# G7). Without KOTOBA_TOKEN the ingest is a dry-run. NOTE: real fab dispatch is Council-
# ratification + §2(a)(c) force-review gated (G1) — this deploys metadata only.
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${SILICON_GRAPH:-com.etzhayyim.silicon}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> silicon kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  exit 1
fi

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

echo "==> done (R0 metadata; real fab dispatch is Council + §2(a)(c) gated, G1)"
