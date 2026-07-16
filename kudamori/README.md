# kudamori 管守

**Sewer / confined-space in-pipe cleaning robotics — atmosphere gate + in-pipe nav + hydro-jetting.**
Tier-B actor · ADR-2606142030 · 🟡 R0 (design + sim) · Clojure-first.

kudamori ("管守" = pipe-keeper) **removes the human from the confined space** that
ADR-2606073001 §4 named as a high-remote-value GAP (toxic-gas / confined-space death). It is
the **in-pipe cleaning counterpart** that mizuho 水穂 (wastewater *treatment*,
ADR-2605263100) left open: an electric, tazuna-teleoperable in-pipe crawler that gates entry
on the atmosphere, navigates the pipe network, and hydro-jets the blockage — pressure-safe,
with the effluent handed back to mizuho.

It is a sibling of the reference Clojure-first actor kuramori (ADR-2606142000): methods are
pure Clojure (no deps) → run under both `bb` and the kotoba pywasm runtime.

## Run

```bash
bb --classpath 20-actors 20-actors/kudamori/methods/test_kudamori.clj   # 17 tests / 55 assertions
bb --classpath 20-actors -m kudamori.methods.analyze                    # → sewer-cleaning R0 report
bb --classpath 20-actors -m kudamori.methods.datom-emit                 # → kotoba EAVT Datom log
```

## What it does

| Method | Role |
|---|---|
| `atmosphere.clj` | ★ G5 confined-space entry gate (O2/H2S/CH4/CO thresholds — **raises** on unsafe) · purge-to-entry forced ventilation |
| `pipe_nav.clj`   | diameter-fit check (**raises** on no-fit) · BFS shortest route over the pipe graph · route-around blocked segments |
| `jetting.clj`    | ★ G7 jet-pressure-safe vs pipe-material rating (**raises** on over-pressure) · debris-removal estimate · water-reuse balance (G2, effluent → mizuho) |
| `analyze.clj`    | end-to-end: entry gate (purge if needed) → in-pipe nav → pressure-safe jetting → report (downstream GATED if the air can't be made safe) |
| `datom_emit.clj` | kotoba EAVT projection (`:kuda.*` GROUND + `:bond/*` DERIVED transient) |

## Gates

R0 design+sim only (G1, no-server-key) · water-reuse/eco + effluent→mizuho (G2) · no worker
surveillance (G3) · Displacement-Dividend-coupled (G4) · ★ confined-space atmosphere gate
raises (G5) · Murakumo-only (G6) · ★ no pipe over-pressure, raises (G7) · tazuna-teleoperable
(G8). See `CLAUDE.md` for the full text.

Apache 2.0 + etzhayyim Charter Compliance Rider v3.1.
