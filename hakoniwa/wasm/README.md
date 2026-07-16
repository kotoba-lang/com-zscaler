# hakoniwa 箱庭 — kotoba pywasm actor (componentize-py) design

R0 ships the actor as **pure-stdlib Python** so it compiles to a **kotoba WASM Component** via
`componentize-py` (the same path as rasen / inochi / sonae). The deterministic Friedkin-Johnsen
kernel runs **in-WASM, browser-local** (ameno T1) or on the donated mesh (e7m T2); there is no
third-party dependency and no `Math.random` (the ensemble jitter is `sha256`-seeded, portable).

## Two execution tiers

- **Deterministic kernel (R0, in-WASM).** `world.py` + `simulate.py` + `distribution.py` +
  `datom_emit.py` compile as-is. Inputs: a scenario EDN; outputs: the distribution report,
  the mitooshi forecast record, and the EAVT Datom projection. Fully reproducible.
- **LLM-persona variant (gated, G5/G8, operator/mesh-side).** Replaces the scalar stance
  update with a Murakumo-routed persona step (the swarm rides baien-edge, ADR-2605242600 /
  2605241900). The *interface* is unchanged — synthetic agents → ensemble → distribution →
  mitooshi — so the WASM boundary and the gates (G1–G3) are identical. This variant needs the
  network and runs operator/mesh-side, never in-browser, and is **Council + operator-DID
  gated** (G8); no platform-held key signs its output (ADR-2605231525).

## Content addressing

`out/` artifacts are content-addressable to a kotoba IPFS CIDv1 (raw/sha2-256) using the same
parity discipline as `rasen/methods/cid.py` (byte-identical to `ipfs add --cid-version=1
--raw-leaves`, verifiable with no daemon). The CID is **reused, not re-implemented**, at deploy
time; R0 does not pin (no-server-key, G8).

## Build (when the WASM wave lands, ADR-2606014500 / 2606014600)

```bash
# illustrative — gated until the actor-WASM deploy wave
componentize-py -w hakoniwa bindings .
componentize-py -w hakoniwa componentize app -o hakoniwa.wasm
# verify the component CID against the kotoba IPFS gateway (verify.mjs parity)
```
