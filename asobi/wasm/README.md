# asobi 遊び — kotoba pywasm actor (componentize-py)

Design for running asobi's analyzer as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
the inochi actor (ADR-2606073000). The only first-party Cloudflare Worker is
`etzhayyim.com` (identity / `did.json`); the actor is a **content-addressed WASM component**
fetched from IPFS and run **locally** (browser via ameno, or the donated mesh via
e7m-wasm-runner) — **no per-actor server** (no-server-key).

## Why pywasm fits asobi

asobi's methods are **pure-stdlib Python (no numpy)** so they compile to a WASM Component
via **componentize-py** (the watatsuna pattern). The edge-primary participation/enclosure
computation is a graph integral over `:en/access-load` — no native deps. The same code runs
as a CLI cell on a mesh node and in-WASM in the browser with zero server trust (the reader
recomputes the component CID and compares it to the DID-doc CID before executing).

## Component ABI (WIT sketch)

```wit
package etzhayyim:asobi@0.1.0;

world asobi-actor {
  /// participation-openness vs enclosure over the embedded :representative graph (G1: no
  /// retention/popularity metric). returns JSON:
  ///   { openness:[{id,label,access,score}], enclosures:[...], enclosed:[...] }
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
componentize-py -w asobi-actor componentize actor -o dist/asobi.wasm
ipfs add --cid-version=1 --raw-leaves dist/asobi.wasm > dist/asobi.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/asobi.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service,
issued dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only component; never signs. Identity = actor `did:key` +
  content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1 holds in WASM too.** The component contains no retention/popularity machinery; it
  cannot surface what it does not contain.

## Status

R0 design-only. Methods are pywasm-ready (pure stdlib, 8 tests green); the componentize-py
build + CID advertisement land with the actor's first WASM deploy wave (gated like inochi /
tsumugi).
