#!/usr/bin/env bash
# wakai 和会 — run the whole test suite with one command.
# The suites themselves ported py -> cljc (repo-wide convention); this just shells out to
# the root bb.edn task rather than re-implementing a runner here.
set -uo pipefail
cd "$(dirname "$0")/../.."

if bb test:wakai; then
  echo "── wakai: ALL suites green ──"
else
  echo "── wakai: FAILURES above ──"; exit 1
fi
