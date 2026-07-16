# 20-actors/wakai — CLAUDE.md

## Identity

- **Name**: wakai (和会 — harmony/reconciliation gathering; classical 互助会 mutual aid society)
- **DID**: `did:web:wakai.etzhayyim.com`
- **ADR**: ADR-2605263500 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605192145 (Public Fund — backstop source)
- **Status**: R0 scaffold — 6 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal mutual aid substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格; NOT state-licensed insurance entity)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

wakai is **member-to-member solidarity pool substrate**, NOT insurance
and NOT a financial product. Seven discipline boundaries are structural:

1. **NOT insurance (G3+N1)** — no premium-as-contract; no actuarial
   pricing; no claim adjudication; no policy denial; no underwriting.
2. **NO commercial insurance software (G4+N8)** — Guidewire / Duck
   Creek / Insurity / Sapiens / Majesco / SAP Insurance / Oracle
   Insurance / Lemonade / Hippo PROHIBITED per Charter Rider
   §2(e)+§2(c).
3. **NO commercial re-insurance (G5+N6)** — Munich Re / Swiss Re /
   SCOR / Hannover Re / Berkshire Hathaway Re PROHIBITED.
4. **NO investment return promise (G6+N2)** — Charter Rider §2(b)
   speculative finance prohibition; pool in USDC on Base L2 only;
   NO DeFi yield farming.
5. **NO pre-existing condition discrimination (G7)** — no
   underwriting; no exclusion.
6. **Voluntary + ability-scaled contribution (G8)** — Charter §1.7
   反個人主義.
7. **Community discernment distribution (G9)** — Council Lv6+ ≥3 +
   ≥3 community attestation; NOT claim adjudication.

## Architecture

6 Pregel cells all on asher node:

```
mutual_aid_pool_contribution ────┐
mutual_aid_distribution ──────────┤
emergency_pool_activation ────────┤── asher (continuous + event)
health_event_support ─────────────┤
public_fund_backstop_request ─────┤
pool_state_reporting ─────────────┘
```

All cell modules at R0 are import-time `RuntimeError`. R1 activation
requires initial pool seed + ≥3 community discernment witness
candidates on file.

## Charter Rider §2(b) Speculative Finance Discipline (G6)

The critical risk for any mutual aid substrate is drift toward
investment vehicle / DeFi yield farming / token speculation. G6
operationalizes Charter Rider §2(b) prohibition:

- Pool held in USDC on Base L2 (stable-asset only per
  ADR-2605172100);
- NO DeFi yield farming (no Aave / Compound / Curve / Uniswap LP
  positions);
- NO token speculation (no swaps to other tokens for "growth");
- NO investment return promise to contributors;
- `mutualAidContributionAttestation.investmentReturnPromised` const
  false structural enforcement;
- Stablecoin de-peg risk acknowledged; mitigation is community-
  funded not actuarially-funded.

## NOT-Insurance Boundary (G3)

The structural distinction from insurance is critical:

| Insurance | Mutual Aid (wakai) |
|---|---|
| Premium-as-contract | Voluntary contribution (G8) |
| Actuarial pricing | Ability-scaled (G8) |
| Claim adjudication | Community discernment (G9) |
| Policy denial | No denial (G7 anti-discrimination) |
| Underwriting | No underwriting (G7) |
| Pre-existing condition exclusion | No exclusion (G7) |
| ROI on premium | No investment return (G6) |
| Risk-transfer to re-insurer | Risk stays in community + Public Fund backstop (G5+G10) |
| Insurance commissioner registration | 任意団体 internal (N9) |

This is operationalized at schema layer via const fields in
mutualAidContributionAttestation + mutualAidDistributionAttestation
+ silenWakaiReview.

## Community Discernment Distribution (G9) — NOT Claim Adjudication

The discipline is operational, not policy:

1. Member experiences need event (health / disability / death-of-
   breadwinner / unemployment / disaster);
2. Member OR community-member-on-behalf submits need attestation
   (with member consent if member able);
3. Council Lv6+ ≥3 reviews need attestation (discerns need);
4. ≥3 community members attest need (witness chain;
   `communityDiscernmentAttestations` minLength 3);
5. Distribution from pool (no actuarial calculation; needs-based
   amount discerned by Council + community);
6. If pool insufficient, public_fund_backstop_request triggered
   (Council Lv6+ ≥4/7 per ADR-2605192145).

The discernment pattern (Council + ≥3 community) prevents claim-
adjudication drift while preserving accountability.

## Cross-Actor Coordination Patterns

### kazaori emergency pool (path-reserved cross-actor)

kazaori ADR-2605263200 path-reserved wakai as future cross-actor
for emergency mutual aid pooling. This actor realizes the cross-
actor. During Council-Lv6+-declared emergency:

1. kazaori emergency_water_supply + emergency_food_supply cells
   coordinate primary supply dispatch;
2. wakai emergency_pool_activation cell coordinates supplementary
   mutual aid pool dispatch (member-funded; complementary to
   kazaori main coordination);
3. Cross-actor mutualAidDistributionAttestation cross-link to
   kazaori.emergencySupplyDispatch.

### iyashi + hagukumi + yakushi health event triad

`health_event_support` cell consumes:
- iyashi.chronicCareContinuityRecord (chronic care gap funding);
- iyashi.clinicalEncounterAttestation (acute event support);
- hagukumi.careSessionAttestation (daily-living gap);
- yakushi medication supply records (medication gap).

Distribution routed need-based; G7 anti-discrimination invariant
preserved (no pre-existing condition exclusion across all 4
cross-actor health event types).

### toritate Public Fund backstop

`public_fund_backstop_request` cell:
1. wakai pool insufficient for current distribution requests;
2. Council Lv6+ ≥4/7 reviews backstop request;
3. Public Fund Safe disburses; toritate.ledgerEntry records;
4. Backstop disbursement recorded in `publicFundBackstopRequest`
   + cross-link to toritate ledgerEntry CID.

## R1 Activation Triggers

1. ADR-2605263500 Council Lv6+ ≥3 ratify;
2. Initial pool seed (Council Lv6+ ≥4/7 Public Fund seed grant);
3. ≥3 community discernment witness candidates on file
   (Bootstrap Council Seat 2-5 RFP);
4. chigiri R1 active (Adherent SBT verification + stewardLaborAttestation);
5. toritate R1 active (accounting + Public Fund backstop coordination).

## R1 Cell Activation Order

1. `wakai_mutual_aid_pool_contribution` (foundation; no distribution
   without contribution flow);
2. `wakai_pool_state_reporting` (transparency from day 1);
3. `wakai_mutual_aid_distribution` (basic distribution; ≤50 members
   pilot).

R2 adds health_event_support + public_fund_backstop_request.

R3 adds emergency_pool_activation (kazaori cross-actor).

## Build & Deploy

**R0 status**: `methods/pool.cljc` is a reference-impl engine (pure `validate-contribution` /
`validate-distribution` / `aggregate-pool-state` functions, `bb test:wakai` — 15 tests / 42
assertions green) — this is validation + aggregation ONLY, not a live pool. The Pregel CELLS
themselves (contribution / distribution / pool-state / backstop) are still unwired scaffold
and RuntimeError on import; wiring one to `methods/pool.cljc` + live kotoba-kotodama execution
is separate R1 work, Council+operator gated same as every other actor's R0→live boundary.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.wakai_mutual_aid_pool_contribution import _r0_marker" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/wakai/manifest.jsonld`
- `/20-actors/wakai/README.md`
- `/00-contracts/lexicons/com/etzhayyim/wakai/` (5 Lexicons + README)
- `/90-docs/adr/2605263500-wakai-mutual-aid-tier-b-actor-r0.md`
- `/90-docs/adr/2605192145-etzhayyim-public-fund-architecture.md` — backstop source
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — emergency cross-actor
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — health event cross-actor
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — accounting + backstop accounting
- `/CHARTER-RIDER.md` §2(b) + §2(e) + §2(c) — G6 + G4 + G5 sources
- `/CLAUDE.md` — Status table row 74
