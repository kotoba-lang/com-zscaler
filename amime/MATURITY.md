# amime 網目 — maturity scorecard

**ADR-2606212020** · clj-native · status **R0** (green).

## R0 checklist (15/15)

- [x] manifest.edn (gates G1–G8, non-goals N1–N5)
- [x] ontology (EAVT schema + enums + negative space)
- [x] synthetic seed (6 sites / 7 links, surplus + deficit + storage)
- [x] mesh solve (deterministic single-hop transportation)
- [x] transmission-loss accounting (`sent = delivered + loss`)
- [x] curtailment + unserved accounting
- [x] N-1 contingency (critical-link identification)
- [x] import-dependence (SPOF map)
- [x] markdown resilience report (map-not-target framing)
- [x] amime-native ledger datoms (`emit/datoms`)
- [x] content-addressed append-only ledger (`kotoba.cljc`, verify-chain)
- [x] deterministic idempotent-by-content heartbeat (`autorun.cljc`)
- [x] kaname-facing `:energy` mirror export (committed `out/energy-sos.kotoba.edn`)
- [x] G1 commons-not-market enforced by construction + test
- [x] tests green (11 tests / 52 assertions)

## R1 worklist

- [ ] multi-hop routing (flow transits a hub)
- [ ] AC power-flow (reactive / voltage)
- [ ] storage as a time-coupled state across beats
- [ ] live-site ingest (behind G7/G8)
- [ ] mio Flowrate bridge + kaname live `:energy` join in the multi-mirror SoS run
