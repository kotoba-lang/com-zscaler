# kaname 要 — production deployment (実運用)

The kaname heartbeat runs on the **local machine** (it depends on the LOCAL kotoba engine `:8077`,
the LOCAL committed mirror outputs, and `bb`), so it deploys as a per-user **macOS LaunchAgent**, not
as a remote cell. (The fleet `KanameHeartbeatCell` in `cells.edn`, node `naphtali`, is the eventual
cell-runner home; this LaunchAgent is the operator-run deployment until the fleet runner is live.)

## What it does, on a schedule

Hourly at **:53** (matching the cell-runner cron registration), one `bb autorun … --bridge` beat:

1. **世界認識** — join every committed mirror (chie/tsumugi/inochi/hokorobi/shiori/web) into the
   cross-domain world model.
2. compute the **要** (R1 betweenness/ΔΦ), route to OPENING, build the おせっかい proposal.
3. **persist** the readout to the local kotoba commit-DAG (idempotent-by-content; no append when the
   world is unchanged).
4. **bridge** — push the commit-DAG to the **LIVE kotoba engine** (`:8077`). FAIL-OPEN: engine down /
   operator DID absent → the beat still completes locally.

Idempotent on both legs: a beat with an unchanged world model is `appended=false` + `bridge pushed=0`.

## Install / manage

```bash
bb 20-actors/kaname/deploy/install.clj install     # render plist → ~/Library/LaunchAgents, load, kickstart once
bb 20-actors/kaname/deploy/install.clj status      # agent state + tail the log
bb 20-actors/kaname/deploy/install.clj uninstall   # bootout + remove the plist
```

`install.clj` is a babashka script (`babashka.fs` + `babashka.process`) — clj-native deploy, matching
the actor. (The launchd-invoked runner stays `run-heartbeat.sh` — bash is the natural fit for the
`pgrep`/`ps` node-DID resolution it does, and it is what the plist's `ProgramArguments` launches.)

- **Label**: `com.etzhayyim.kaname.heartbeat`
- **Log**: `~/Library/Logs/kaname-heartbeat.log`
- **Operator DID**: read DYNAMICALLY from the running kotoba node's env (`KOTOBA_AGENT_DID`) — the
  loopback "node persists on the actor's behalf" path (ibuki-identical). A PUBLIC identifier, never a
  secret; no signing key is held (the bearer is unsigned, loopback trust boundary).

## Constitutional / safety

- The bridge is the only outward action and it targets the fleet allowlist only (loopback + EVO-X2
  LAN, ADR-2605215000); any other endpoint throws before I/O.
- `data/persisted/` (the commit-DAG) is gitignored runtime state; the LaunchAgent plist + log are
  machine-local (not committed).
- The cron **cell** (`kaname.cell/fire`) stays local-only; `--bridge` (this LaunchAgent) is the
  operator-run live leg — a deliberate G7 gate.

## ⚠ Worktree caveat

`install.clj` resolves the repo root from its own location. If installed from a temporary git
worktree, that path is **ephemeral** — once kaname merges to `main`, **re-run
`bb install.clj install` from the merged checkout** so the agent repoints to the stable path (and
`uninstall` the old one).
