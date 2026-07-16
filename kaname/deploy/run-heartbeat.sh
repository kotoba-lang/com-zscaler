#!/bin/bash
# kaname 要 — production heartbeat runner (ADR-2606172100; deploy 実運用).
# One beat: perceive the cross-domain world model → compute the 要 → persist to the local kotoba
# commit-DAG → push to the LIVE kotoba engine (--bridge). Idempotent-by-content (no-op when the
# world is unchanged) + FAIL-OPEN (engine down → the beat still completes locally).
#
# Constitutional notes:
#   - the operator DID is read DYNAMICALLY from the running kotoba node's own env (KOTOBA_AGENT_DID):
#     the loopback "node persists on the actor's behalf" path (ibuki-identical). It is a PUBLIC
#     identifier, never a secret; if the node is down, the bridge fail-opens and the beat is local-only.
#   - no platform signing key is held (the bearer is unsigned; loopback trust boundary).
set -uo pipefail

REPO="${KANAME_REPO:?set KANAME_REPO to the repo root}"
BB="${KANAME_BB:-/opt/homebrew/bin/bb}"

# Resolve the node operator DID from the running kotoba-server's env (public; loopback path).
PID="$(pgrep -f kotoba-server | head -1 || true)"
if [ -n "${PID:-}" ]; then
  DID="$(ps eww "$PID" 2>/dev/null | tr ' ' '\n' | grep '^KOTOBA_AGENT_DID=' | head -1 | cut -d= -f2- || true)"
  [ -n "${DID:-}" ] && export KANAME_KOTOBA_OPERATOR_DID="$DID"
fi

cd "$REPO" || exit 1
echo "[$(date '+%Y-%m-%dT%H:%M:%S')] kaname heartbeat (bridge=$([ -n "${KANAME_KOTOBA_OPERATOR_DID:-}" ] && echo on || echo fail-open))"
exec "$BB" 20-actors/kaname/autorun.cljc "$REPO/20-actors" --bridge
