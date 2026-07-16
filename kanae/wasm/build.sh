#!/usr/bin/env bash
# Build the kanae-core T1 WASM actor + report its IPFS CID (ADR-2606015200).
set -euo pipefail
cd "$(dirname "$0")/kanae-core"
TC="$(rustup show home)/toolchains/$(rustup default | awk '{print $1}')"
rustup target add wasm32-unknown-unknown >/dev/null 2>&1 || true
env RUSTC="$TC/bin/rustc" PATH="$TC/bin:$PATH" "$TC/bin/cargo" build --release --target wasm32-unknown-unknown
mkdir -p dist
SRC=target/wasm32-unknown-unknown/release/kanae_core.wasm
wasm-tools strip "$SRC" -o dist/kanae-core.wasm 2>/dev/null || cp "$SRC" dist/kanae-core.wasm
wasm-tools validate dist/kanae-core.wasm
CID="$(ipfs add -Q --only-hash --cid-version=1 dist/kanae-core.wasm)"; echo "$CID" > dist/kanae-core.cid
cp dist/kanae-core.wasm ../loader/kanae-core.wasm
echo "kanae-core.wasm $(wc -c < dist/kanae-core.wasm) bytes  CID=$CID"
