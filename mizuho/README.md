# mizuho (水穂) — Non-profit Religious-Corp Water + Sanitation Substrate

**DID**: `did:web:mizuho.etzhayyim.com`
**Namespace**: `com.etzhayyim.mizuho.*`
**ADR**: ADR-2605263100 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 5 Lexicon skeletons
**Cross-actor pairs**: mitsuho (irrigation) / hagukumi (daily-living) / iyashi (clinical) / yakushi (WFI feed) / tatekata (MEP) / hodoki (greywater recovery) / hikari (edge power)

## Naming-collision Note (IMPORTANT)

`mizuho` (水穂 — water + ear-of-rice) is a **romanization-homophone**
with the existing `mitsuho` (瑞穂 — food/agriculture, ADR-2605261015).
Both Japanese characters can read "mizuho" in standard romanization.

**Disambiguation**:
- Filesystem: `20-actors/mizuho/` (this) vs `20-actors/mitsuho/` (food)
- DID: `did:web:mizuho.etzhayyim.com` (this) vs `did:web:mitsuho.etzhayyim.com` (food)
- Lexicon namespace: `com.etzhayyim.mizuho.*` (this) vs `com.etzhayyim.mitsuho.*` (food)

User explicitly proposed `mizuho (水穂)` in the gap audit row 4; this
ADR follows verbatim.

## Overview

Religious-corp water + sanitation substrate. Community-scale only.
Upstream infrastructure prerequisite for:

- **mitsuho** — agricultural irrigation water supply
- **hagukumi** — daily-living water (cooking + hand-hygiene + bathing)
- **iyashi** — clinical-grade water (hand-hygiene + sterile reprocessing)
- **yakushi** — water-for-injection feed (pre-treatment; yakushi
  handles final pharma-grade WFI)

Without mizuho, religious-corp depends entirely on municipal utilities
— bringing vendor data-sovereignty exposure, mandatory fluoridation
tension, single-use plastic bottle distribution chains, and inability
to operate closed-loop greywater recycling at religious-corp
facilities.

## Identity (CRITICAL — IMMUTABLE)

- **Community-scale only** (G3 / N1) — per-source service population
  ≤2,500 at R2, ≤25,000 cumulative at R3. NOT a large municipal
  utility at any phase.
- **NO commercial water utility software** (G4) — Veolia / Suez /
  American Water / Aquarion / Évian (Danone) / Nestlé Pure Life /
  Beck Water / Trojan UV proprietary control systems PROHIBITED per
  Charter Rider §2(e) anti-gatekeeping + §2(c) vendor data-sovereignty
  exposing water-quality + member-consumption posture.
- **NO bottled water vendor** (G5 / N3) — single-use plastic PROHIBITED
  per Charter §1.13 Wellbecoming + multi-gen priority. Closed-loop
  reusable container ONLY where containerized delivery unavoidable
  (future kazaori disaster relief coordination).
- **NO mandatory fluoridation** (G6) — per-member consent required;
  anti-paternalism invariant. Naturally fluoridated source waters
  reported per-source; no addition by mizuho.
- **Greywater recycling MANDATORY for new construction** (G10) —
  Wellbecoming closed-loop invariant; `iyashi.clinicFacilityAttestation`
  cross-checks `greywater-recycling-attested=true` at R3.
- **Water-source rights waqf-equivalent inalienability** (G11) — per
  ADR-2605192245 Land Registry doctrine extended to water rights;
  water-rights trading PROHIBITED; water is constitutional commons.
- **NO payroll for operators** (G12) — operators are vocation-flow
  L5 stewards per Liberation Ladder (cross-actor enforcement with
  chigiri.stewardLaborAttestation + toritate.ledgerEntry.category
  enum exclusion).
- **Murakumo-only inference** (G7) — water-quality anomaly detection
  via judah LiteLLM; proprietary AI (Xylem Insights / Bentley
  Hydrologic) PROHIBITED.

## 6 Pregel Cells (R0 path-reserved)

All cells path-reserved under `40-engine/kotoba/crates/kotoba-kotodama/cells/mizuho_*/`.
Cell modules created at R1 ratification, import-time
`RuntimeError("mizuho R0 scaffold: activate via Council ADR + R1 ratification + water-source quality baseline established")`.

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `mizuho_potable_water_supply` | dan | continuous | source state + distribution → waterQualityAttestation |
| `mizuho_wastewater_treatment` | dan | continuous | discharge measurement → wastewaterDischargeAttestation |
| `mizuho_stormwater_management` | dan | event (rainfall) | catchment state → stormwater capture record |
| `mizuho_greywater_recycling` | dan | continuous | greywater capture + treatment → waterQualityAttestation (reuse-grade) |
| `mizuho_irrigation_supply` | dan (mitsuho-paired) | continuous | irrigation dispatch → mitsuho cross-actor |
| `mizuho_clinical_grade_water_supply` | dan (L4 triad-paired) | continuous | clinical-grade dispatch → iyashi + hagukumi + yakushi cross-actor |

## 5 Lexicons under `com.etzhayyim.mizuho.*`

| Lexicon | Purpose |
|---|---|
| `waterQualityAttestation` | Per-source / per-period quality test (WHO microbiological + chemical + radiological + physical) |
| `wastewaterDischargeAttestation` | Per-discharge event; G9 jurisdictional permit compliance |
| `waterSupplySourceRegistry` | Per-source registry (well/spring/captured rainwater/partner feed); Land Registry waqf cross-link |
| `waterContaminationIncident` | Anomaly / contamination event; severity enum; chigiri.disputeMediation routing if critical |
| `silenMizuhoReview` | Quarterly Wellbecoming + closed-loop ratio + multi-gen consumption review |

See `/00-contracts/lexicons/com/etzhayyim/mizuho/README.md`.

## Constitutional Gates (G1–G12)

See ADR-2605263100 §5. Key:

- **G3** Community-scale only
- **G4** NO commercial water utility software
- **G5** NO bottled water vendor (single-use plastic prohibited)
- **G6** NO mandatory fluoridation (per-member consent)
- **G10** Greywater recycling MANDATORY for new construction
- **G11** Water-source rights waqf-equivalent inalienability
- **G12** NO payroll for operators (vocation-flow L5)

## Non-Goals (N1–N12)

See ADR-2605263100 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Council + ≥1 licensed-water-engineer + Land Registry source baseline | 2 core cells + 1 pilot source ≤50 households |
| **R2** | post-R1 + 30-day public + 3 site attestations | +3 cells + ≤500 households + ≤200 ha irrigation |
| **R3** | post-R2 + Council Lv7+ + clinical-grade certification | +1 cell + ≤2,500 households + ≤25,000 cumulative + L4 Care Tier clinical-grade dispatch |

## Related Files

- `/20-actors/mizuho/manifest.jsonld`
- `/20-actors/mizuho/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/mizuho/` (5 Lexicons + README)
- `/90-docs/adr/2605263100-mizuho-water-sanitation-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605192245-etzhayyim-global-land-sovereignty.md` — G11 source
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — cross-actor clinical
- `/CHARTER-RIDER.md` §2(e) + §2(c) + §1.13 — G4 + G5 sources
- `/CLAUDE.md` — Status table row 71
