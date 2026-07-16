#!/usr/bin/env bash
# infra-utility-connect — test suite (py->cljc port wave).
# NOTE: the actor dir is hyphenated (infra-utility-connect); bb resolves ns segments
# with hyphens->underscores, so a symlink infra_utility_connect -> infra-utility-connect
# inside 20-actors/ is required for `require` to work. This script creates it on the fly
# if absent, so it is idempotent whether run from CI or a fresh worktree.
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$here/../.." && pwd)"
fail=0

# ── ensure symlink exists so bb classpath resolution works ────────────────────
SYMLINK="$repo_root/20-actors/infra_utility_connect"
if [ ! -e "$SYMLINK" ]; then
  ln -s infra-utility-connect "$SYMLINK"
fi

# ── cljc (babashka) test suite ────────────────────────────────────────────────
echo "==> infra-utility-connect [cljc] infra-utility-connect.methods.test-agent"
( cd "$repo_root" && bb -cp 20-actors -e \
  '(require (quote clojure.test) (quote infra-utility-connect.methods.test-agent))(let [r (clojure.test/run-tests (quote infra-utility-connect.methods.test-agent))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))' ) || fail=1

for ns in \
  infra-utility-connect.cells.activation-test.test-state-machine \
  infra-utility-connect.cells.meter-install.test-state-machine \
  infra-utility-connect.cells.provider-approval.test-state-machine \
  infra-utility-connect.cells.service-request.test-state-machine; do
  echo "==> infra-utility-connect [cljc] $ns"
  ( cd "$repo_root" && bb -cp 20-actors -e \
    "(require (quote clojure.test) (quote ${ns}))(let [r (clojure.test/run-tests (quote ${ns}))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" ) || fail=1
done

if [[ $fail -eq 0 ]]; then
  echo "==> infra-utility-connect: ALL GREEN"
else
  echo "==> infra-utility-connect: FAILURES (rc=$fail)" >&2
  exit 1
fi
