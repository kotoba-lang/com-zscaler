#!/usr/bin/env bash
# tadori threat-intel kotoba Datomic deploy.
#
# Dry run:
#   ./deploy.sh
#
# Live transact + readback verification:
#   KOTOBA_URL=http://127.0.0.1:8077 \
#   KOTOBA_SESSION_POP=<compact-eddsa-jws> \
#   TADORI_CASE_ID=case:<authorized-case> \
#   ./deploy.sh
set -euo pipefail

# The ingest leg was ported to cljc (methods/transact.cljc) per ADR-2606160842; this
# wrapper now drives `bb tadori:ingest` (no credential ⇒ DRY RUN; credential ⇒ live
# transact + read-back). The seed (kotoba/seed.threat-intel.jsonl) + schema stay here.
KOTOBA_URL="${KOTOBA_URL:-http://127.0.0.1:8077}"
GRAPH="${TADORI_GRAPH:-etzhayyim/tadori/threat-intel}"
SEED="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/seed.threat-intel.jsonl"
REPO_ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"

echo "==> tadori threat-intel Datomic deploy -> ${KOTOBA_URL} (graph ${GRAPH})"

if ! curl -fsS -m 5 "${KOTOBA_URL}/health" >/dev/null 2>&1; then
  echo "!! kotoba node not reachable at ${KOTOBA_URL} - start it with: kotoba serve" >&2
  exit 1
fi

if [[ -z "${KOTOBA_SESSION_POP:-}" && -z "${KOTOBA_TOKEN:-}" ]]; then
  echo "--> credential unset -> DRY RUN"
fi
# bb tadori:ingest reads KOTOBA_URL/KOTOBA_SESSION_POP/KOTOBA_TOKEN/TADORI_CASE_ID from env.
(cd "${REPO_ROOT}" && KOTOBA_URL="${KOTOBA_URL}" TADORI_GRAPH="${GRAPH}" \
  bb tadori:ingest "${SEED}" "${GRAPH}" "${KOTOBA_URL}")

echo "==> done"
