#!/usr/bin/env bash
# Build the shionome actor as a WASI Component-Model component with componentize-py,
# transpile with jco, and report its IPFS CID (ADR-2606072200 + ADR-2606014600).
# Requires: python3 (componentize-py via venv), node/npx (jco), ipfs, wasm-tools.
set -euo pipefail
cd "$(dirname "$0")"

VENV="${CPY_VENV:-/tmp/cpy-venv}"
if [ ! -x "$VENV/bin/componentize-py" ]; then
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet componentize-py
fi

# python sanity (offline) — regime must be risk-on, no_trade true.
python3 -c "import app, json; r=json.loads(app.compute()); assert r['regime']=='risk-on' and r['no_trade'] is True, r; print('python sanity OK:', r['regime'])"

"$VENV/bin/componentize-py" -d wit -w shionome-actor componentize app -o shionome-actor.wasm
wasm-tools validate shionome-actor.wasm
npx -y @bytecodealliance/jco@latest transpile shionome-actor.wasm -o transpiled --name shionome

CID="$(ipfs add -Q --only-hash --cid-version=1 shionome-actor.wasm)"
SIZE="$(wc -c < shionome-actor.wasm | tr -d ' ')"
printf 'shionome-actor.wasm  %s bytes  CID=%s\n' "$SIZE" "$CID"
echo "If the CID changed, update shionome-actor.meta.json."
echo "NOTE: dag-pb CID (multi-block, bundles CPython) → T2 donated-mesh tier (not browser-local)."
