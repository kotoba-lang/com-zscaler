# open-isco kotoba-native — MATURITY scorecard

_Generated for the kotoba-datomic / clj→WASM pilot. Implementation engine:
`moonshotai/kimi-k2.7-code` via OpenRouter; orchestration + verification: Claude._

## Coordinator cell commands (`coordinator.clj` → `coordinator.wasm`)

| mode | command | host surface | output | test |
|---|---|---|---|---|
| 0 | lookup | `kqe-get-objects` name | CBOR text (name) | ✅ |
| 1 | summarize | `llm-infer` | model output | ✅ |
| 2 | coverage | `kqe-query` name | CBOR uint (count) | ✅ |
| 3 | parent | `kqe-get-objects` parent | CBOR text (parent code) | ✅ |
| 4 | children | `kqe-query` parent + filter | CBOR uint (count) | ✅ |
| 5 | materialize | `kqe-query` + cbor array | CBOR array (child codes) | ✅ |
| 6 | ratio | `kqe-query` ×2 + cbor map | CBOR map {names,parents} | ✅ |

All 7 commands compile to a single WASM Component (kotoba:kais world) and are
verified end-to-end on `WasmExecutor` over a seeded ISCO Datom snapshot.

## Seed census (`isco-occupations.kotoba.edn`)

| level | count |
|---|---|
| major (1-digit) | 10 |
| sub-major (2-digit) | 43 |
| minor (3-digit) | 130 |
| unit (4-digit) | 436 |
| **total** | **619** |

Integrity (CI-checked via bb): `:isco.occupation/code` unique-identity, 0 undeclared
attributes, 0 dangling `:isco.occupation/parent` refs, 0 nil rows.

## Verification

`cargo test -p kotoba-clj --test isco_coordinator` → **10 passed**; `--test seed_integrity` runs `validate.clj` over the seed as a CI gate (skips if bb absent). WASM re-emitted on every cell change.

## Sourcing (G8/G11)

Seed is the AUTHORITATIVE ISCO-08 standard structure — all 619 nodes (10/43/130/436)
tagged `:occ/sourcing :authoritative`, names per the official ILO ISCO-08, retrieved
from the web (gist iamarsenibragimov/39b5186…). Structure verified: exact standard
counts, 0 dangling parents, gaps=0. Representative-approximation data from earlier
iterations has been fully replaced.
