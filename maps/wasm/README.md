# maps 地図 — T1 WASM actor (`maps-core`)

The **executable read-side face** of the maps onsen (温泉) recommender
(`20-actors/maps/methods/onsen.cljc`, ADR-2606064500), shipped as a
content-addressed browser-local WASM actor per the **ameno** execution model
(ADR-2606014500 / 2606015200) — the same compact raw-CID T1 pattern as
`shionome-core` / `tsumugi-core` / `kanae-core`.

It embeds a bounded `:representative` seed of famous onsen and computes the
**transparent place-quality ranking** (おすすめ) the `recommend` read-path serves.
Every output carries `place_not_person:true` — a feature is a PLACED THING, never
a person (G9); the score is recomputable from the embedded tags and `why` makes
it auditable.

## ABI (no WIT imports — instantiate with `{}`)

| export | meaning |
|---|---|
| `compute() -> i32` | run; write result JSON to an internal buffer; return its byte length |
| `result_ptr() -> i32` | pointer into the exported `memory` where the JSON starts |
| `memory` | standard cdylib linear memory |

```js
const { instance } = await WebAssembly.instantiate(wasmBytes, {});
const len = instance.exports.compute();
const ptr = instance.exports.result_ptr();
const out = JSON.parse(Buffer.from(instance.exports.memory.buffer, ptr, len).toString());
// → { actor:"maps", view:"onsen-osusume", osusume:[…], place_not_person:true, … }
```

## Build

```
bb build.clj      # cargo → wasm32 → strip → validate → node invariant check → CID
```

Produces `dist/maps-core.wasm` + `dist/maps-core.cid`. The CID is recorded in
`maps-core.meta.json` and the did-web registry (`50-infra/etzhayyim-did-web/`).
The bytes are **not** committed (reproducible; `dist/`+`target/` are gitignored) —
they live on IPFS / the donated mesh. **Pinning the bytes to IPFS is an operator step.**

## Scope

This is the **onsen-recommendation slice** as wasm. The full interactive map
renderer (MapLibre + KAMI + SvelteKit, currently a TS-native CF Worker over a
RisingWave/Hyperdrive backend) becomes a browser-local wasm actor only after the
kotoba-native migration (ADR-2606064500 R1→R3) lands — a separate effort.
