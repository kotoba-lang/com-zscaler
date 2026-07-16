# kenchi 検地 — Maturity

**Stage: R0** (scaffold) — ADR-2606272030. External-market worldwide real-estate
valuation + transparency substrate, **NOT a brokerage / closed AVM / advice
renderer**. PROVENANCE-OR-SILENCE gate, derived/aggregate license discipline, and
the load-bearing **inalienable-land exclusion** (G5) that keeps it disjoint from
toritate's non-market commons-asset STOCK.

| Dimension | State |
|---|---|
| Lexicons | ✅ 4 under `com.etzhayyim.kenchi.*` (valuation / regionReport / provenanceAttestation / sourceLicense) |
| Cells | 🟡 6 path-reserved over the `com-junkawasaki/kenchi-clj` engine (R0) |
| Manifest | ✅ present (10 gates / 10 non-goals / R0–R3 roadmap) |
| Engine | ✅ real & tested upstream — `com-junkawasaki/kenchi-clj` (18 tests / 60 assertions green; live HM Land Registry + BIS + Common Crawl) |
| Tests | ✅ `methods/test_charter_gates.clj` — pins G3/G4/G5/G6 + N8 at the schema layer; `bb run_tests.clj` |
| Source registry | ✅ `valuation/v1-sources.json` (open authorities; flywheel recalibrates priors) |

## Charter gates pinned by the test

- **G3 PROVENANCE-OR-SILENCE** — `valuation` requires `provenance` + `nComps`(≥3) +
  `nAuthorities`(≥2) + `ciLoUsd/ciHiUsd`; `provenanceAttestation.verdict` offers
  `withheld-mrv` / `insufficient-evidence`, not only `published`.
- **G4 derived/aggregate license** — `valuation.license` and `regionReport.license`
  are **exactly** {open, derived-only}; `sourceLicense.licenseClass` still
  represents `restricted` (a restricted source contributes but never publishes raw).
- **G5 inalienable-land exclusion** — `valuation.assetClass` is **exactly**
  {external-market}; the manifest pins G5 (citing ADR-2605192245) + N1.
- **G6 NO-PII** — no `owner`/`person`/PII property is representable on `valuation`.
- **N8 no single-source oracle** — `provenanceAttestation` requires `nAuthorities`
  + `nComps` + `verdict`.

## R0 → R1 gate

Council Lv6+ ≥3 baseline + the 3 core cells (ingest / fusion / provenance_governor)
wired on naphtali + the first open-authority valuations published derived/aggregate
(MST publish + IPFS pin ≥2). The engine already runs live upstream; R1 is the
constitutional ratification + on-mesh deployment.
