# 20-actors/iyashi — CLAUDE.md

## Identity

- **Name**: iyashi (癒 — 癒し / 癒える = healing)
- **DID**: `did:web:iyashi.etzhayyim.com`
- **ADR**: ADR-2605263000 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605261000 (Liberation Ladder — L4 Care Tier gate)
- **L4 Care Tier siblings**: yakushi (pharma mfg ADR-2605250500..615) / mitate (diagnosis routing ADR-2605260100) / hagukumi (daily-living ADR-2605261030)
- **Status**: R0 scaffold — 6 cells path-reserved (import-time RuntimeError per G2 privacy invariant) + 7 Lexicon skeletons
- **Form**: 任意団体 internal clinical care provider substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

iyashi is **clinical encounter provider procedural + attestation
substrate**, NOT a state-licensed medical entity. Individual providers
carry state license per jurisdiction; iyashi handles the procedural +
attestation layer. Five discipline boundaries are structural:

1. **Encrypted envelope MANDATORY (G2)** — `clinicalEncounterAttestation`
   + `chronicCareContinuityRecord` MUST carry `encryptedPayloadCid`;
   plaintext content fields rejected at schema layer (R1
   `additionalProperties: false`). Clinical PHI is the most sensitive
   observation class in religious-corp; structural enforcement.
2. **No commercial EHR (G11)** — Epic / Cerner / Athena / Allscripts /
   NextGen / eClinicalWorks / Greenway / Practice Fusion PROHIBITED
   per Charter Rider §2(e) anti-gatekeeping + §2(c) covert-ops vendor
   concern.
3. **No insurance billing (G13)** — funded via Public Fund grant +
   sliding-scale donation only. State insurance integration OUT OF
   SCOPE at all phases.
4. **No payroll for providers (G14)** — providers are vocation-flow
   L5 stewards per Liberation Ladder L0..L6; toritate.ledgerEntry.category
   enum excludes payroll/wage/salary/bonus/commission.
5. **Murakumo-only inference (G12)** — clinical decision support via
   judah LiteLLM only; vendor clinical-AI (Microsoft Nuance DAX /
   Abridge / Suki AI / Augmedix) PROHIBITED.

## Architecture

6 Pregel cells, all privacy-first, structurally encrypted:

```
primary_care_encounter ──┐
chronic_care_followup ────┤── encrypted XChaCha20 envelope per ADR-2605181100
acute_first_line ─────────┤    (mitate G5 emergency keyword cross-actor pair)
vaccination_administration ┘

provider_lifecycle ──── reuben (event; Council ≥3 attestation)
clinic_facility ─────── reuben (annual; facility standards)
```

Each cell = 1 Pregel graph. Cells communicate via lexicon records on
MST (`com.etzhayyim.iyashi.*`). All cell modules at R0 are import-time
`RuntimeError`.

## Privacy Invariant (CRITICAL)

Clinical PHI handling is the most sensitive observation class in
religious-corp. Structural enforcement (not policy):

1. `clinicalEncounterAttestation` + `chronicCareContinuityRecord`
   schemas **require** `encryptedPayloadCid` field; **reject** any
   plaintext content field via JSON Schema additionalProperties=false
   (at R1).
2. R0 cells raise `RuntimeError` on import specifically to **prevent
   accidental plaintext data flow** before R1's encrypted-record
   framework is Council-attested production-ready.
3. Patient identity uses 30-day rotating pseudonym DID per ADR-
   2605181200 (no stable identifier for adversary correlation).
4. Video stream firmware enforcement: telepresence transient-only,
   never write video frames to disk (G3).

## Provider Classification (G14) — Cross-actor with chigiri + toritate

Providers are vocation-flow L5 stewards per Liberation Ladder L0..L6:

- `providerAttestation` Lexicon links to `chigiri.stewardLaborAttestation`
  with `lLevel = "L5"` + `employmentRelation = "vocation-flow"`;
- `toritate.ledgerEntry.category` enum DELIBERATELY excludes
  `payroll`/`wage`/`salary`/`bonus`/`commission` — provider
  compensation flows as `vocation-flow` + `grant` (one-time Public
  Fund grant for setup) + `reimbursement` (out-of-pocket expenses);
- External employment parallel relationships may exist (provider
  holding separate hospital position) — those are EXTERNAL relations
  tracked in `chigiri.stewardLaborAttestation.externalEmployerDid`.

## R1 Activation Triggers

1. ADR-2605263000 Council Lv6+ ≥3 ratify;
2. ≥1 licensed-MD on Council medical advisory (Bootstrap Council Seat
   2-5 RFP must surface a willing candidate);
3. ADR-2605181100 encrypted-record framework production-deployed in
   CI;
4. Provider onboarding pipeline (medical-license-cite verification
   procedure + Council-attestation workflow) Council-reviewed;
5. Cross-actor mitate G5 emergency-keyword lexicon production-deployed;
6. chigiri R1 active (cross-actor stewardLaborAttestation read
   dependency).

## R1 Cell Activation Order

1. `iyashi_provider_lifecycle` (must precede patient encounters; no
   patient-bearing cell can activate before provider registry exists);
2. `iyashi_primary_care_encounter` (≤20 patients pilot, single clinic
   site).

R2 adds chronic_care_followup + vaccination_administration +
acute_first_line + clinic_facility.

R3 adds quarterly silenIyashiReview cycle + community-scale 50 clinic
sites.

## Cross-actor Relationships

### L4 Care Tier triad (existing)

- **yakushi** (pharma manufacturing) — Sukoyaka cold-chain delivery
  for vaccination + prescription supply;
- **mitate** (diagnosis routing) — diagnosis ↔ clinical encounter
  handoff; G5 emergency keyword lexicon shared (G10);
- **hagukumi** (daily-living care) — chronic_care_followup pairs with
  hagukumi.chronic_continuity for non-medical daily-life adherence
  support.

### Funding + procedural peers

- **toritate** (read) — donation + Public Fund grant accounting for
  iyashi operations;
- **chigiri** (read) — `consentRecord` + `stewardLaborAttestation`;
  `chigiri.member_onboarding` for Adherent SBT verification;
- **Public Fund Safe** (← grant) — operational funding via Council
  Lv6+ ≥4/7 approval (no insurance billing per G13).

### Future cross-actors

- **kokoro** (mental health, gap audit row 9) — postnatal mood
  screening + chronic mental health continuity;
- **shidemori** (memorial + cemetery, gap audit row 10) — end-of-life
  clinical attestation handoff.

## Build & Deploy

**R0 status**: Scaffold only. All cells RuntimeError on import
(intentional — prevents plaintext PHI data flow).

**Smoke test** (import-only; R0 cells deliberately fail import):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.iyashi_primary_care_encounter import _r0_marker" 2>&1 | grep "R0 scaffold"
# ... similar for all 6 iyashi_* cells
```

Expected: all 6 imports raise `RuntimeError` with "R0 scaffold" message.

## Related Files

- `/20-actors/iyashi/manifest.jsonld`
- `/20-actors/iyashi/README.md`
- `/00-contracts/lexicons/com/etzhayyim/iyashi/` (7 Lexicon JSONs + README)
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605181100-mst-encrypted-records-signal-keywrap.md` — privacy envelope
- `/90-docs/adr/2605181200-mst-encrypted-metadata-leak-reduction.md`
- `/90-docs/adr/2605192145-etzhayyim-public-fund-architecture.md` — funding source
- `/90-docs/adr/2605215000-etzhayyim-inference-murakumo-only-no-runpod.md` — G12 inference
- `/90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md` — cross-actor diagnosis
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — L4 gate
- `/90-docs/adr/2605261030-hagukumi-care-tier-b-actor-r0.md` — sibling R0 pattern
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — chigiri cross-actor
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — toritate cross-actor
- `/CHARTER-RIDER.md` — G11 commercial EHR prohibition source
- `/CLAUDE.md` — Religious-corp status table
