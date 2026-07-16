# Post_quantum Clean Room Actor

Clean-room API-compatible implementation of the post_quantum frontier technology, backed by Datomic and Py Kotodama WASM.

## Methods (R0, pure stdlib — ADR-2606111300)

The actor is the machine-readable SSoT of the substrate's post-quantum
migration state (the survivability paper's §7 table as data):

- `methods/suite.py` — pqh-v1 suite registry (FIPS 203/204 + RFC 9106
  constants, draft multicodecs 0x120c/0x1211) + per-layer migration status
  (`:migrated` / `:adequate` / `:operator-pending` / `:chain-blocked` /
  `:upstream-pending` / `:deferred`) + Mosca/Grover helpers.
  `python3 methods/suite.py` prints the coverage readout.
- `methods/datom_emit.py` — EAVT Datom-log projection (ground :add datoms;
  derived coverage flagged `:pq/is-transient`, computed on read).
- `tests/test_suite_registry.py` — 8 tests: every Shor-vulnerable layer is
  migrated or *explicitly* gated with a reason (no silent debt), FIPS sizes
  match the landed SDK/did-web implementations, Mosca/Grover reproduce the
  paper's numbers, emit is deterministic and stratified.

Run: `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest tests/ -q`
