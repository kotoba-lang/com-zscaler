# aburi 炙り — kotoba cljc-native WASM actor (cherry + ComponentizeJS) design

> **ADR-2606261200**: this actor is built cljc-native — `methods/*.cljc` → cherry(JS) →
> ComponentizeJS(jco) → WASI Component. The entry is `app.cljs` (was `app.py`); the WIT
> world (`wit/world.wit`) is unchanged. The notes below describe the build shape; the
> componentize-py history is superseded.

aburi's methods are **pure stdlib** (no numpy, no network) precisely so they can run as a
content-addressed kotoba WASM actor under cherry + ComponentizeJS (ADR-2606261200), where the
**host owns the log** and the component is a stateless analyzer.

## Exports (planned)

| export | maps to | returns |
|---|---|---|
| `analyze` | `methods/analyze.py::analyze` | exposure / surface-leak / spread / route-coverage readouts (transient, N1/G2) |
| `datoms` | `methods/datom_emit.py::emit` | ground EAVT datoms for the host to append (own-surface / public-catalogue only) |
| `coverage` | `methods/coverage_report.py::report` | honest coverage + gap map |

## Constitutional shape (why WASM is the right boundary)

- **G1 / G7 — own-data, local-only.** The component reads exposure **structure** the member's
  device already produced (parsed from their OWN Google Takeout / Apple App-Privacy / Play
  Data-safety / permission dump under `data/local/`). The WASM sandbox has **no network and no
  ambient FS** — it cannot exfiltrate, cannot phone home, cannot build a dossier of anyone. The
  member's raw export bytes never leave their device; only the aggregate exposure graph (which
  data-kind flows to which catalogued collector) is computed.
- **G8 — no credential / raw identifier crosses the boundary.** The host passes already-parsed
  structural facts; the emit attr allowlist carries no credential / raw-id attribute (see
  `datom_emit.NODE_ATTRS`), so nothing of that shape can be projected even in principle.
- **no-server-key.** The component signs nothing and serves no key; any persistence to the live
  kotoba engine, any DSAR/opt-out routing, is a host step gated by member-sig + operator + Council
  (G7).

## Build (operator step, deferred)

cherry + ComponentizeJS over `methods/*.cljc` against the aburi world (to be authored at R1), CID-pinned and
verified the way rasen/shionome T1 actors are. R0 ships the analyzer + ontology + representative
seed only; the build is an explicit operator step, never a cron.
