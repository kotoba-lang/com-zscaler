# watatsuna 綿津綱 — WASM actor (componentize-py, T2 donated-mesh)

The watatsuna actor's chokepoint-criticality computation built as a **WASI
Component-Model component** with **componentize-py** (ADR-2606014600). This is the
*real Tier-B actor → WASM* path (the counterpart to tsumugi's compact Rust core),
and it surfaces an honest architectural boundary.

## Files

- `app.py` — the actor: per submarine-cable **chokepoint**, the aggregate Tbps of
  the distinct cables that traverse it (mirrors `methods/analyze.py`; a resilience
  map, NEVER a target-list — G2, watatsumi N8). Embeds a bounded `:representative`
  seed; reproduces the documented finding **Malacca = top chokepoint**.
  `componentize-py` binds the WIT export to `class WitWorld.compute()`.
- `wit/world.wit` — `world watatsuna-actor { export compute: func() -> string; }`
- `build.sh` — componentize-py build + jco transpile + CID.
- `verify.mjs` — headless: run the transpiled component, assert Malacca top.
- `watatsuna-actor.meta.json` — recorded CID + size + tier (committed; the binary is not).

## Build & verify

```bash
./build.sh                 # → watatsuna-actor.wasm (~17.6 MB) + transpiled/ + CID
node verify.mjs            # asserts top chokepoint = Malacca
```

Recorded artifact (see `watatsuna-actor.meta.json`):
- CID `bafybeihusqahaeirwqur64aeh5fvwuoh54cawbmo7smx3h2abvps6li7pa` (**dag-pb**, multi-block)
- size ~17.6 MB (bundles CPython), built with componentize-py 0.23.0

## The honest boundary this surfaces (ADR-2606014600)

A Python actor componentized with componentize-py bundles the CPython runtime, so
the artifact is **~17.6 MB → a multi-block dag-pb CID**. Consequences:

| | tsumugi (Rust) | watatsuna (componentize-py) |
|---|---|---|
| size | 23.5 KB | ~17.6 MB |
| CID | raw single-block (`bafkrei…`) | dag-pb multi-block (`bafybei…`) |
| apex `/ipfs` gateway verify | ✅ (sha256→CID) | ❌ needs full UnixFS verifier |
| browser-local (ameno T1) | ✅ | ❌ |
| exec tier | **T1 browser-local** + mesh | **T2 donated-mesh** (full IPFS node) |

The DID doc reflects this: watatsuna's `EtzhayyimWasmComponent` service is tagged
`x-exec: donated-mesh` / `x-cid-codec: dag-pb`, so the browser loader correctly
declines it and a mesh node (jco / wasmtime / full IPFS host) runs it. **This is why
compact edge actors use Rust/AssemblyScript** (raw CID, browser-verifiable) while
Python componentization fits the heavier mesh tier.

## Honest scope (R0)

Bounded `:representative` seed (reproduces the *direction* — Malacca top — not the
exact analyze.py 940.16 Tbps); the 17.6 MB binary + `transpiled/` are gitignored
(rebuild via `build.sh`); live IPFS pinning + mesh execution are operator-gated.
