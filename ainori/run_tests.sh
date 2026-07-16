#!/usr/bin/env bash
# ainori — test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet green-check.
# Runs the cljc route suite AND the cljc agent suite (matching + cost-share + settlement
# gates G5/G10 — the no-auto-execute / member-signed-capability guards, FINDING 260617).
#
# The former third suite (py/test_agent_parity.clj, a python3-subprocess-vs-clj LIVE parity
# check) is gone: it always compared against `agent.py` via `import agent as a`, but no .py
# source exists anywhere in this repo (fully ported) — every run silently no-op'd via the
# suite's own "gracefully skip if python3 unavailable/import fails" fallback. py/agent.clj +
# py/test_agent.clj (a snapshot the suite's own docstring called "stale") were the
# now-superseded first-generation port kept only for that vacuous comparison; methods/agent.cljc
# + methods/test_agent.cljc are the canonical, actively-tested implementation.
set -uo pipefail
here="$(dirname "$0")"
fail=0

# cljc route suite (todoke route-core parity) — bb from the repo root (uses bb.edn paths)
( cd "$here/../.." && bb -e '(require (quote clojure.test) (quote ainori.methods.test-pooled-route))(let [r (apply clojure.test/run-tests (quote [ainori.methods.test-pooled-route]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))' ) || fail=1

# cljc agent suite (methods/agent.cljc port: safety-envelope + cost-share + match-pool + settlement)
( cd "$here/../.." && bb -e '(require (quote clojure.test) (quote ainori.methods.test-agent))(let [r (apply clojure.test/run-tests (quote [ainori.methods.test-agent]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))' ) || fail=1

[ "$fail" -eq 0 ] && echo "── ainori: ALL suites green ──" || { echo "── ainori: FAILURES above ──"; exit 1; }
