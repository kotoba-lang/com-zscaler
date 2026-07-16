#!/usr/bin/env bash
# Build the watatsuna actor as a WASI Component-Model component with
# componentize-py, transpile with jco, and report its IPFS CID (ADR-2606014600).
# Requires: python3 (for componentize-py via venv), node/npx (jco), ipfs, wasm-tools.
set -euo pipefail
cd "$(dirname "$0")"

# componentize-py in an isolated venv (PEP-668 environments block global pip).
VENV="${CPY_VENV:-/tmp/cpy-venv}"
if [ ! -x "$VENV/bin/componentize-py" ]; then
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet componentize-py
fi

# python sanity (offline) — top chokepoint must be Malacca.
python3 -c "import app, json; r=json.loads(app.compute()); assert r['top'][0]['chokepoint']=='malacca', r['top'][0]; print('python sanity OK:', r['top'][0])"

# build the component
"$VENV/bin/componentize-py" -d wit -w watatsuna-actor componentize app -o watatsuna-actor.wasm
wasm-tools validate watatsuna-actor.wasm

# transpile for a JS host (node / browser-with-bundler / mesh)
npx -y @bytecodealliance/jco@latest transpile watatsuna-actor.wasm -o transpiled --name watatsuna

CID="$(ipfs add -Q --only-hash --cid-version=1 watatsuna-actor.wasm)"
SIZE="$(wc -c < watatsuna-actor.wasm | tr -d ' ')"
printf 'watatsuna-actor.wasm  %s bytes (%.1f MB)  CID=%s\n' "$SIZE" "$(echo "$SIZE/1048576" | bc -l)" "$CID"
echo "If the CID changed, update watatsuna-actor.meta.json + :actor/wasm-cid in"
echo "  00-contracts/schemas/actor-profile-seed.kotoba.edn + infra-actors.ts,"
echo "then regenerate watatsuna's did.json."
echo "NOTE: dag-pb CID (multi-block) → T2 donated-mesh tier (not browser-local)."
