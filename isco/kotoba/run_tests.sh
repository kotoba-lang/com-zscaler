#!/usr/bin/env bash
# ISCO actor — kotoba-native verification: seed integrity (bb) + cell build +
# runtime smoke (WasmExecutor via the generic kotoba-clj CLI).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
KOTOBA_DIR="${KOTOBA_DIR:-$HERE/../../../../kotoba}"
SEED="$HERE/isco-occupations.kotoba.edn"

echo "== 1. seed integrity =="
bb "$HERE/validate.clj" "$SEED"

echo "== 2. coverage worklist (gaps) =="
bb "$HERE/query.clj" "$SEED" gaps | tail -1

echo "== 3. build cell → wasm =="
"$HERE/build.sh"

echo "== 4. runtime smoke (WasmExecutor) =="
BIN="$(find "$KOTOBA_DIR/target" -maxdepth 3 -name kotoba-clj -type f | head -1)"
SNAP='[{"graph":"open-isco","subject":"2512","predicate":"isco.occupation/name","object":"Software Developers"},{"graph":"open-isco","subject":"2512","predicate":"isco.occupation/parent","object":"251"}]'
echo "-- mode 0 lookup 2512 --"
"$BIN" run "$HERE/coordinator.wasm" --ctx '{"code":"2512","mode":0}' --snapshot "$SNAP"
echo "-- mode 6 coverage ratio --"
"$BIN" run "$HERE/coordinator.wasm" --ctx '{"code":"","mode":6}' --snapshot "$SNAP"
echo "OK"
