#!/usr/bin/env bash
# himawari 向日葵 — kotoba deploy
# ADR-2606021200 + ADR-2606015000 (PDS write path on kotoba-server)
#
# Ingests the seven com.etzhayyim.himawari.* lexicon records into a running kotoba
# node and (optionally) builds the langgraph WASM actor (7-cell manufacturing
# chain). Writes to the canonical Datom journal require a verified operator session
# Proof-of-Possession (KOTOBA_SESSION_POP) — no-server-key posture, substrate
# boundary. Without it the ingest is a DRY RUN. LLM access is Murakumo-only (G5).
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 \
#   KOTOBA_SESSION_POP=<operator-session-pop-jws> \
#     ./deploy.sh
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${HIMAWARI_GRAPH:-com.etzhayyim.himawari}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTOR_DIR="$(cd "${DEPLOY_DIR}/.." && pwd)"
ACTORS_ROOT="$(cd "${ACTOR_DIR}/.." && pwd)"          # 20-actors/ — Python path for himawari.cells.*
KOTOBA_DIR="$(cd "${ACTORS_ROOT}/../40-engine/kotoba" && pwd)"

echo "==> himawari kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  exit 1
fi

# 1. Record ingest via the PDS write path (session-PoP-gated; ADR-2606015000).
if [[ -z "${KOTOBA_SESSION_POP:-}" ]]; then
  echo "--> KOTOBA_SESSION_POP unset → DRY RUN (no writes). Set an operator session PoP to ingest."
  python3 "${DEPLOY_DIR}/ingest_records.py" --url "${KOTOBA_URL}" --graph "${GRAPH}" --dry-run
else
  echo "--> ingesting himawari records via PDS kg.ingest (operator session PoP present)"
  KOTOBA_SESSION_POP="${KOTOBA_SESSION_POP}" python3 "${DEPLOY_DIR}/ingest_records.py" \
    --url "${KOTOBA_URL}" --graph "${GRAPH}"
  echo "--> sealing hot arrangement (kotoba commit)"
  kotoba --url "${KOTOBA_URL}" --token "${KOTOBA_SESSION_POP}" commit
fi

# 2. WASM Component build: deploy/agent.cljc → deploy/agent.wasm via kotoba-clj
#    (ADR-2606222100, 2026-06-23). Supersedes the old componentize-py Python build
#    (himawari.cells.* was 1:1-ported to cljc per cell; agent.cljc's own docstring
#    calls itself "the cljc replacement for deploy/agent.py"). Run:
#      bb 20-actors/himawari/deploy/build_wasm.clj
#    or the bb.edn task `bb himawari:build-wasm`.
echo "--> WASM build: bb 20-actors/himawari/deploy/build_wasm.clj (kotoba-clj; see that file for prerequisites)"

echo "==> done"
