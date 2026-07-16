# mimamori wasm — kotoba pywasm component (build-ready)

ADR-2606112300 · pattern: rasen (ADR-2606101000) · runtime lineage: One Worker,
many WASM actors (ADR-2606014500) + trustless `/ipfs/<cid>` gateway (2606014600).

The component is **stateless**: `heartbeat(cycle, prev)` returns the
content-addressed transaction (`{cid, txEdn, summary}`) and the HOST appends it
to its own append-only log — the component never holds the log, so the
architecture itself cannot become the throne (G5). The SYNTHETIC seed (G7) is
embedded at build time; the moyai ledger is vendored **verbatim** with a
provenance header (ADR-2606082100 Part A).

| export | returns |
|---|---|
| `heartbeat(cycle, prev)` | JSON `{cid, txEdn, summary}` — summary is counts-only (G5) |
| `coverage()` | aggregate-only markdown (counts, never names) |
| `bonds-of(did)` | own-DID-only bond list (G4/G5); non-party → `[]` |
| `vow()` | the shomer vow (§D5) — each line IS a gate |

## Dev mode (no build needed)

```bash
python3 wasm/app.py heartbeat 1     # exact export bodies, on-disk seed
python3 wasm/app.py vow
```

## Build (operator step — componentize-py + ipfs required)

```bash
./wasm/build.sh    # → dist/mimamori.wasm + dist/mimamori.cid + did-service.json
```

Then advertise `wasm/did-service.json` in the actor did.json (G7-gated operator
step, ADR-2606013800).
