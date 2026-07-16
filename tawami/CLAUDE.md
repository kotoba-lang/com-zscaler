# 撓 tawami — CLAUDE (actor-local rules)

Proof-of-Flexibility asset KG — the flexibility leg of the **Energy Order Protocol**
suite (submits to 澪 mio). OBSERVATION ONLY. ADR-2606211200.

## Invariants (do not weaken)

- **G1 map-not-dispatch.** tawami maps flexibility; it NEVER issues a dispatch/curtail
  order. Never add a `:tawami/dispatch` or `:tawami/curtail-order` attribute — hikari
  actuates under Council gate. Test `g1-g2-g3-map-not-dispatch-no-person-no-trade` guards it.
- **G2 aggregate-first-no-person.** No per-person load profile. Never add a
  `:tawami.person/*` attribute (Rider §2(c) reciprocity).
- **G3 no-trade-no-signal.** Flexibility is observed, never traded.
- **G7 no-server-key.** Telemetry ingest and claim submission to mio are operator/member
  steps; the loop does no network I/O.

## Conventions

- clj/bb over the kotoba Datom log; append-only content-addressed commit-DAG ledger
  (`methods/kotoba.cljc`), resume-safe, deterministic (caller supplies tx-id + as-of).
- Mirrors the mio pattern (analyze/datoms/coverage + kotoba + autorun). Keep the ledger
  machinery byte-identical to mio's so the family stays consistent.
- Tests: `./20-actors/tawami/run_tests.sh` (babashka). Keep green before commit.

## Suite

backbone = 澪 mio (verification). tawami submits flexibility-use claims to mio; verified
outcomes route to :reward (1 SBT=1 vote) and to hikari for actuation under Council gate.
