#!/usr/bin/env bash
# Build this actor's coordinator cell (.clj) → kotoba:kais WASM Component.
#
# The Clojure→WASM compiler is a GENERIC capability of the kotoba substrate
# engine (sibling repo); this actor merely calls the `kotoba-clj` CLI. Nothing
# ISCO/APQC-specific lives in kotoba — see ADR layering (kotoba = engine,
# 20-actors/* = domain actors).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# sibling kotoba checkout (orgs/etzhayyim/kotoba); override with KOTOBA_DIR.
KOTOBA_DIR="${KOTOBA_DIR:-$HERE/../../../../kotoba}"
WIT="$KOTOBA_DIR/crates/kotoba-runtime/wit"

# the coordinator cell = the .clj that is not a tool script
CELL="$(ls "$HERE"/*.clj | grep -vE '/(validate|query)\.clj$' | head -1)"
OUT="${CELL%.clj}.wasm"

( cd "$KOTOBA_DIR" && cargo build -q -p kotoba-clj --features cli --bin kotoba-clj )
BIN="$(find "$KOTOBA_DIR/target" -maxdepth 3 -name kotoba-clj -type f | head -1)"
"$BIN" build "$CELL" -o "$OUT" --wit "$WIT"
echo "[build.sh] $(basename "$CELL") → $(basename "$OUT")"
