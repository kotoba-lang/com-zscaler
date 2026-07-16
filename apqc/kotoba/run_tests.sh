#!/usr/bin/env bash
# APQC actor — kotoba-native verification: seed integrity (bb) + cell build +
# runtime smoke (WasmExecutor via the generic kotoba-clj CLI).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
KOTOBA_DIR="${KOTOBA_DIR:-$HERE/../../../../kotoba}"
SEED="$HERE/apqc-pcf.kotoba.edn"

echo "== 1. seed integrity =="
bb "$HERE/validate.clj" "$SEED"

echo "== 2. coverage worklist (gaps) =="
bb "$HERE/query.clj" "$SEED" gaps | tail -1

echo "== 3. build cell → wasm =="
"$HERE/build.sh"

echo "== 4. runtime smoke (WasmExecutor) =="
BIN="$(find "$KOTOBA_DIR/target" -maxdepth 3 -name kotoba-clj -type f | head -1)"
SNAP='[{"graph":"open-apqc","subject":"9.0","predicate":"apqc.process/name","object":"Manage Financial Resources"},{"graph":"open-apqc","subject":"9.1.1.1","predicate":"apqc.process/parent","object":"9.1.1"}]'
echo "-- mode 0 lookup 9.0 --"
"$BIN" run "$HERE/apqc-coordinator.wasm" --ctx '{"code":"9.0","mode":0}' --snapshot "$SNAP"
echo "-- mode 6 coverage ratio --"
"$BIN" run "$HERE/apqc-coordinator.wasm" --ctx '{"code":"","mode":6}' --snapshot "$SNAP"
echo "OK"
