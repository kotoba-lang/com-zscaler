# atsurae 誂え — maturity scorecard

**ADR-2606212010** · clj-native · status **R0** (green).

## R0 checklist (14/14)

- [x] manifest.edn (gates G1–G7, non-goals N1–N5)
- [x] ontology (EAVT schema + enums + negative space)
- [x] synthetic seed (15-feature OSS-robotics mobility-base line)
- [x] structural validation (mandatory / xor / or / orphan)
- [x] cross-tree constraints (requires / excludes)
- [x] bounded variant enumeration (176 valid variants)
- [x] commonality (platform vs. variation points)
- [x] variation-point identification
- [x] variant → BOM derivation (→ uchiwake / open-kyber)
- [x] markdown product-line report (commons-not-license framing)
- [x] content-addressed append-only ledger (`kotoba.cljc`, verify-chain)
- [x] deterministic idempotent-by-content heartbeat (`autorun.cljc`)
- [x] G1/G2 commons-not-license + spec-only enforced by construction + test
- [x] tests green (11 tests / 41 assertions)

## R1 worklist

- [ ] SAT/BDD enumeration for large feature models (>16-wide or-groups)
- [ ] feature-model diff (commonality drift across product-line versions, as-of on the ledger)
- [ ] attribute features (numeric/cost constraints) feeding open-kyber cost accounting
- [ ] uchiwake GTIN binding for derived variants
