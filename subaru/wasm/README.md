# subaru 昴 — kotoba cljc-native WASM actor (cherry+ComponentizeJS, ADR-2606261200)

Design for running subaru's constellation sim as a **kotoba pywasm actor** under the
"one Worker, many WASM actors" model (ADR-2606014500 / 2606014600), identical in shape to
hoshimori (ADR-2606073600) and torifune (this wave, ADR-2606162355). The only first-party
Cloudflare Worker is `etzhayyim.com` (identity / `did.json`); the actor is a
**content-addressed WASM component** fetched from IPFS and run **locally** (browser via
ameno, or the donated mesh via e7m-wasm-runner) — **no per-actor server** (no-server-key).

## Why pywasm fits subaru

subaru's methods are **pure-stdlib Python (no numpy)** so they compile to a WASM Component via
**componentize-py**. The per-link budget, the §1.16 coverage reach, and the stewardship check
are arithmetic + a graph fold over the constellation ontology — no native deps. The same code
runs as a CLI cell on a mesh node and in-WASM in the browser with zero server trust (the reader
recomputes the component CID and compares it to the DID-doc CID before executing).

This is also the correct posture for **G1**: a browser-local, content-addressed, read-only
component **cannot** be a surveillance / targeting platform. It **routes no traffic**, holds
only aggregate `:service-area` facts, and its schema admits **no DPI / user-location /
targeting-relay attribute** (`check_g1` runs before any emit). A sim that carries no user
traffic and cannot represent a per-person location is not a surveillance network.

## Component ABI (WIT sketch)

```wit
package etzhayyim:subaru@0.1.0;

world subaru-actor {
  /// per-link budget margins over the embedded constellation. returns JSON:
  ///   { links:[{link,label,band,margin_db}], min_margin_db, all_closed }
  export link_budget: func() -> string;

  /// coverage as §1.16 Social Security reach (G3). JSON: { areas:[...], ss_reach, n_priority }
  export coverage: func() -> string;

  /// orbital stewardship (G5): disposal + darksat; refuses an undisposed occupied shell.
  export stewardship: func() -> string;

  /// emit the kotoba Datom log (EAVT) for the embedded constellation graph as EDN text.
  export datoms: func(tx: u32) -> string;
}
```

`link_budget.py` / `coverage.py` / `stewardship.py` / `datom_emit.py` become the export bodies;
the embedded `seed-constellation.kotoba.edn` is bundled read-only (no filesystem at runtime).

## Build & verify (target)

```bash
componentize-py -w subaru-actor componentize actor -o dist/subaru.wasm
ipfs add --cid-version=1 --raw-leaves dist/subaru.wasm > dist/subaru.cid
node ../../tsumugi/wasm/loader/verify.mjs dist/subaru.wasm   # reuse headless CID-verify path
```

The CID is advertised in the actor's `did.json` as an `EtzhayyimWasmComponent` service,
issued dynamically by the apex Worker (ADR-2606013800) from `:actor/wasm-cid`.

## Trust model

- **No server key.** Read-only component; never signs; operates no spacecraft and carries no
  user traffic. Identity = actor `did:key` + content-addressed DID doc (ADR-2606015600).
- **Integrity before execution.** ameno / e7m refuse on CID mismatch.
- **G1/G2 hold in WASM too.** The component embeds only aggregate service-area facts; it
  cannot inspect traffic (no DPI attribute exists), cannot geolocate a user (no user-location
  attribute exists), and routes no ciphertext. Live constellation operation is a separate
  Council + operator-DID-gated leg (G8), never this component.

## Status

R1 sim landed (pure stdlib, 8 tests green incl. `test_g1_no_surveillance_relay`). The
componentize-py build + CID advertisement land with the actor's first WASM deploy wave
(operator step, gated like hoshimori / torifune / tsumugi).
