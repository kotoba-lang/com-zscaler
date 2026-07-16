#!/usr/bin/env bash
# sumitsubo 墨壺 — kotoba deploy
# ADR-2606033600
#
# Ingests schema-shaped seed datoms into a running kotoba node and (optionally)
# builds the langgraph WASM actor (model/draft/interop cells). Writes to the
# canonical Datom journal require an authorized operator session token
# (no-server-key posture, G6). Without KOTOBA_TOKEN the ingest is a dry-run.
#
# Usage:
#   KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<at-session-jwt> ./deploy.sh
set -euo pipefail

KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${SUMITSUBO_GRAPH:-com.etzhayyim.sumitsubo}"
ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> sumitsubo kotoba deploy → ${KOTOBA_URL} (graph ${GRAPH})"

if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} — start it with: kotoba serve" >&2
  exit 1
fi

if [[ -z "${KOTOBA_TOKEN:-}" ]]; then
  echo "--> KOTOBA_TOKEN unset → DRY RUN (schema + seed printed, no writes)."
  echo "    schema: ${ACTOR_DIR}/kotoba/schema.edn"
  echo "    seed:   ${ACTOR_DIR}/kotoba/seed.edn"
else
  echo "--> ingesting schema + seed datoms (operator token present)"
  kotoba --url "${KOTOBA_URL}" --token "${KOTOBA_TOKEN}" transact "${ACTOR_DIR}/kotoba/schema.edn"
  kotoba --url "${KOTOBA_URL}" --token "${KOTOBA_TOKEN}" transact "${ACTOR_DIR}/kotoba/seed.edn"
  echo "--> sealing hot arrangement (kotoba commit)"
  kotoba --url "${KOTOBA_URL}" --token "${KOTOBA_TOKEN}" commit
fi

echo "--> langgraph actor build (componentize-py)"
WASM_SBOM_GEN="${ACTOR_DIR}/../../70-tools/scripts/wasm-sbom/wasm_sbom_gen.py"
if command -v componentize-py >/dev/null 2>&1; then
  ( cd "${ACTOR_DIR}/py" && componentize-py -w kotoba-actor componentize agent -o agent.wasm )
  echo "    built py/agent.wasm — deploy via the node's invoke.run with an operator token"

  # ── SBOM attestation (ADR-2606036000): bind a CycloneDX SBOM to the wasm's
  #    kotoba program CID, then ingest it (operator-gated). The deployed binary
  #    carries WHAT it is made of, not just its CID + DID.
  echo "--> wasm SBOM (CycloneDX → kotoba program CID bind)"
  CZPY_VER="$(componentize-py --version 2>/dev/null | awk '{print $NF}')"
  python3 "${WASM_SBOM_GEN}" \
    --wasm "${ACTOR_DIR}/py/agent.wasm" --actor sumitsubo --world kotoba-actor \
    --program-type wasm-node \
    --requirements "${ACTOR_DIR}/py/requirements.txt" \
    ${CZPY_VER:+--built-by "componentize-py@${CZPY_VER}"} \
    --adr 2606036000 --out-dir "${ACTOR_DIR}/py"
  echo "    wrote py/agent.wasm.cdx.json + py/agent.wasm.sbom.ingest.json (keyed by program CID)"

  if [[ -n "${KOTOBA_TOKEN:-}" ]]; then
    echo "--> ingesting wasm SBOM into kotobase-kg-v1 (operator token present)"
    curl -fsS -XPOST "${KOTOBA_URL}/xrpc/com.etzhayyim.apps.kotobase.kg.ingest_batch" \
      -H "Authorization: Bearer ${KOTOBA_TOKEN}" -H 'Content-Type: application/json' \
      --data @"${ACTOR_DIR}/py/agent.wasm.sbom.ingest.json" >/dev/null \
      && echo "    SBOM ingested — query: ?c <kg/relation/wasm/componentOf> <programCid>"
    echo "    (vuln-match: python3 ../../70-tools/scripts/sbom/purl_vuln_match.py <jwt> ${KOTOBA_URL})"
  else
    echo "    KOTOBA_TOKEN unset → SBOM written to disk only (no ingest)."
  fi
else
  echo "    (componentize-py absent — skipping wasm build + SBOM)"
fi

echo "--> SDK module: build @etzhayyim/sumitsubo-cad (kami-engine-sdk re-export target)"
if command -v npm >/dev/null 2>&1; then
  ( cd "${ACTOR_DIR}/sdk" && npm run build >/dev/null 2>&1 && echo "    built sdk/dist" ) || \
    echo "    (sdk build skipped — run 'npm install && npm run build' in sdk/)"
fi

echo "==> done"
