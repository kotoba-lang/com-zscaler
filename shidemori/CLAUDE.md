# 20-actors/shidemori — CLAUDE.md (FINAL gap-closure)

## Identity

- **Name**: shidemori (死出守 — guardian of the death-journey; 死出 = 死出の旅 classical 万葉集 imagery + 守 = keeper)
- **DID**: `did:web:shidemori.etzhayyim.com`
- **ADR**: ADR-2605263800 (R0 scaffold, 2026-05-26; **FINAL gap-closure** of 10-actor 30min-loop wave)
- **Parent ADR**: ADR-2605192100 (Mission Charter — §1.7 多世代; §1.13 Wellbecoming; §1.15 non-eschatological)
- **Status**: R0 scaffold — 6 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal memorial + cemetery substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格; NOT state-licensed mortuary entity)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

shidemori is **community memorial + cemetery substrate**, NOT a
state-licensed mortuary entity and NOT a commercial cemetery
operator. 10 discipline boundaries are structural (matching kokoro
9-novenary; shidemori extends to 10-decennary):

1. **Charter §1.15 non-eschatological (G3)** — `memorialNftAttestation.afterlifeDoctrineImposed` const false; extends kataribe G4 to memorial domain.
2. **Cross-doctrinal Wellbecoming priority (G4)** — 4-actor maturity (musubi + kataribe + kokoro + shidemori).
3. **NOT state-licensed mortuary services (G5)** — 4-actor UPL-equivalent maturity (chigiri legal + iyashi clinical + kokoro mental health + shidemori mortuary).
4. **NO commercial memorial software (G6)** — Frazer Consultants / Tribute Center / FuneralOne / ASD / SRS Computing / Wilbert / Aldor / Adobe Cemetery Mgmt / Cremation Society Cremation Mgmt PROHIBITED (extends musubi G6).
5. **NO embalming chemicals (G7)** — Charter Rider §2(d) toxic chemistry; green burial + cremation only.
6. **NO surveillance-based mortuary (G8)** — Charter §2(c).
7. **Member burial-or-cremation directive (G9)** — free conscience.
8. **Land Trust waqf-equivalent cemetery rights (G10)** — 2-actor waqf-equivalent pattern (mizuho G11 water + shidemori G10 cemetery).
9. **NO payroll for guardians (G11)** — vocation-flow L5.
10. **chigiri.inheritanceChain emit MANDATORY (G13)** — when deceased had Adherent SBT.

## Architecture (all zebulun node)

6 Pregel cells, all zebulun:

```
memorial_nft_mint (musubi TIGHT) ─────────┐
cemetery_land_registry (Land Registry) ───┤
chinkon_annual_remembrance (musubi seasonal) ─┤── zebulun (event + annual + quarterly)
inheritance_handoff (chigiri TIGHT) ──────┤
external_mortuary_engagement (toritate UPL-equivalent) ┤
silen_shidemori_review ───────────────────┘
```

All cell modules at R0 are import-time `RuntimeError`. R1 activation
requires ≥3 guardian baseline attestations + ≥1 cemetery Land
Registry waqf-attested entry + musubi R2 active + chigiri R2 active.

## Charter §1.15 Non-Eschatological Discipline (G3) — Most Difficult Memorial Domain Application

Memorial domain is where Charter §1.15 non-eschatological invariant
is structurally most counter-cultural. Most Christian / Buddhist /
Shinto / etc. traditions have detailed afterlife cosmologies.
shidemori G3 discipline:

- `memorialNftAttestation.afterlifeDoctrineImposed` const false;
- Memorial honors the deceased without imposing afterlife doctrine;
- Members may privately hold any afterlife view;
- shidemori does NOT publish afterlife claims in memorial content;
- Cross-doctrinal Wellbecoming priority (G4) operationalizes this
  via member-directive-respecting memorial text generation.

This is the same discipline as kataribe G4 (non-eschatological tone)
extended into the memorial domain (where temptation to make
afterlife claims is highest).

## 4-Actor UPL-Equivalent Pattern Maturity (G5)

shidemori G5 completes a 4-actor UPL-equivalent pattern:

| Actor | UPL-equivalent gate | Domain |
|---|---|---|
| chigiri | G14 | Legal counsel |
| iyashi | N9 | Clinical care |
| kokoro | G3 | Mental health |
| **shidemori** | **G5** | **Mortuary services** |

The pattern: religious-corp does NOT provide state-licensed
professional services; community-witnessed-competent stewards
handle the substrate; external licensed professionals engaged via
Public Fund Council Lv6+ ≥4/7 when state-licensing legally
required.

## 2-Actor Waqf-Equivalent Pattern Maturity (G10)

shidemori G10 completes a 2-actor Land Trust waqf-equivalent pattern:

| Actor | Waqf-equivalent gate | Resource domain |
|---|---|---|
| mizuho | G11 | Water sources |
| **shidemori** | **G10** | **Cemetery land** |

The pattern: certain resource categories are inalienable per
ADR-2605192245 Land Trust doctrine. The 2-actor pattern is the
beginning of a generalized resource-inalienability discipline that
could extend to forests (future) / sacred sites (future) / etc.

## 4-Actor Cross-Doctrinal Wellbecoming Pattern Maturity (G4)

shidemori G4 completes a 4-actor cross-doctrinal Wellbecoming
priority pattern:

| Actor | Cross-doctrinal gate | Domain |
|---|---|---|
| musubi | G9+N12 | Covenant ceremony |
| kataribe | G6 | Press + publishing |
| kokoro | G12 | Mental health |
| **shidemori** | **G4** | **Memorial + cemetery** |

The pattern: religious-corp accommodates Christian / Buddhist /
Shinto / nondenominational / secular within Charter §1.13 + §1.15
boundaries. No single-tradition monopoly in any of these 4 domains.

## Bereavement Arc Cross-Actor Coordination

shidemori sits at the end of the bereavement arc:

```
musubi.funeral_ceremony  (ceremony performance)
  ↓
shidemori.memorial_nft_mint  (memorial NFT + chigiri.inheritanceChain G13)
  ↓
kokoro.grief_support  (multi-session grief continuity; musubi TIGHT)
  ↓
shidemori.chinkon_annual_remembrance  (annual cycle; multi-gen)
  ↓
kataribe.community_chronicle  (memorial obituary publication)
```

3-actor cross-actor coordination (musubi + kokoro + shidemori + kataribe)
covers the full bereavement arc from ceremony through annual
remembrance + community memory.

## R1 Activation Triggers

1. ADR-2605263800 Council Lv6+ ≥3 ratify;
2. ≥3 guardian baseline attestations on file (community-witnessed-
   competence; NOT state-licensed funeral director per G5);
3. ≥1 cemetery Land Registry waqf-attested entry (G10);
4. musubi R2 active (cross-actor funeral_ceremony TIGHT dependency);
5. chigiri R2 active (cross-actor inheritanceChain TIGHT dependency);
6. Cross-doctrinal advisor on Council (G4).

## R1 Cell Activation Order

1. `shidemori_memorial_nft_mint` (musubi TIGHT pair; depends on
   musubi R2 + ADR-2605181100 envelope production);
2. `shidemori_cemetery_land_registry` (Land Registry waqf
   inalienability;
   depends on ≥1 cemetery acquisition Public Fund Lv6+ ≥4/7);
3. `shidemori_inheritance_handoff` (chigiri TIGHT pair; G13 MANDATORY
   emit pattern).

R2 adds chinkon_annual_remembrance + external_mortuary_engagement.

R3 adds silen_shidemori_review cycle.

## Build & Deploy

**R0 status**: Scaffold only. R0 cells RuntimeError on import.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.shidemori_memorial_nft_mint import _r0_marker" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/shidemori/manifest.jsonld`
- `/20-actors/shidemori/README.md`
- `/00-contracts/lexicons/com/etzhayyim/shidemori/` (5 Lexicons + README)
- `/90-docs/adr/2605263800-shidemori-memorial-cemetery-tier-b-actor-r0.md`
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md` — TIGHT funeral_ceremony pair
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — TIGHT inheritanceChain pair
- `/90-docs/adr/2605263100-mizuho-water-sanitation-tier-b-actor-r0.md` — G11 waqf-equivalent sibling
- `/90-docs/adr/2605263700-kokoro-mental-health-tier-b-actor-r0.md` — grief continuity cross-actor
- `/90-docs/adr/2605263600-kataribe-press-publishing-translation-tier-b-actor-r0.md` — memorial publication cross-actor
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — mass-fatality memorial path-reserved cross-actor
- `/90-docs/adr/2605192245-etzhayyim-global-land-sovereignty.md` — G10 waqf source
- `/CHARTER-RIDER.md` §2(d) + §2(e) + §2(c) — G7 + G6 + G8 sources
- `/CLAUDE.md` — Status table row 77
