# 20-actors/mizuho — CLAUDE.md

## Identity

- **Name**: mizuho (水穂 — 水 water + 穂 ear-of-rice/spike)
- **DID**: `did:web:mizuho.etzhayyim.com`
- **ADR**: ADR-2605263100 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605192100 (Mission Charter — Wellbecoming + multi-gen)
- **Naming collision**: ROMANIZATION-HOMOPHONE with `mitsuho` (瑞穂; food/agriculture, ADR-2605261015). Filesystem + DID disambiguates; user explicitly proposed `mizuho (水穂)` in gap audit row 4.
- **Status**: R0 scaffold — 6 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal water + sanitation substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

mizuho is **community-scale water + sanitation infrastructure
substrate**, NOT a large municipal utility and NOT a commercial water
vendor. Seven discipline boundaries are structural:

1. **Community-scale only (G3 / N1)** — per-source service population
   ≤2,500 at R2, ≤25,000 cumulative at R3.
2. **No commercial water utility software (G4)** — Veolia / Suez /
   American Water / Aquarion / Évian (Danone) / Nestlé Pure Life /
   Beck Water / Trojan UV PROHIBITED per Charter Rider §2(e) + §2(c).
3. **No bottled water vendor (G5)** — single-use plastic PROHIBITED
   per Charter §1.13 Wellbecoming + multi-gen priority.
4. **No mandatory fluoridation (G6)** — per-member consent required.
5. **Greywater recycling MANDATORY for new construction (G10)** —
   Wellbecoming closed-loop invariant; cross-actor enforced by
   iyashi.clinicFacilityAttestation greywater-recycling-attested=true.
6. **Water-source waqf-equivalent inalienability (G11)** — per
   ADR-2605192245 Land Registry doctrine; water-rights trading
   PROHIBITED.
7. **No payroll for operators (G12)** — vocation-flow L5 stewards.

## Architecture

6 Pregel cells, all read source/distribution state continuously OR
event-driven:

```
potable_water_supply ────────┐
wastewater_treatment ────────┤
stormwater_management ───────┤── dan (continuous + rainfall-event)
greywater_recycling ─────────┤
irrigation_supply (mitsuho) ─┤
clinical_grade_water_supply ─┘    (iyashi + hagukumi + yakushi triad)
```

All cell modules at R0 are import-time `RuntimeError`. R1 activation
requires per-source water-quality baseline test on file.

## Greywater Mandatory Invariant (G10) — Cross-actor enforcement

`iyashi.clinicFacilityAttestation` field `greywaterRecyclingAttested`
MUST be `true` for any new iyashi clinic facility post-R2 mizuho
activation. Similarly `tatekata` MEP plumbing standards (future R1)
will require greywater-loop in any new community-scale building per
mizuho G10 cross-link.

## Water-source Inalienability (G11) — Extension of Land Trust Doctrine

`waterSupplySourceRegistry.landRegistryCid` REQUIRED. Source rights
(wells / springs / captured rainwater) honor waqf-equivalent
inalienability per ADR-2605192245:

- NO water-rights transfer / sale / lease;
- NO water-as-commodity trading market within religious-corp;
- Source rights belong to Land Trust permanently;
- Cross-jurisdictional water-rights legal framework via chigiri
  procedural attestation.

## Naming Disambiguation (Operations Note)

When humans or automated tooling reference "mizuho" in plain text,
context determines which actor:
- Water + sanitation = `mizuho` (water = water-related infrastructure context)
- Food + agriculture = `mitsuho` (food, harvest, crop context)

If ambiguous, prefer full path: `20-actors/mizuho/` vs
`20-actors/mitsuho/` OR DID: `did:web:mizuho.etzhayyim.com` vs
`did:web:mitsuho.etzhayyim.com`.

A future renaming wave (if Council Lv6+ approves) might change one
of the two to a clearly-distinct romanization (e.g., `mizuho` →
`midu` or `kannami` for water; `mitsuho` → `mizuho-ag` for food).
Out of scope for R0.

## R1 Activation Triggers

1. ADR-2605263100 Council Lv6+ ≥3 ratify;
2. ≥1 licensed-water-engineer on Council infrastructure advisory
   (Bootstrap Council Seat 2-5 RFP candidate);
3. Land Registry water-source-rights baseline (≥1 well / spring
   source registered with waqf inalienability attested);
4. Per-source baseline water-quality test on file (third-party
   laboratory; Council-attested);
5. chigiri R1 active (cross-actor stewardLaborAttestation read
   dependency for operator L5 classification).

## R1 Cell Activation Order

1. `mizuho_potable_water_supply` (lowest-risk; community-scale
   pilot 1 source ≤50 households);
2. `mizuho_wastewater_treatment` (paired; closed-loop discipline
   from R1 — single pilot site).

R2 adds stormwater / greywater / irrigation (mitsuho pair) cells.

R3 adds clinical_grade_water_supply (L4 Care Tier triad).

## Build & Deploy

**R0 status**: Scaffold only. R0 cells RuntimeError on import.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.mizuho_potable_water_supply import _r0_marker" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/mizuho/manifest.jsonld`
- `/20-actors/mizuho/README.md`
- `/00-contracts/lexicons/com/etzhayyim/mizuho/` (5 Lexicons + README)
- `/90-docs/adr/2605263100-mizuho-water-sanitation-tier-b-actor-r0.md`
- `/90-docs/adr/2605192245-etzhayyim-global-land-sovereignty.md` — G11
- `/90-docs/adr/2605261015-mitsuho-food-agriculture-tier-b-actor-r0.md` — naming-collision sibling
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — cross-actor clinical-grade
- `/CHARTER-RIDER.md` §2(e) + §2(c) + §1.13 — G4 + G5 sources
- `/CLAUDE.md` — Status table row 71
