# hokorobi 綻び — kotoba pywasm actor (componentize-py)

Design for running hokorobi's analyzer as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
the inochi (ADR-2606073000) and asobi (ADR-2606073200) actors. The only first-party
Cloudflare Worker is `etzhayyim.com` (identity / `did.json`); the actor is a
**content-addressed WASM component** fetched from IPFS and run **locally** (browser via
ameno, or the donated mesh via e7m-wasm-runner) — **no per-actor server** (no-server-key).

## Why pywasm fits hokorobi

hokorobi's methods are **pure-stdlib Python (no numpy)** so they compile to a WASM Component
via **componentize-py**. The edge-primary systemic-risk/resilience computation is a graph
integral over `:en/risk-load` — no native deps. The same code runs as a CLI cell on a mesh
node and in-WASM in the browser with zero server trust (the reader recomputes the component
CID and compares it to the DID-doc CID before executing).

This is also the right trust posture for finance-risk: a browser-local, content-addressed,
read-only component cannot be a market-moving service (G1) — it holds no live feed, issues no
signal, and never trades.

## Component ABI (WIT sketch)

```wit
package etzhayyim:hokorobi@0.1.0;

world hokorobi-actor {
  /// systemic-risk concentration vs resilience over the embedded :representative graph
  /// (G1: no market signal, no solvency verdict). returns JSON:
  ///   { systemic:[{id,label,sii,score}], risk_sources:[...], resilience:[...] }
  export analyze: func() -> string;

  /// emit the kotoba Datom log (EAVT) for the embedded graph as EDN text.
  export datoms: func(tx: u32) -> string;

  /// honest coverage report (markdown).
  export coverage: func() -> string;
}
```

`analyze.py` / `datom_emit.py` / `coverage_report.py` become the three export bodies; the
embedded seed is bundled read-only (no filesystem at runtime).

## Build & verify (target)

```bash
componentize-py -w hokorobi-actor componentize actor -o dist/hokorobi.wasm
ipfs add --cid-version=1 --raw-leaves dist/hokorobi.wasm > dist/hokorobi.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/hokorobi.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service,
issued dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only component; never signs; never trades. Identity = actor
  `did:key` + content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1 holds in WASM too.** The component holds no live market feed and emits no signal; it
  cannot move a market it cannot reach.

## Status

R0 design-only. Methods are pywasm-ready (pure stdlib, 8 tests green); the componentize-py
build + CID advertisement land with the actor's first WASM deploy wave (gated like inochi /
asobi / tsumugi).
