#!/bin/bash
# futawa 二輪 — deployment script (kotoba WASM actor cells)
# ADR-2605261330 · R0 scaffold
# Usage: bash deploy.sh [--dry-run]

set -euo pipefail

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=1
fi

ACTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTOBA_DIR="${ACTOR_DIR}/kotoba"

echo "=== futawa actor deploy (R0 scaffold) ==="
echo "Actor dir: ${ACTOR_DIR}"
echo "Kotoba dir: ${KOTOBA_DIR}"

if [[ ${DRY_RUN} -eq 1 ]]; then
    echo "[dry-run] Validating manifest.edn"
    if [[ ! -f "${ACTOR_DIR}/manifest.edn" ]]; then
        echo "ERROR: manifest.edn not found"
        exit 1
    fi
    echo "[dry-run] Checking cells/*.edn"
    for cell_edn in "${ACTOR_DIR}/cells"/*.edn; do
        if [[ -f "${cell_edn}" ]]; then
            echo "  OK: $(basename "${cell_edn}")"
        fi
    done
    echo "[dry-run] Checking lex/*.edn"
    for lex_edn in "${ACTOR_DIR}/lex"/*.edn; do
        if [[ -f "${lex_edn}" ]]; then
            echo "  OK: $(basename "${lex_edn}")"
        fi
    done
    echo "[dry-run] Checking kotoba schema.edn"
    if [[ -f "${KOTOBA_DIR}/schema.edn" ]]; then
        echo "  OK: schema.edn"
    fi
    echo "[dry-run] Checking Python cells"
    for py_file in "${ACTOR_DIR}/py"/*.py; do
        if [[ -f "${py_file}" ]]; then
            echo "  OK: $(basename "${py_file}")"
        fi
    done
    echo "[dry-run] Deploy complete (no writes)"
    exit 0
fi

echo "R0 scaffold: no physical deployment yet."
echo "To activate for R1, ADR-2605261330 must pass Council Lv6+ vote."
exit 0
