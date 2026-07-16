# torifune 鳥船 — kotoba cljc-native WASM actor (cherry+ComponentizeJS, ADR-2606261200)

Design for running torifune's launch-vehicle sim as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
hoshimori (ADR-2606073600) and the funadaiku build-pattern lineage. The only first-party
Cloudflare Worker is `etzhayyim.com` (identity / `did.json`); the actor is a
**content-addressed WASM component** fetched from IPFS and run **locally** (browser via
ameno, or the donated mesh via e7m-wasm-runner) — **no per-actor server** (no-server-key).

## Why pywasm fits torifune

torifune's methods are **pure-stdlib Python (no numpy)** so they compile to a WASM Component
via **cherry + ComponentizeJS** (ADR-2606261200). The staged Tsiolkovsky Δv, the carbon balance, and the disposal
check are arithmetic + a graph fold over the launch-vehicle ontology — no native deps
(`math.log` is stdlib). The same code runs as a CLI cell on a mesh node and in-WASM in the
browser with zero server trust (the reader recomputes the component CID and compares it to the
DID-doc CID before executing).

This is also the correct posture for **G1**: a browser-local, content-addressed, **dry-run**
component **cannot** fly a rocket. It commands no actuator, issues no guidance, holds only
representative engineering estimates, and its trajectory/payload enums admit **no strike /
munition member** (`check_g1` runs before any emit). A sim that cannot actuate and cannot
represent a strike profile is not a weapon.

## Component ABI (WIT sketch)

```wit
package etzhayyim:torifune@0.1.0;

world torifune-actor {
  /// staged Δv budget + margin to the embedded mission's target regime (civilian only).
  /// returns JSON: { per_stage:[{stage,isp_s,dv_ms,...}], total_dv_ms, required_dv_ms,
  ///                 dv_margin_ms, payload_kg, target_regime }
  export ascent: func() -> string;

  /// zero-net-carbon propellant accounting (G2). JSON: { rows:[...], net_kgco2e, g2_pass }
  export carbon: func() -> string;

  /// debris-responsibility disposal plan (G5) as EDN text; refuses a mission with no plan.
  export disposal: func() -> string;

  /// emit the kotoba Datom log (EAVT) for the embedded vehicle graph as EDN text.
  export datoms: func(tx: u32) -> string;
}
```

`ascent_sim.py` / `carbon_balance.py` / `disposal_plan.py` / `datom_emit.py` become the export
bodies; the embedded `seed-ama-vehicle.kotoba.edn` is bundled read-only (no filesystem at
runtime).

## Build & verify (target)

```bash
componentize-py -w torifune-actor componentize actor -o dist/torifune.wasm
ipfs add --cid-version=1 --raw-leaves dist/torifune.wasm > dist/torifune.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/torifune.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service,
issued dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only, dry-run component; never signs; commands no spacecraft and no
  launch hardware. Identity = actor `did:key` + content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1 holds in WASM too.** The component embeds only civilian classes + representative
  estimates; it cannot represent a strike trajectory or munition payload, and it cannot
  actuate. Actual launch operation is a separate Council + operator-DID-gated leg (G6), never
  this component.

## Status

R1 sim landed (pure stdlib, 7 tests green incl. `test_g1_no_strike_profile`). The
componentize-py build + CID advertisement land with the actor's first WASM deploy wave
(operator step, gated like hoshimori / tsumugi).
