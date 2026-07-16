#!/usr/bin/env bash
# matsurigoto — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote matsurigoto.methods.modules.test-civil-registry) (quote matsurigoto.methods.modules.test-corp-registry) (quote matsurigoto.methods.modules.test-credential-issue) (quote matsurigoto.methods.modules.test-tax-assess) (quote matsurigoto.methods.test-datoms) (quote matsurigoto.methods.test-lexicons) (quote matsurigoto.methods.test-sign-capability) (quote matsurigoto.methods.test-standard))(let [r (apply clojure.test/run-tests (quote [matsurigoto.methods.modules.test-civil-registry matsurigoto.methods.modules.test-corp-registry matsurigoto.methods.modules.test-credential-issue matsurigoto.methods.modules.test-tax-assess matsurigoto.methods.test-datoms matsurigoto.methods.test-lexicons matsurigoto.methods.test-sign-capability matsurigoto.methods.test-standard]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
