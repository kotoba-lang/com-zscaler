#!/usr/bin/env bash
# shionome T1 build — compact raw-CID browser-local wasm (tsumugi/kanae pattern,
# ADR-2606015200). Distinct from ./build.sh (the componentize-py T2 component).
set -euo pipefail
cd "$(dirname "$0")/shionome-core"
# use the rustup toolchain explicitly — the Homebrew cargo lacks the wasm target
TC="$(rustup show home)/toolchains/$(rustup default | awk '{print $1}')"
rustup target add wasm32-unknown-unknown >/dev/null 2>&1 || true
env RUSTC="$TC/bin/rustc" PATH="$TC/bin:$PATH" "$TC/bin/cargo" build --release --target wasm32-unknown-unknown
mkdir -p dist
SRC=target/wasm32-unknown-unknown/release/shionome_core.wasm
wasm-tools strip "$SRC" -o dist/shionome-core.wasm 2>/dev/null || cp "$SRC" dist/shionome-core.wasm
wasm-tools validate dist/shionome-core.wasm
# instantiate + run in node; assert the no-trade invariant on the actual output
node -e '
const fs = require("fs");
WebAssembly.instantiate(fs.readFileSync("dist/shionome-core.wasm"), {}).then(({instance}) => {
  const len = instance.exports.compute();
  const ptr = instance.exports.result_ptr();
  const out = JSON.parse(Buffer.from(instance.exports.memory.buffer, ptr, len).toString());
  if (out.no_trade !== true || out.actor !== "shionome") throw new Error("invariant: " + JSON.stringify(out));
  console.log("wasm run OK: regime=" + out.regime + " grand_total=" + out.stock_pyramid.grand_total_usd_tn + "tn no_trade=" + out.no_trade);
});'
CID="$(ipfs add -Q --only-hash --cid-version=1 dist/shionome-core.wasm)"; echo "$CID" > dist/shionome-core.cid
echo "shionome-core.wasm $(wc -c < dist/shionome-core.wasm | tr -d ' ') bytes  CID=$CID"
