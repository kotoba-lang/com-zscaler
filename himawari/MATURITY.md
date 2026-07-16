# himawari 向日葵 — Maturity

**Stage: R0.1** — ADR-2606021200. Solar-grade c-Si PV manufacturing (vertical integration
that structurally closes hikari's feedstock-provenance gap). Composes sarutahiko loader +
kami-autodrive GNC + giemon AGV (does not re-implement logistics).

| Dimension | State |
|---|---|
| Lexicons | ✅ 7 under `com.etzhayyim.himawari.*` (polysiliconProvenance/waferBatch/cellBatch/module/loading/outboundManifest/silenHimawariReview) — rich const + enum ledger |
| Cells | ✅ 7 cell solvers implemented (R0.1; `.solve()` real, no RuntimeError stubs) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) machine-readable |
| Tests | ✅ **charter-gate 7 green** (`methods/test_charter_gates.cljc`, added 2026-06-17) **+ 88 pure-logic cell tests** (pre-existing); `./run_tests.sh` runs the charter suite |
| Methods | ✅ cell solvers; live Pregel/Murakumo wiring + kotoba materialization = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **G2 feedstock provenance** — `polysiliconProvenanceAttestation` requires `regionCode` +
  `originRegionAttestationCid` + `chainOfCustody` + `sourcingAuditCid` (no XUAR/forced-labor
  polysilicon); `feedstockGrade` ∈ {solar-grade-6N, solar-grade-6N+, recycled-kerf}.
- **G3 F-gas abatement** — `cellBatchRecord` requires `gasAbatementCid` + `minDreFloor` +
  `meetsG3Floor` + `uncontrolledVenting`.
- **G12 no external sale** — `outboundManifest.destinationKind` const `hikari-install-site`
  (no external commercial PV sale).
- **anti-weaponization + no-server-key** — `outboundManifest` declares `weaponizationPayload`;
  `moduleAttestation` declares `serverHeldKey` (gate hooks; values cell-enforced).
- **G11 traceability** — `moduleAttestation` requires `provenanceChainDigest` + `signedDigest`
  + `signer`; signing `alg` ∈ {content-binding-sha256, ed25519}.
- **G4 witness-signed** — cellBatch / module / waferBatch require `attestingRobots` + `signature`.

## R0 → R1 gate

silenHimawariReview `r1-module-assembly-activation` + Council Lv6+ + hikari energy-budget
coupling contract; live Pregel/Murakumo runtime wiring deferred to R1.

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `himawari.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
