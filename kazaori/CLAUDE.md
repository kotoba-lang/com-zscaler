# 20-actors/kazaori — CLAUDE.md

## Identity

- **Name**: kazaori (風折 — wind-broken; storm-damaged tree branches; classical 万葉集 imagery)
- **DID**: `did:web:kazaori.etzhayyim.com`
- **ADR**: ADR-2605263200 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605192100 (Mission Charter — Wellbecoming + §1.12 + §2(c))
- **Force-separation sibling**: ADR-2605192315 (Transparent Force authorization — civilian/military boundary)
- **Status**: R0 scaffold — 6 cells path-reserved + 6 Lexicon skeletons
- **Form**: 任意団体 internal civilian disaster response substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

kazaori is **civilian-only emergency coordination substrate**, NOT a
state-licensed emergency services entity and NOT a military / armed
force responder. Six discipline boundaries are structural:

1. **Civilian-only (G5+N1)** — force authorization separate per
   ADR-2605192315 Transparent Force; kazaori MUST NOT coordinate
   with armed force actions during disaster response.
2. **No commercial disaster management software (G4)** — Veoci / NC4
   / Crisis Track / Everbridge / OnSolve / SAP DR / Microsoft DR Hub
   / IBM Crisis Response PROHIBITED per Charter Rider §2(e) + §2(c).
3. **No surveillance (G6)** — aerial drone / facial-recognition /
   Bluetooth-beacon-tracking PROHIBITED per Charter §2(c); opt-in
   self-check-in only.
4. **Time-bounded carve-outs (G8)** — normally-prohibited operations
   activated only during declared emergency; 60-day initial / Council
   Lv7+ unanimity extension / auto-revoke on lifting; logged.
5. **Sphere Standards minimum compliance (G9)** — reference framework
   audited per emergency via silenKazaoriReview.
6. **Council Lv6+ ≥4/7 declaration (G10)** — emergency state requires
   Council supermajority; Founder Lv7+ unilateral declaration is NOT
   constitutionally permitted (institutional integrity over urgency,
   per ADR-2605262200 precedent).

## Architecture

6 Pregel cells, all naphtali node (witness pair pattern):

```
emergency_declaration ─── naphtali (event)
damage_assessment ────────naphtali (continuous during emergency)
emergency_water_supply ── naphtali (mizuho G5 carve-out coordination)
emergency_food_supply ─── naphtali (mitsuho reserve coordination)
mass_evacuation ────────── naphtali (opt-in self-check-in; NO surveillance)
medical_surge ──────────── naphtali (iyashi + mitate triad)
```

All cell modules at R0 are import-time `RuntimeError`. R1 activation
requires Sphere Standards baseline + ≥1 community-pilot tabletop drill.

## Time-Bounded Carve-Out Lifecycle (G8) — Constitutional Novelty

G8 is constitutionally novel: there is no prior religious-corp
precedent for time-bounded gate suspension. Discipline:

1. Carve-out activated by `emergencyCarveOutLog` record cite to the
   specific gate being carved + Council Lv6+ ≥4/7 attestation;
2. Default duration = 60 days from emergency declaration;
3. Extension beyond 60 days requires Council Lv7+ unanimity;
4. **Auto-revoke** on emergency-lifting — carve-outs do NOT carry over
   to normal operations;
5. Post-emergency `silenKazaoriReview` audits every carve-out used
   during the emergency for justification + outcome + lessons-learned.

Paradigm carve-out: **mizuho G5 single-use container prohibition**.
During declared emergency, single-use plastic water containers MAY be
distributed for short-term needs while closed-loop refillable
infrastructure is restored. Each distribution event logged via
`emergencyCarveOutLog`; ≤60 days from declaration; auto-revoke on
lifting.

Other potential carve-outs (R2+ as needed):
- iyashi G4 expedited consent for unconscious patients in mass casualty
- hagukumi G2 telepresence-without-encryption emergency stop-gap (DENIED at R0; encrypted envelope MANDATORY even in emergency)
- mitsuho reserve-stock release without per-recipient consent

## Surveillance Prohibition (G6) — Why It Holds in Emergency

The standard counter-argument for emergency-surveillance is "lives at
stake; track everyone." kazaori's discipline rejects this for three
reasons:

1. **Constitutional**: Charter §2(c) covert-ops avoidance does not
   carve out for emergencies (and G8 carve-outs are for normally-
   prohibited operational gates, not for constitutional invariants).
2. **Practical**: Opt-in self-check-in scales with religious-corp
   member buy-in; surveillance creates fear that suppresses check-in.
3. **Boundary**: Families that do not check in are NOT pursued by
   kazaori (state emergency management has alternate authority for
   non-religious-corp residents — that is their domain, not kazaori's).

## Cross-Actor Emergency Flows

### Water (mizuho ↔ kazaori)

`emergency_water_supply` cell activates `mizuho.waterContaminationIncident`
+ `emergencyCarveOutLog` (mizuho G5 single-use carve-out) +
`emergencySupplyDispatch` to needs-assessed safe sites.

### Food (mitsuho ↔ kazaori)

`emergency_food_supply` cell coordinates mitsuho reserve stock
release + `emergencySupplyDispatch`.

### Medical (iyashi + mitate ↔ kazaori)

`medical_surge` cell coordinates iyashi clinic-overflow protocols +
temporary triage site authorization + mitate diagnostic surge.

### Power (hikari ↔ kazaori)

Damage assessment cell consumes hikari grid status; emergency_water_supply
+ medical_surge cells depend on hikari grid-edge battery emergency
redirection.

### Damage assessment (tatekata ↔ kazaori)

`damage_assessment` cell consumes tatekata building damage reports;
safe-site registry routing depends on tatekata facility damage
attestation.

## R1 Activation Triggers

1. ADR-2605263200 Council Lv6+ ≥3 ratify;
2. Sphere Standards baseline attestation on file (Council-attested
   reference compliance plan);
3. ≥1 community-pilot tabletop drill executed (post-drill review +
   lessons-learned committed);
4. ≥1 emergency-management-experienced advisor on Council infrastructure
   advisory (Bootstrap Council Seat 2-5 RFP);
5. chigiri R1 active (cross-actor stewardLaborAttestation read for
   responder L5 classification);
6. toritate R1 active (Public Fund emergency disbursement accounting).

## R1 Cell Activation Order

1. `kazaori_emergency_declaration` (procedural foundation; no
   operational action until declaration cell exists);
2. `kazaori_damage_assessment` (data-fusion cell; reads from
   cross-actors, no carve-out activation yet).

R2 adds emergency_water_supply (mizuho G5 carve-out) + emergency_food_supply
(mitsuho reserve) + mass_evacuation (opt-in registry).

R3 adds medical_surge (iyashi + mitate triad) + silenKazaoriReview
cycle.

## Build & Deploy

**R0 status**: Scaffold only. R0 cells RuntimeError on import.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.kazaori_emergency_declaration import _r0_marker" 2>&1 | grep "R0 scaffold"
```

## Related Files

- `/20-actors/kazaori/manifest.jsonld`
- `/20-actors/kazaori/README.md`
- `/00-contracts/lexicons/com/etzhayyim/kazaori/` (6 Lexicons + README)
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md`
- `/90-docs/adr/2605192315-etzhayyim-transparent-force-authorization.md` — G5 + N1 force-separation
- `/90-docs/adr/2605263100-mizuho-water-sanitation-tier-b-actor-r0.md` — G5 carve-out coordination
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — declaration procedural attestation
- `/CHARTER-RIDER.md` §2(e) + §2(c) — G4 + G6 sources
- `/CLAUDE.md` — Status table row 72
- Sphere Handbook (Sphere Standards) — open-publication reference
- ICRC Code of Conduct — civilian-only doctrine reference
