#!/usr/bin/env bash
# matsurigoto 政 — run all test suites (ADR-2606062300). stdlib only, no deps.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/methods"

echo "== standard (COFOG + profiles) =="
python3 test_standard.py

echo ""
echo "== R1.B datom persistence layer =="
python3 test_datoms.py

echo ""
echo "== R1.C sign / authority layer =="
python3 test_sign_capability.py

echo ""
echo "== lexicon 3-place invariant drift-lock =="
python3 test_lexicons.py

echo ""
echo "== tax-assess module (conformance) =="
cd "$ROOT/methods/modules"
python3 test_tax_assess.py

echo ""
echo "== civil-registry module (conformance) =="
python3 test_civil_registry.py

echo ""
echo "== corp-registry module (conformance) =="
python3 test_corp_registry.py

echo ""
echo "== credential-issue module (conformance) =="
python3 test_credential_issue.py

echo ""
echo "== R1.A WIT contract (wasm-tools) =="
if command -v wasm-tools >/dev/null 2>&1; then
  wasm-tools component wit "$ROOT/../../00-contracts/wit/matsurigoto/egov.wit" >/dev/null \
    && echo "  WIT valid (4 worlds)"
else
  echo "  wasm-tools not found — skipped"
fi

echo ""
echo "== coverage report =="
cd "$ROOT/methods"
python3 standard.py >/dev/null && echo "  out/coverage.md written"
