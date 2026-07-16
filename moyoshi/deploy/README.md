# moyoshi 催し — heartbeat deploy (ADR-2606272100 R3, 実運用)

Per-user macOS **LaunchAgent** that runs the convening heartbeat hourly at **:39** against the
LOCAL kotoba engine. clj/bb-native (no shell, per ADR-2606072802).

```bash
bb 20-actors/moyoshi/deploy/install.clj install     # render plist, load, kickstart one beat
bb 20-actors/moyoshi/deploy/install.clj status      # agent state + last log
bb 20-actors/moyoshi/deploy/install.clj uninstall   # bootout + remove plist
```

## What one beat does (`run-heartbeat.clj` → `autorun --bridge`)
ingest a committed kizuna 絆 readout → design a gathering → govern (G1..G6) → record a pending
gathering → settle any gathering whose decay window elapsed (against kizuna's now-graph) → persist
to the local kotoba commit-DAG → **push to the LIVE kotoba engine** (`MOYOSHI_KOTOBA_LIVE=1`).

- **Operator DID** is read DYNAMICALLY from the running `kotoba-server`'s own env
  (`KOTOBA_AGENT_DID`) — the loopback "node persists on the actor's behalf" path. PUBLIC
  identifier, never a secret. Absent → the bridge **fail-opens** and the beat is local-only.
- **no-server-key**: no platform signing key is held (the bearer is unsigned; loopback trust
  boundary). The local commit-DAG is content-addressed, verify-chain tamper-evident, resume-safe.

## Notes
- **Must run local** — depends on the LOCAL `:8077` kotoba node + the mirror files + `bb`.
- Run `install` from the **merged checkout** (not a temporary git worktree — that path is ephemeral).
- The generated plist + log live machine-local (not committed).
- Fleet alternative: `MoyoshiHeartbeatCell` (`cell.cljc`) on the kotodama cell-runner
  (node reuben, cron `39 * * * *`, healthz 13092) — the cell runs LOCAL-only (no bridge).
