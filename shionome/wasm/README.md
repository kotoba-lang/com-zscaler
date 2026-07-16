# 潮目 shionome — kotoba-WASM actor (componentize-py, T2 donated-mesh)

The **executable** face of the shionome analyzer, built as a **WASI Component-Model component**
with **componentize-py** (ADR-2606014600 + ADR-2606072200). It runs in the kotoba WASM runtime —
via `jco` transpile under node, the donated mesh, or a full IPFS-node host.

```
world shionome-actor { export compute: func() -> string; }
```

`compute()` returns the cross-asset capital-flow observation as JSON — net flow per bucket, the
top rotation pair (どこからどこへ), and a FACTUAL risk-on/off/mixed regime — carrying
`no_trade: true` (G2, トレードはしない) and `is_mirror: true` (G5). A self-contained
`:representative` seed makes it offline-verifiable; real deployments read the full graph from the
kotoba Datom log.

## Build + verify

```bash
./build.sh        # componentize-py → shionome-actor.wasm → wasm-tools validate → jco transpile → IPFS CID
node verify.mjs   # runs the transpiled component, asserts regime=risk-on + no_trade=true
```

Last build: `shionome-actor.wasm` 18.5 MB · CID
`bafybeigk6whellozcybop4btzcrdtybd5yejjrax7tczxhapfsyya64hka` (dag-pb, multi-block) ·
**T2 donated-mesh** (bundles CPython ~17.7 MB → not the browser-local ameno raw-CID tier).

The `.wasm` + `transpiled/` are **gitignored** — reproducible from `app.py` + `wit/` via
`build.sh`; the CID is recorded in `shionome-actor.meta.json`.

> This is the standalone WASM face. The **production fleet** path runs shionome as
> `kotoba_langgraph` Pregel cells under cron on the Murakumo Mac-mini fleet — see
> `20-actors/magatama/cells/shionome_*` + `50-infra/murakumo/fleet.toml`. Both are kotoba-WASM;
> live ingest/posting stays Council Lv6+ + operator gated (G8).
