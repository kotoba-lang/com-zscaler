#!/usr/bin/env bash
# jinushi 地主 — POLITE, EXPLICIT WDQS refresh of the national-park acquisition snapshot.
#
# WDQS / Wikimedia load discipline (operator directive 2026-06-16 — "wdqs に負担をかけない"):
#   - This is NOT run by the loop. The 30-min loop re-ingests the COMMITTED snapshot
#     (data/acquired/wikidata-national-parks.kotoba.edn) with ZERO network I/O. Only a human
#     operator runs this, and only occasionally, to refresh the snapshot.
#   - ONE small LIMITed query per run. No retry loop. Respect the WDQS 60 s server cap by
#     keeping the query cheap (small LIMIT); if it times out, LOWER the limit — never hammer.
#   - Descriptive User-Agent WITH a contact address (Wikimedia UA policy).
#   - A courtesy sleep before the request; --max-time bounds the single attempt.
#   - 15-minute local result cache is honoured by reusing the snapshot; do not bypass it.
#
# Usage: methods/fetch_wdqs.sh [LIMIT]   (default LIMIT=400 — keep it modest)
set -euo pipefail

LIMIT="${1:-400}"
UA='etzhayyim-jinushi/0.1 (https://etzhayyim.com; land-acquisition commons research; jun784@gmail.com)'
# raw lands in the repo DATA LAYER (80-data/jinushi-land), ingested via the datalad substrate
# (ADR-2605241500); the actor processes it later. methods → jinushi → 20-actors → repo root.
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$ROOT/80-data/jinushi-land/wikidata-national-parks.raw.json"
mkdir -p "$ROOT/80-data/jinushi-land"

if [ "$LIMIT" -gt 800 ]; then
  echo "refusing LIMIT > 800 — keep WDQS queries cheap (be a good citizen)" >&2
  exit 2
fi

read -r -d '' QUERY <<SPARQL || true
SELECT ?p ?area ?unit ?cc WHERE {
  ?p wdt:P31 wd:Q46169 ; p:P2046 ?st ; wdt:P17 ?country .
  ?st psv:P2046 ?qn . ?qn wikibase:quantityAmount ?area ; wikibase:quantityUnit ?u .
  BIND(STRAFTER(STR(?u),"entity/") AS ?unit)
  ?country wdt:P297 ?cc .
} LIMIT ${LIMIT}
SPARQL

echo "courtesy pause (3s) before the single WDQS request…" >&2
sleep 3

echo "GET https://query.wikidata.org/sparql  (LIMIT=${LIMIT}, one attempt, --max-time 100)" >&2
curl -sS --max-time 100 -G 'https://query.wikidata.org/sparql' \
  --data-urlencode "query=${QUERY}" \
  -H 'Accept: application/sparql-results+json' \
  -A "$UA" \
  -o "$OUT"

echo "wrote $OUT ($(wc -c < "$OUT") bytes)." >&2
echo "Next: normalize → data/acquired/wikidata-national-parks.kotoba.edn (see CLAUDE.md), then commit the snapshot." >&2
echo "Do NOT re-run in a loop. One refresh is enough; the committed snapshot serves the loop." >&2
