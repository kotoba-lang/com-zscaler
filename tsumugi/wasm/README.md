# tsumugi 紡ぎ — WASM actor (one-Worker, browser-local execution)

PoC for **ADR-2606014500** — the "one Worker, many WASM actors" model. The only
first-party Cloudflare Worker is `etzhayyim.com` (identity / `did.json`). The
actor itself is a **content-addressed WASM component** fetched from IPFS and run
**locally** (browser via ameno, or a donated mesh node) — there is **no per-actor
server**.

## Layout

- `tsumugi-core/` — Rust crate → `wasm32-unknown-unknown` cdylib. Embeds a bounded
  `:representative` power-graph and computes **edge-primary incident karma**
  (取-concentration) — aggregate-only, no per-soul score (G2/N1, ADR-2606011800).
  ABI: `compute() -> i32` (writes JSON into linear memory, returns length) +
  `result_ptr() -> i32`.
- `loader/index.html` — self-contained browser loader: resolve DID → find
  `EtzhayyimWasmComponent` service → fetch WASM → **verify content integrity**
  (recompute CIDv1, compare to the DID-doc CID) → instantiate + run → render.
- `loader/verify.mjs` — the same resolve→integrity→execute path, headless in Node
  (CI-runnable; no deps).
- `build.sh` — build + content-address.

## Build & verify

```bash
./build.sh                 # → dist/tsumugi-core.wasm + dist/tsumugi-core.cid
node loader/verify.mjs      # headless proof (asserts CID integrity + TSMC top 取)
# browser: serve this dir and open loader/index.html
python3 -m http.server -d loader 8088   # then http://127.0.0.1:8088/
```

Current artifact CID (CIDv1, raw/sha2-256):
`bafkreidfttpqimwnx4i5a3rswum3orcg3qfa3q7fwts6axgqtcpuokddfi`

This CID is carried in the actor's DID document as an `EtzhayyimWasmComponent`
service (`ipfs://<cid>`), emitted by the apex Worker's dynamic did.json issuance
(ADR-2606013800) from `:actor/wasm-cid` in the kotoba `actors-v1` graph.

## Trust model

did:web trust root = **TLS** (the DID doc is fetched over HTTPS from
`etzhayyim.com`). The WASM artifact's trust root = its **CID** (content address) —
the loader recomputes `sha256 → CIDv1` and refuses to run bytes that don't match.
**No server key is involved** (ADR-2605231525). Inference stays Murakumo-only /
ameno frozen-edge; this PoC is pure compute (no model).

## Honest scope (R0)

- Embedded `:representative` seed graph (real deployments read the full graph from
  the kotoba Datom log). Reproduces the documented finding that **TSMC** carries the
  top 取-concentration; exact analyze.py numbers are not reproduced.
- `wasm32-unknown-unknown` core module (not yet a full WASI/Component-Model
  component); the kotoba-wasm runtime + libp2p peer dispatch (T2 donated mesh) and
  live IPFS pinning are operator-gated.
- Live trustless IPFS gateway at `etzhayyim.com/ipfs/<cid>` is not yet wired (the
  loader defaults to the local artifact).
