#!/usr/bin/env bash
# tsutae 伝え — kotoba schema + seed deploy (ADR-2605261300 R0 scaffold).
# Loads the EAVT schema and the representative seed into a local kotoba node.
# R0: returns early — the actor is a Council-gated scaffold (ADR-2605261315).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KOTOBA_ENDPOINT="${KOTOBA_ENDPOINT:-http://127.0.0.1:8077}"

echo "tsutae R0 scaffold — deploy is Council-gated (ADR-2605261315). No-op."
echo "  schema: $HERE/schema.edn"
echo "  seed:   $HERE/seed.edn"
echo "  endpoint (when activated): $KOTOBA_ENDPOINT"
exit 0

# --- activated path (post-ratification) ---
# curl -sf "$KOTOBA_ENDPOINT/xrpc/com.etzhayyim.apps.kotoba.kg.transact_schema" \
#   --data-binary @"$HERE/schema.edn"
# curl -sf "$KOTOBA_ENDPOINT/xrpc/com.etzhayyim.apps.kotoba.kg.transact" \
#   --data-binary @"$HERE/seed.edn"
