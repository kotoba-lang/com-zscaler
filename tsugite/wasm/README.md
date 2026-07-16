# tsugite 継ぎ手 — kotoba pywasm actor (componentize-py)

Design for running tsugite's analyzer as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
inochi (ADR-2606073000), asobi (2606073200), hokorobi (2606073400) and hoshimori (2606073600).
The only first-party Cloudflare Worker is `etzhayyim.com` (identity / `did.json`); the actor is
a **content-addressed WASM component** fetched from IPFS and run **locally** (browser via
ameno, or the donated mesh via e7m-wasm-runner) — **no per-actor server** (no-server-key).

## Why pywasm fits tsugite

tsugite's methods are **pure-stdlib Python (no numpy)** so they compile to a WASM Component via
**componentize-py**. The edge-primary continuity/protection computation is a graph integral
over `:en/peril-load` — no native deps. The same code runs as a CLI cell on a mesh node and
in-WASM in the browser with zero server trust (the reader recomputes the component CID and
compares it to the DID-doc CID before executing).

This is also the right posture for G1: a browser-local, content-addressed, read-only component
that embeds only collective-aggregate data **cannot** be a person-tracking service — it holds no
individual record, no location, no biometric, and commands no border system.

## Component ABI (WIT sketch)

```wit
package etzhayyim:tsugite@0.1.0;

world tsugite-actor {
  /// peoples-continuity need vs protection over the embedded :representative graph
  /// (G1: collective-aggregate, no individuals). returns JSON:
  ///   { continuity:[{id,label,vitality,score}], pressures:[...], protection:[...], fragility:[...] }
  export analyze: func() -> string;

  /// emit the kotoba Datom log (EAVT) for the embedded graph as EDN text (collective-aggregate).
  export datoms: func(tx: u32) -> string;

  /// honest coverage report (markdown).
  export coverage: func() -> string;
}
```

`analyze.py` / `datom_emit.py` / `coverage_report.py` become the three export bodies; the
embedded seed is bundled read-only (no filesystem at runtime).

## Build & verify (target)

```bash
componentize-py -w tsugite-actor componentize actor -o dist/tsugite.wasm
ipfs add --cid-version=1 --raw-leaves dist/tsugite.wasm > dist/tsugite.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/tsugite.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service,
issued dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only component; never signs; commands no border system. Identity =
  actor `did:key` + content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1 holds in WASM too.** The component embeds only collective-aggregate facts; it cannot leak
  an individual it does not contain.

## Status

R0 design-only. Methods are pywasm-ready (pure stdlib, 9 tests green); the componentize-py
build + CID advertisement land with the actor's first WASM deploy wave (gated like inochi /
asobi / hokorobi / hoshimori / tsumugi).
