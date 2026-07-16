# shiori 栞 — kotoba pywasm actor (componentize-py)

Design for running shiori's analyzer as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
inochi (ADR-2606073000), asobi (2606073200), hokorobi (2606073400), hoshimori (2606073600) and
tsugite (2606073800). The only first-party Cloudflare Worker is `etzhayyim.com` (identity /
`did.json`); the actor is a **content-addressed WASM component** fetched from IPFS and run
**locally** (browser via ameno, or the donated mesh via e7m-wasm-runner) — **no per-actor server**
(no-server-key).

## Why pywasm fits shiori

shiori's methods are **pure-stdlib Python (no numpy)** so they compile to a WASM Component via
**componentize-py**. The edge-primary burden/relief/relief-gap computation is a graph integral
over `:en/load` — no native deps. The same code runs as a CLI cell on a mesh node and in-WASM in
the browser with zero server trust (the reader recomputes the component CID and compares it to the
DID-doc CID before executing).

This is also the right posture for G1: a browser-local, content-addressed, read-only component
that embeds only **cohort-aggregate + structural** data **cannot** be a per-person affect/
manipulation engine — it holds no individual record, no per-person affect score, no biometric, and
commands no nudge channel (intervention is carried by ossekai, gated and on-chain-logged).

## Component ABI (WIT sketch)

```wit
package etzhayyim:shiori@0.1.0;

world shiori-actor {
  /// wellbecoming detraction vs relief over the embedded :representative graph
  /// (G1: cohort-aggregate, no individuals). returns JSON:
  ///   { relief_gap:[{id,label,burden,relief,gap}], imposed:[...], relief:[...], unrouted:[...] }
  export analyze: func() -> string;

  /// emit the kotoba Datom log (EAVT) for the embedded graph as EDN text (cohort-aggregate).
  export datoms: func(tx: u32) -> string;

  /// honest coverage report (markdown).
  export coverage: func() -> string;
}
```

`analyze.py` / `datom_emit.py` / `coverage_report.py` become the three export bodies; the embedded
seed is bundled read-only (no filesystem at runtime). Note the ABI exports **no nudge / no
write / no send** — shiori cannot influence anyone directly; it only emits maps. Any intervention
is a separate, gated call into ossekai (G7/G8).

## Build & verify (target)

```bash
componentize-py -w shiori-actor componentize actor -o dist/shiori.wasm
ipfs add --cid-version=1 --raw-leaves dist/shiori.wasm > dist/shiori.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/shiori.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service, issued
dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only component; never signs; commands no nudge channel. Identity =
  actor `did:key` + content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1 holds in WASM too.** The component embeds only cohort-aggregate + structural facts; it
  cannot leak an individual it does not contain, and exports no influence primitive.

## Status

R0 design-only. Methods are pywasm-ready (pure stdlib, 11 tests green); the componentize-py build +
CID advertisement land with the actor's first WASM deploy wave (gated like inochi / asobi /
hokorobi / hoshimori / tsugite). Live intervention routing into ossekai is additionally
Council-gated (G7/G8).
