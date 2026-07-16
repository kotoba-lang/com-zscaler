#!/bin/bash
# infra-utility-connect — deployment scaffold
# ADR-2605250900, Phase 1 R0
# Compiles cells, ingest seed data to live kotoba node, smoke-test health.

set -e

ACTOR_ID="infra-utility-connect"
GRAPH="com.etzhayyim.infrautilityconnect"
KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"

# Defaults (override via env)
SCHEMA_FILE="${SCHEMA_FILE:-$(dirname "$0")/schema.edn}"
SEED_FILE="${SEED_FILE:-$(dirname "$0")/seed.edn}"
INGEST_SCRIPT="${INGEST_SCRIPT:-$(dirname "$0")/ingest_mcp.cljc}"

echo "[infra-utility-connect] deploying to $KOTOBA_URL ..."

# 1. Compile cells (stub: WASM + Python LangGraph layers, R0 scaffold)
echo "[1/3] Cells compiled (R0 stub; no actual runtime yet)"

# 2. Ingest seed data
echo "[2/3] Ingest seed.edn → $GRAPH"
python3 "$INGEST_SCRIPT" --url "$KOTOBA_URL" --graph "$GRAPH" --dry-run
echo "      (dry-run; set KOTOBA_TOKEN for live ingest)"

# 3. Smoke test
echo "[3/3] Smoke test: GET /_meta"
echo "      (health check TBD; R0 scaffold skips live probe)"

echo "[infra-utility-connect] R0 deployment complete. Awaiting Phase 1+ activation."
exit 0
