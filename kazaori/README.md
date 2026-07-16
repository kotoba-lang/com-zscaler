# kazaori (風折) — Non-profit Religious-Corp Civilian Disaster Response Substrate

**DID**: `did:web:kazaori.etzhayyim.com`
**Namespace**: `com.etzhayyim.kazaori.*`
**ADR**: ADR-2605263200 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 6 Lexicon skeletons
**Cross-actor**: mizuho / mitsuho / hagukumi / iyashi + mitate / tatekata / hikari / chigiri / toritate / wakai+kokoro+shidemori (future)
**Standards reference (NOT membership)**: Sphere Standards / ICRC Code of Conduct / IFRC / UN OCHA cluster system / WHO Health Cluster

## Overview

Religious-corp civilian disaster response substrate. Earthquake /
typhoon / flood / wildfire / pandemic-class biological emergency /
power outage / water shortage / food shortage / building damage /
mass evacuation / medical surge coordination.

Etymology: 風折 (kazaori) = "wind-broken" (storm-damaged tree
branches; classical imagery; 万葉集 evokes ephemeral fragility that
disasters expose). The actor name carries the dual meaning of (a)
disasters that come, and (b) the response that follows.

## Identity (CRITICAL — IMMUTABLE)

- **Civilian-only** (G5 + N1) — kazaori is civilian disaster response;
  force authorization separate per ADR-2605192315 Transparent Force.
  kazaori MUST NOT invoke or coordinate with armed force actions.
- **NO commercial disaster management software** (G4) — Veoci / NC4 /
  Crisis Track / Everbridge / OnSolve / SAP Disaster Recovery /
  Microsoft Disaster Response Hub / IBM Crisis Response PROHIBITED
  per Charter Rider §2(e) anti-gatekeeping + §2(c) covert-ops vendor
  concern (vendor closed query-tracking on evacuation status + member
  location is structurally unacceptable).
- **NO surveillance-based monitoring** (G6) — aerial drone surveillance
  / facial-recognition crowd-monitoring / Bluetooth-beacon-tracking
  PROHIBITED per Charter §2(c). Evacuation check-in is OPT-IN
  self-attestation only; member-signed; encrypted payload per
  ADR-2605181100.
- **Time-bounded carve-outs** (G8) — normally-prohibited operations
  (mizuho G5 single-use water container; iyashi G4 expedited consent
  abridgment for unconscious patients; etc.) MAY be activated only
  during a Council-Lv6+-declared emergency. 60-day initial / Council
  Lv7+ unanimity for extension / **auto-revoke** on emergency-lifting.
  Each carve-out logged via `emergencyCarveOutLog`.
- **Sphere Standards minimum compliance** (G9) — reference framework
  for shelter / water / food / health / protection. Sphere is
  open-publication; no membership required. silenKazaoriReview audits
  compliance per emergency.
- **Council Lv6+ ≥4/7 declaration** (G10) — emergency state requires
  Council supermajority. Council Lv7+ unanimity for extension beyond
  60 days. Auto-lifting at expiration unless re-extended.
- **NO payroll for responders** (G12) — vocation-flow L5 stewards
  (cross-actor chigiri.stewardLaborAttestation + toritate.ledgerEntry
  enum exclusion).
- **Murakumo-only inference** (G7) — needs prediction + damage
  assessment via judah LiteLLM; commercial disaster-AI (One Concern
  / FloodFlash) PROHIBITED.

## 6 Pregel Cells (R0 path-reserved)

All cells path-reserved under `40-engine/kotoba/crates/kotoba-kotodama/cells/kazaori_*/`.
Cell modules created at R1 ratification, import-time
`RuntimeError("kazaori R0 scaffold: activate via Council ADR + R1 ratification + Sphere Standards baseline + ≥1 community-pilot tabletop drill")`.

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `kazaori_emergency_declaration` | naphtali | event | Council Lv6+ ≥4/7 declaration → emergencyDeclarationAttestation |
| `kazaori_damage_assessment` | naphtali | continuous | cross-actor data fusion → damageAssessmentReport |
| `kazaori_emergency_water_supply` | naphtali (mizuho-paired) | continuous | needs prediction → mizuho G5 carve-out + emergencySupplyDispatch |
| `kazaori_emergency_food_supply` | naphtali (mitsuho-paired) | continuous | needs prediction → mitsuho reserve release + emergencySupplyDispatch |
| `kazaori_mass_evacuation` | naphtali | continuous | opt-in self-check-in → evacuationCheckIn + safe-site registry |
| `kazaori_medical_surge` | naphtali (iyashi + mitate paired) | continuous | clinical overflow → iyashi clinic-overflow + temporary triage |

## 6 Lexicons under `com.etzhayyim.kazaori.*`

| Lexicon | Purpose |
|---|---|
| `emergencyDeclarationAttestation` | Council Lv6+ ≥4/7 declaration; G10 structural; duration enum; declaredScope |
| `damageAssessmentReport` | Per-area / per-asset damage; cross-actor data fusion sources via $ref |
| `emergencySupplyDispatch` | Per-dispatch event; cross-actor mizuho/mitsuho; carve-out cite via $ref |
| `evacuationCheckIn` | OPT-IN self-attestation; encryptedPayloadCid REQUIRED; G6 structural |
| `emergencyCarveOutLog` | Per-carve-out activation log; gate carved + Council attestation + auto-revoke timestamp; G8 structural |
| `silenKazaoriReview` | Post-emergency Council review; Sphere compliance + carve-out audit + Wellbecoming preservation |

See `/00-contracts/lexicons/com/etzhayyim/kazaori/README.md`.

## Constitutional Gates (G1–G12)

See ADR-2605263200 §5. Key:

- **G3** Community-scale only
- **G4** NO commercial disaster management software
- **G5** NO armed enforcement (civilian only; force separate)
- **G6** NO surveillance (opt-in self-check-in only)
- **G8** Time-bounded carve-outs (auto-revoke on lifting)
- **G9** Sphere Standards minimum compliance
- **G10** Council Lv6+ ≥4/7 declaration
- **G12** NO payroll for responders

## Non-Goals (N1–N12)

See ADR-2605263200 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Council + Sphere baseline + ≥1 tabletop drill | 2 core cells + 1 simulated declaration |
| **R2** | post-R1 + 30-day public + 3 site attestations | +3 cells (mizuho G5 carve-out + mitsuho reserve + opt-in evacuation) |
| **R3** | post-R2 + Council Lv7+ + ≥1 emergency cycle | +1 cell medical_surge + silenKazaoriReview cycle established |

## Related Files

- `/20-actors/kazaori/manifest.jsonld`
- `/20-actors/kazaori/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/kazaori/` (6 Lexicons + README)
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605192315-etzhayyim-transparent-force-authorization.md` — G5 + N1 separation
- `/90-docs/adr/2605263100-mizuho-water-sanitation-tier-b-actor-r0.md` — G5 carve-out coordination
- `/CHARTER-RIDER.md` §2(e) + §2(c) — G4 + G6 sources
- `/CLAUDE.md` — Status table row 72
