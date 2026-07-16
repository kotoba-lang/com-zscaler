# hoshimori 星守 — kotoba pywasm actor (componentize-py)

Design for running hoshimori's analyzer as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
inochi (ADR-2606073000), asobi (ADR-2606073200) and hokorobi (ADR-2606073400). The only
first-party Cloudflare Worker is `etzhayyim.com` (identity / `did.json`); the actor is a
**content-addressed WASM component** fetched from IPFS and run **locally** (browser via
ameno, or the donated mesh via e7m-wasm-runner) — **no per-actor server** (no-server-key).

## Why pywasm fits hoshimori

hoshimori's methods are **pure-stdlib Python (no numpy)** so they compile to a WASM Component
via **componentize-py**. The edge-primary congestion/stewardship computation is a graph
integral over `:en/orbit-load` — no native deps. The same code runs as a CLI cell on a mesh
node and in-WASM in the browser with zero server trust (the reader recomputes the component
CID and compares it to the DID-doc CID before executing).

This is also the correct posture for G1: a browser-local, content-addressed, read-only
component that holds only shell-aggregate data **cannot** be a targeting service — it embeds
no precise ephemeris, issues no state vector, and commands no spacecraft.

## Component ABI (WIT sketch)

```wit
package etzhayyim:hoshimori@0.1.0;

world hoshimori-actor {
  /// orbital-congestion concentration vs stewardship over the embedded :representative graph
  /// (G1: shell-aggregate, no ephemeris). returns JSON:
  ///   { congestion:[{id,label,regime,score}], occupancy:[...], stewardship:[...], fragility:[...] }
  export analyze: func() -> string;

  /// emit the kotoba Datom log (EAVT) for the embedded graph as EDN text (shell-aggregate).
  export datoms: func(tx: u32) -> string;

  /// honest coverage report (markdown).
  export coverage: func() -> string;
}
```

`analyze.py` / `datom_emit.py` / `coverage_report.py` become the three export bodies; the
embedded seed is bundled read-only (no filesystem at runtime).

## Build & verify (target)

```bash
componentize-py -w hoshimori-actor componentize actor -o dist/hoshimori.wasm
ipfs add --cid-version=1 --raw-leaves dist/hoshimori.wasm > dist/hoshimori.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/hoshimori.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service,
issued dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only component; never signs; commands no spacecraft. Identity =
  actor `did:key` + content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1 holds in WASM too.** The component embeds only shell-aggregate facts; it cannot leak a
  precise ephemeris it does not contain.

## Status

R0 design-only. Methods are pywasm-ready (pure stdlib, 9 tests green); the componentize-py
build + CID advertisement land with the actor's first WASM deploy wave (gated like inochi /
asobi / hokorobi / tsumugi).
