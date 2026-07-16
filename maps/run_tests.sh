#!/usr/bin/env bash
# maps — clj/bb test suite (ADR-2606160842 py->clj port wave; Python methods pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb test:maps
