# iriai 入会 — heartbeat residency (deploy)

Makes `iriai` a **resident, running** commons-heartbeat actor (the kaname/kafun/mimamori
track) — `IriaiCommonsHeartbeatCell` runs one deterministic, idempotent-by-content beat
(infra + 資金 + 管理 + 物理 twin + 運用 maintain → append to the local commons ledger) on a
schedule.

## Fleet registration (already committed)

Registered in `50-infra/cluster/murakumo/cell-runner/cells.edn`:

```
IriaiCommonsHeartbeatCell · module iriai.cell · entry fire · node judah · cron "44 * * * *" · healthz 13093
```

## Local LaunchAgent (operator step)

Residence is a launchd LaunchAgent (OS config, not a bash loop). Generated plist + logs are
**machine-local** (gitignored); only `install.clj` + the `.plist.template` are committed.

```bash
bb 20-actors/iriai/deploy/install.clj install     # render plist + load (hourly at :44)
bb 20-actors/iriai/deploy/install.clj status      # launchctl state
bb 20-actors/iriai/deploy/install.clj uninstall   # unload + remove
```

`RunAtLoad` fires one beat immediately; `StartCalendarInterval` minute 44 repeats hourly.

## Guarantees

- **No-server-key** (G6): the cell depends only on the LOCAL seed + bb; it appends to a
  local append-only kotoba commit-DAG and performs no network I/O. An unchanged assessment
  is a NO-OP (`:appended false :reason :no-change`).
- **ASSESSMENT / SIM ONLY** (G5): the beat never produces, actuates, or dispatches. The
  Murakumo-narrated digest + the live kotoba-engine bridge (ibuki-R3 pattern) + actual crew
  dispatch stay operator/Council-gated (a later R-cycle).

`bb 20-actors/iriai/cell.cljc` runs one beat directly (what the LaunchAgent invokes).
