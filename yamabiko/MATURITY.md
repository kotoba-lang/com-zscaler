# yamabiko 山彦 — Maturity

**Stage: R0** (scaffold) — ADR-2605252600. Civilian Shinkansen-class high-speed rail
manufacturing (sarutahiko's rail sibling). Open ATP/ATO/traction firmware, propulsion
electrification, no ads / no face recognition, ≤320 km/h + ≤GoA-3, EoL ≥90% (kanayama loop).

| Dimension | State |
|---|---|
| Lexicons | ✅ 9 under `com.etzhayyim.yamabiko.*` (carbody/bogie/interior/tractionElectrical/finalAssembly/dynamicTest/acousticEmissions/homologation/silenRailReview) — rich const ledger |
| Cells | ✅ 9 path-reserved; homologation_binder now parses (syntax bug fixed, below) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) machine-readable |
| Tests | ✅ **3 suites green** — `methods/test_charter_gates.cljc` (**7**, added 2026-06-17) + homologation_binder parse smoke + `py/test_agent.py`; `./run_tests.sh` aggregates all |
| Methods | 🟡 cells R0 (`.solve()` Council-gated); offline engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **G1/N5 open firmware** — `tractionElectricalAttestation` const `g1Enforcement` +
  `n5Enforcement` + `g7Enforcement` = "active", `proprietaryNdaPresent=false`; requires
  `openSourceVerification` (no proprietary signalling NDA).
- **G7 propulsion + N7 GoA cap** — `propulsionType` is exactly the 6 electrified/H₂/NH₃ types
  (no diesel); `atoLevel` is {GoA-1,2,3} only (GoA-4 = N7 unrepresentable).
- **N6 no ads + N8 no face recognition** — `interiorAttestation` const
  `n6AdvertisingPresent=false` + `n8FaceRecognitionPresent=false` + `g5Trilingual=true`;
  `finalAssemblyAttestation` const `n6AdvertisingFreeAccept=true`.
- **G12 speed cap** — `dynamicTestRecord` const `maxSpeedLimitKmh=320` + `g12KpiCheck`.
- **G2 homologation** — `homologationRecord` const `openTrainsetRegistry=true` +
  `g2Compliant=true`; requires `kotoba-datomicAnchor` + `trainsetDid` + `authorityReview`.
- **G4 witness quorum** — carbody / bogie / finalAssembly require `attestingRobots`.

## Bug fixed this iteration (2026-06-17)

`cells/homologation_binder/{state_machine,cell}.py` had the same bad-rename **broken Python
identifier** as sarutahiko: `kotoba-datomicAnchor` / `transition_to_kotoba-datomic_anchored`
(hyphen = `SyntaxError`). Fixed the identifier sites → underscore. The record key string
`"kotoba-datomicAnchor"` was **kept** (it matches the `homologationRecord` lexicon's required
field name). Both files `ast.parse` OK.

> ⚠️ **Same bug still open** in `kanayama/cells/mass_balance_binder/` +
> `watatsumi/cells/class_certification_binder/` — follow-up.

## R0 → R1 gate

silenRailReview `r1-benchtop-mockup` + Council Lv6+ + rail-engineering SME; cell `.solve()`
stays R0-gated.

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `yamabiko.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
