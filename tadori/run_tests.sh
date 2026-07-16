#!/usr/bin/env bash
# tadori 辿 — run the whole test suite with one command.
# Fully migrated to cljc (ADR-2606160842): the self-audit loop + threat-intel ingest gates
# all live in methods/*.cljc and tests/*.cljc, run by `bb test:tadori` from the repo root.
set -uo pipefail
cd "$(dirname "$0")"

if ! command -v bb >/dev/null 2>&1; then
  echo "!! bb (babashka) not found — install it to run the tadori cljc suite" >&2
  exit 1
fi

if (cd "$(git rev-parse --show-toplevel)" && bb test:tadori); then
  echo "── tadori: ALL suites green ──"
else
  echo "── tadori: FAILURES above ──"; exit 1
fi
