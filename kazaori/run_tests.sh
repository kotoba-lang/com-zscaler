#!/usr/bin/env bash
# kazaori — full suite (emergency engine + charter-gates), bb/clj (ADR-2606160842; py pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb test:kazaori
