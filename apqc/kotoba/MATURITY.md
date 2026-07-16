# open-apqc kotoba-native — MATURITY scorecard

_kotoba-datomic / clj→WASM pilot. Implementation engine: `moonshotai/kimi-k2.7-code`
via OpenRouter; orchestration + verification: Claude._

## Coordinator cell commands (`apqc-coordinator.clj` → `apqc-coordinator.wasm`)

| mode | command | host surface | output | test |
|---|---|---|---|---|
| 0 | lookup (getProcess.name) | `kqe-get-objects` name | CBOR text | ✅ |
| 1 | summarize | `llm-infer` | model output | ✅ |
| 2 | coverageSnapshot | `kqe-query` name | CBOR uint (count) | ✅ |
| 3 | parent (parentProcessId) | `kqe-get-objects` parent | CBOR text | ✅ |
| 4 | children | `kqe-query` parent + filter | CBOR uint (count) | ✅ |
| 5 | materializeSubprocesses | `kqe-query` + cbor array | CBOR array (child codes) | ✅ |
| 6 | coverage-ratio | `kqe-query` ×2 + cbor map | CBOR map {names,parents} | ✅ |

All 7 commands compile to one WASM Component and verify on `WasmExecutor` over a
seeded PCF Datom snapshot. (The cell is derived by deterministic substitution from
the verified ISCO cell — identical control flow, swapped graph/predicate strings.)

## Seed census (`apqc-pcf.kotoba.edn`)

| level | count |
|---|---|
| L1 category | 13 |
| L2 process group | 72 |
| L3 process | 352 |
| L4 activity | 276 |
| **total** | **713** |

Integrity (bb-checked): `:apqc.process/code` unique-identity, 0 undeclared attributes,
0 dangling `:apqc.process/parent` refs, 0 nil rows. All 13 PCF v7.4 L1 categories
present with verbatim canonical names.

## Verification

`cargo test -p kotoba-clj --test apqc_coordinator` → **10 passed**.

## Sourcing (G8/G11)

- **L1 (13 categories): `:authoritative`** — the public canonical PCF v7.4 cross-industry
  category names (verbatim).
- **L2/L3/L4 (700): `:representative`** — parallel-generated approximations.

The full APQC PCF v7.4 body (L2–L5 detail) is **APQC copyrighted IP**, distributed
under APQC license terms; unlike ILO ISCO-08 it is NOT freely redistributable, so it
is deliberately NOT web-scraped/committed here. Authoritative L2–L4 requires importing
it through APQC's own license (a separate, user-authorized step). Structure (codes/
hierarchy/parent refs) + WASM execution are verified regardless.
