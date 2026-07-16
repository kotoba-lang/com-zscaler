# tsubasa 翼 — WASM component (R3 scaffold)

Per ADR-2606072802 (R3) + the one-Worker-many-WASM-actors architecture (ADR-2606014500):
the **pure compute core** (`methods/analyze.cljc` — `analyze` / `coverage` + the template
digest) is exposed as a content-addressed WASM **Component Model** module so it can run
**browser-local (ameno)** or on a **donated mesh node**, with no per-actor server.

## Why this is charter-clean by construction

The component is **compute-only**. `world.wit` exports `compute` and imports **no**
`wasi:sockets` / `wasi:clocks` / `wasi:random`. The *absence* of those imports is the
guarantee:

- **G1 no-inflow** — no network → it cannot redirect to an affiliate or take a commission; `build.clj` (bb) additionally fails the build if a `commission`/`affiliate` symbol is present.
- **G5 no-person-tracking** — no clock/random/socket → no per-searcher state can leave the module.
- **G6 Murakumo-only** — the LLM digest stays on the *host* (loopback Murakumo); the component only emits the deterministic template digest.

The host (operator/member runtime) owns every side effect: the ingest **fetch leg**
(`methods/ingest.cljc` consumes its output), persistence to the kotoba commit-DAG
(`methods/kotoba.cljc`), and any Murakumo narration (`methods/digest.cljc`).

## Status (honest)

This is the **build scaffold**: `world.wit` (the interface) + `build.clj` (bb) (the recipe +
charter assertions). The compiled artifact + its pinned CID are the **no-server-key
operator step** — identical to how `shionome-core` / `tsumugi` / `rasen` landed their
WASM. The recommended implementation is a tiny Rust `tsubasa-core` crate that ports the
pure `analyze`/`coverage` core (the cljc is the reference semantics; `test_analyze.cljc`
is the conformance oracle the Rust port must match).

```
cargo component build --release --target wasm32-wasip2     # operator
bb build.clj target/wasm32-wasip2/release/tsubasa_core.wasm   # verify + CID
# then register the CID in INFRA_ACTORS.tsubasa.wasmCid + public/actor/tsubasa/did.json _meta.wasmCid
```

Until then, `did.json` carries `"wasmCid": null` and the actor runs `service`-model
(the bb methods), exactly as the other clj-native actors do today.
