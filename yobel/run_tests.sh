#!/usr/bin/env bash
# yobel — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
# eth-crypto.* is an external git dep (com-junkawasaki/eth-crypto-clj); its own gate test
# runs in that repo's CI, not here. yobel tests exercise it via the concrete-ports tests.
# The tests_integration/test_web3_roundtrip ns is an OPERATOR-RUN EVM harness (bb,
# anvil); without foundry's `anvil` on PATH it SKIPS GRACEFULLY (zero assertions,
# stays green) — its ABI-selector + sign-tx-legacy + EIP-712 legs run unconditionally.
exec bb -e '(require (quote clojure.test) (quote yobel.cells.audit-witness.tests.test-cell) (quote yobel.cells.creditor-enrollment.tests.test-cell) (quote yobel.cells.debtor-enrollment.tests.test-cell) (quote yobel.cells.release-settlement.tests.test-cell) (quote yobel.cells.rite-declaration.tests.test-cell) (quote yobel.tests.test-orchestrator) (quote yobel.concrete-ports.tests.test-eip712-erc725) (quote yobel.concrete-ports.tests.test-web3-ports) (quote yobel.tests-integration.test-web3-roundtrip))(let [r (apply clojure.test/run-tests (quote [yobel.cells.audit-witness.tests.test-cell yobel.cells.creditor-enrollment.tests.test-cell yobel.cells.debtor-enrollment.tests.test-cell yobel.cells.release-settlement.tests.test-cell yobel.cells.rite-declaration.tests.test-cell yobel.tests.test-orchestrator yobel.concrete-ports.tests.test-eip712-erc725 yobel.concrete-ports.tests.test-web3-ports yobel.tests-integration.test-web3-roundtrip]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
