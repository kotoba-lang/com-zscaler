#!/usr/bin/env bash
# Build the tsumugi-core WASM actor and report its IPFS CID (ADR-2606014500).
# Requires: rustup (wasm32-unknown-unknown target), wasm-tools, ipfs (for CID).
set -euo pipefail
cd "$(dirname "$0")/tsumugi-core"

# Use the rustup toolchain explicitly (Homebrew rustc lacks the wasm std).
TC="$(rustup show home)/toolchains/$(rustup default | awk '{print $1}')"
rustup target add wasm32-unknown-unknown >/dev/null 2>&1 || true

env RUSTC="$TC/bin/rustc" PATH="$TC/bin:$PATH" "$TC/bin/cargo" \
    build --release --target wasm32-unknown-unknown

mkdir -p dist
SRC=target/wasm32-unknown-unknown/release/tsumugi_core.wasm
wasm-tools strip "$SRC" -o dist/tsumugi-core.wasm 2>/dev/null || cp "$SRC" dist/tsumugi-core.wasm
wasm-tools validate dist/tsumugi-core.wasm

# Content address (CIDv1, raw/sha2-256). --only-hash needs no daemon; drop it to
# actually pin into the local IPFS blockstore.
CID="$(ipfs add -Q --only-hash --cid-version=1 dist/tsumugi-core.wasm)"
echo "$CID" > dist/tsumugi-core.cid
echo "tsumugi-core.wasm  $(wc -c < dist/tsumugi-core.wasm) bytes  CID=$CID"

# Refresh the loader fixtures.
cp dist/tsumugi-core.wasm ../loader/tsumugi-core.wasm
echo "If the CID changed, update :actor/wasm-cid in:"
echo "  00-contracts/schemas/actor-profile-seed.kotoba.edn"
echo "  50-infra/etzhayyim-did-web/src/registry/infra-actors.ts"
echo "then regenerate ../loader/tsumugi.did.json via publish-actor-records.mjs --actor tsumugi"
