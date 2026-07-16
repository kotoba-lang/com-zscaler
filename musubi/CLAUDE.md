# 20-actors/musubi — CLAUDE.md

## Identity

- **Name**: musubi (結 — tie/knot/bind/connect; Shinto 産霊 generative force tying threads of life)
- **DID**: `did:web:musubi.etzhayyim.com`
- **ADR**: ADR-2605263400 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605262700 (chigiri — TIGHT PAIR)
- **Status**: R0 scaffold — 6 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal covenant ceremony substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

musubi is **ceremony performance substrate**, NOT a state-licensed
religious entity and NOT a clergy-class-bearing organization. Six
discipline boundaries are structural:

1. **No clergy class (G3+N2)** — Reformed 万人祭司 invariant;
   `officiantAttestation.officiantClass` enum DELIBERATELY excludes
   "clergy"/"ordained"/"priest"/"bishop"/"minister-with-ecclesiastical-
   authority"; the valid value is "community-witnessed-competent".
2. **No mandatory ritual attendance (G4)** — free conscience.
3. **No commercial wedding/funeral industry software (G6+N6)** —
   Aisle Planner / Honeybook / The Knot / WeddingWire / Zola / SRS
   Computing / Aldor / Wilbert / Frazer Consultants PROHIBITED per
   Charter Rider §2(e)+§2(c).
4. **No bride price / dowry (G7+N7)** — anti-coercive ceremony
   economy.
5. **No video without per-party consent (G8)**.
6. **No sacrament-as-transubstantiation (G9+N4)** — Sola Scriptura
   + Reformed memorial view; cross-doctrinal Wellbecoming priority.

Cross-actor invariants:
- **G11 chigiri.covenantAttestation emit MANDATORY** for marriage /
  naming / funeral / vocation-vow / rededication (NOT seasonal_communal);
- **G12 vocation-flow L5 officiants** (no payroll);
- **G10 multi-generational invariant** (Charter §1.7 多世代).

## Architecture (chigiri tight pair)

musubi performs; chigiri attests. The two-step is structural:

```
Member request (e.g., marriage)
        │
        ▼
musubi cell (e.g., marriage_ceremony)
  ▸ verify party consent (G5)
  ▸ verify officiant L5 + community-witnessed-competent (G3 + G12)
  ▸ verify witnesses multi-gen (G10)
  ▸ perform ceremony (off-chain action)
  ▸ emit ceremonyPerformanceAttestation
        │
        ▼ (G11 cross-emit MANDATORY)
chigiri.covenant_ceremony cell
  ▸ verify Charter Rider scan PASS
  ▸ emit chigiri.covenantAttestation (ceremonyType=marriage)
  ▸ if SBT issuance/burn: ChartersComplianceRegistry attestation
        │
        ▼
kotoba-datomic block CID + Land Registry cross-link (if applicable)
```

All 6 musubi cell modules at R0 are import-time `RuntimeError`. R1
activation requires ≥3 officiant baseline attestations + community-
witness registry initialized + chigiri R1 active (the cross-emit
dependency).

## Officiant Attestation Pattern (G3 + G12 enforcement)

L5 vocation-flow steward classification for officiants:

1. Candidate undergoes apprenticeship (informal; community-attested);
2. ≥3 prior officiants attest community-witnessed competence
   (`officiantAttestation` chain of ≥3 attesters);
3. Council Lv6+ ≥3 attests classification at L5 vocation-flow per
   chigiri.stewardLaborAttestation;
4. Officiant performs ceremonies; each ceremony emits
   ceremonyPerformanceAttestation citing officiant CID;
5. NO ordination ceremony separate from vocation_vow_ceremony
   (G3 — there is no special clergy class; community-witnessed
   competence IS the officiant status).

This is structurally distinct from apostolic-succession / sacramental-
monopoly models.

## Anti-Coercive Ceremony Economy (G7 + N7)

Gifts at ceremonies are permitted; coercive economic transfer
embedded in ceremony structure is constitutionally rejected:

- Bride price (payment from groom's family to bride's family as
  precondition of marriage) → PROHIBITED;
- Dowry (payment from bride's family to groom's family; sometimes
  conflated with bride price) → PROHIBITED;
- Bride purchase / arranged-marriage-with-coercion → PROHIBITED;
- Gift exchange in non-coercive cultural / religious tradition
  (お祝い金 / お見舞い / 香典) → PERMITTED with member opt-in;
- Public Fund grant for ceremony venue / supplies → PERMITTED
  per ADR-2605192145 Council Lv6+ approval (cross-actor toritate);
- Officiant compensation = NONE per G12 (vocation-flow L5).

silenMusubiReview audits anti-coercive compliance per ceremony
category.

## Multi-Generational Invariant (G10)

Charter §1.7 prioritizes 多世代 inclusion. silenMusubiReview tracks
cohort ratio per ceremony category:

- Marriage: bride/groom families across generations represented;
- Naming: grandparents + parents + community elders attending;
- Funeral: cross-generational mourners;
- Seasonal: community-wide multi-gen attendance.

Target ratio per ceremony category (R3 audit benchmarks):
- ≥10% under-18 attendance per ceremony where appropriate;
- ≥10% over-65 attendance per ceremony;
- ≤80% middle-adults-only (counter-balances modern individualism).

## Cross-Doctrinal Wellbecoming (G9 + N12)

musubi accommodates members from multiple Christian / Reformed /
Anglican / Baptist / Methodist / nondenominational / cross-tradition
backgrounds within Charter §1.7 + §1.13 boundaries:

- NO single-doctrinal-stance monopoly (N12);
- NO transubstantiation imposed (G9);
- NO mandatory confessional formula;
- Cross-doctrinal Wellbecoming priority over theological monoculture.

This is operationalized by:
- Ceremony content review board per Charter §1.13;
- Officiant attestation includes doctrinal-tradition self-declaration
  (transparency, not gatekeeping);
- Member can request officiant of compatible tradition (matching
  service, not enforcement).

## R1 Activation Triggers

1. ADR-2605263400 Council Lv6+ ≥3 ratify;
2. ≥3 officiant baseline attestations on file
   (different from clergy ordination per G3 — community-witnessed-
   competence attestations);
3. Community-witness registry initialized;
4. chigiri R1 active (cross-actor covenantAttestation cross-emit
   dependency);
5. ≥1 ceremony-experienced advisor on Council (Bootstrap Council
   Seat 2-5 RFP).

## R1 Cell Activation Order

1. `musubi_marriage_ceremony` (most-requested; pairs with chigiri
   covenant_ceremony cell directly);
2. `musubi_naming_ceremony` (pairs with chigiri.member_onboarding;
   used for new Adherent SBT issuance ritual).

R2 adds funeral_ceremony + vocation_vow_ceremony + rededication_ceremony.

R3 adds seasonal_communal_ceremony + silenMusubiReview cycle.

## Build & Deploy

**R0 status**: Scaffold only. R0 cells RuntimeError on import.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.musubi_marriage_ceremony import _r0_marker" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/musubi/manifest.jsonld`
- `/20-actors/musubi/README.md`
- `/00-contracts/lexicons/com/etzhayyim/musubi/` (5 Lexicons + README)
- `/90-docs/adr/2605263400-musubi-covenant-ceremony-tier-b-actor-r0.md`
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — TIGHT PAIR
- `/90-docs/adr/2605250200-l5-religious-marriage-cell.md` — existing Pregel-cell pattern
- `/CHARTER-RIDER.md` — license + Rider canonical text
- `/CLAUDE.md` — Status table row 73
