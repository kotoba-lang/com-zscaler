# umisachi 海幸 — kotoba WASM actor (design)

R0 ships the analyzer as **babashka Clojure** methods (`methods/*.clj`). The WASM target is
a kotoba Component-Model actor compiled from the same pure logic.

## Plan

- **Logic is already WASM-clean**: `methods/{analyze,datom_emit,coverage_report}.clj` use only
  `clojure.edn`, `clojure.string`, and `clojure.java.io` (for the seed read / out write — the
  only host-effecting calls). The pure core (`load-graph`, `analyze`, `emit`, `report`,
  `assert-charter-clean`) takes/returns plain data and performs **no I/O**, so it ports to the
  WASM Component Model with the host owning the Datom log (the rasen/ibuki pattern: a stateless
  transform; the host appends).
- **Boundary**: the component exports `analyze(seed-edn) → report-md`,
  `datoms(seed-edn, tx) → eavt-edn`, and `coverage(seed-edn) → md`. The host injects the seed
  bytes and persists the emitted EAVT to the canonical kotoba log (ADR-2605312345).
- **Charter guard travels with the logic**: `assert-charter-clean` (G1 no-coordinates / G4
  factory-fishing-unrepresentable) runs inside `load-graph`, so a violating seed is refused at
  the component boundary, not just at the CLI.
- **Build** (operator step, G7): a Clojure→WASM path (e.g. via the kotoba Clojure runtime, or
  a componentize step mirroring rasen's `wasm/build.sh`). Until then the babashka methods are
  the reference implementation and the test suite (`tests/`) is the conformance contract.

## Invariants the WASM build must preserve

- no coordinate-shaped attribute is ever accepted (G1);
- `:factory-fishing` etc. are never a `:fishery/method` (G4) — only a `:pressure/kind`;
- 取 is edge-only; node depletion/nourishment is computed on read (G2/N1);
- emitted EAVT is valid EDN of `[e a v tx op]` datoms (the `tests/test_coverage.clj`
  round-trip is the gate).
