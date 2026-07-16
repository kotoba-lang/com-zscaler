# tokigusuri 時薬 — kotoba pywasm actor (componentize-py)

Design for running tokigusuri's analyzer as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
the hokorobi (ADR-2606073400), inochi (ADR-2606073000) and asobi (ADR-2606073200) actors.
The only first-party Cloudflare Worker is `etzhayyim.com` (identity / `did.json`); the actor
is a **content-addressed WASM component** fetched from IPFS and run **locally** (browser via
ameno, or the donated mesh via e7m-wasm-runner) — **no per-actor server** (no-server-key).

## Why pywasm fits tokigusuri

tokigusuri's methods are **pure `.cljc` (bb/clj) with no native deps** — the edge-primary
access-barrier / release computation is a graph integral over `:en/barrier-load`. The
canonical methods are portable `.cljc`; the WASM leg is the pywasm-runtime shape shared by
the mirror lineage (a pure-stdlib translation bundles the same graph integral). The same
logic runs as a CLI cell on a mesh node and in-WASM in the browser with zero server trust
(the reader recomputes the component CID and compares it to the DID-doc CID before executing).

This is also the right trust posture for medicine-access: a browser-local, content-addressed,
read-only component cannot be an FTO-opinion service or a trading signal (G1) — it holds no
live patent feed, issues no legal opinion, and never trades.

## Component ABI (WIT sketch)

```wit
package etzhayyim:tokigusuri@0.1.0;

world tokigusuri-actor {
  /// access-barrier concentration vs release over the embedded :representative graph
  /// (G1: no FTO opinion, no trading signal, no per-company verdict). returns JSON:
  ///   { barrier:[{id,label,essentiality,score}], holders:[...], release:[...] }
  export analyze: func() -> string;

  /// emit the kotoba Datom log (EAVT) for the embedded graph as EDN text.
  export datoms: func(tx: u32) -> string;

  /// honest coverage report (markdown).
  export coverage: func() -> string;
}
```

`analyze.cljc` / `datom_emit.cljc` / `coverage_report.cljc` become the three export bodies;
the embedded seed is bundled read-only (no filesystem at runtime).

## Build & verify (target)

```bash
componentize-py -w tokigusuri-actor componentize actor -o dist/tokigusuri.wasm
ipfs add --cid-version=1 --raw-leaves dist/tokigusuri.wasm > dist/tokigusuri.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/tokigusuri.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service,
issued dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only component; never signs; never trades; never asserts FTO.
  Identity = actor `did:key` + content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1 / G4 hold in WASM too.** The component holds no live patent feed and emits no signal or
  legal opinion; it surfaces only disclosed, lawful release routes.

## Status

R0 design-only. Methods are pywasm-ready (pure `.cljc`, 8 tests green); the componentize-py
build + CID advertisement land with the actor's first WASM deploy wave (gated like hokorobi /
inochi / asobi / tsumugi).
