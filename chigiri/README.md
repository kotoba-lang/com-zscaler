# chigiri (契) — Non-profit Religious-Corp Legal Procedure Substrate

**DID**: `did:web:chigiri.etzhayyim.com`
**Namespace**: `com.etzhayyim.chigiri.*`
**ADR**: ADR-2605262700 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 12 cells path-reserved + 9 Lexicon skeletons
**Companion ADR**: ADR-2605262800 (global legal corpus ingestion — data substrate)
**Parent ADRs**: ADR-2605192100 (Mission Charter) + ADR-2605192200 (Charter Rider) + ADR-2605192300 (Council) + ADR-2605261000 (Liberation Ladder)

## Overview

Non-profit religious-corp legal procedure substrate. Procedural template +
on-chain attestation + routing substrate for:

- **Internal procedures** — Charter Rider §2(a)-(h) enforcement / Council
  Lv6/Lv7 vote / Adherent SBT lifecycle / cooperative mediation /
  excommunication (with 30-day cure period);
- **External legal interfaces** — employment law compliance (steward
  classification per Liberation Ladder L0..L6) / multi-jurisdiction tax-
  receipt routing / IP defense / trademark management / vendor contract
  Charter Rider scrutiny / data protection (GDPR / CCPA / APPI / LGPD);
- **State-function routing-around** (Charter §1.12) — marriage / naming
  / funeral / inheritance / ID alternatives via covenant attestation +
  SBT-link;
- **Defensive force / Just War legal** — IHL checklist + Transparent
  Force authorization procedural front-end (ADR-2605192315 integration);
- **Eros / Gore content moderation** — Charter §1.13 applicability board
  procedure;
- **Steward labor flow** — L0..L6 ladder classification + constructive-
  employment drift prevention.

## Identity (CRITICAL)

- **NOT a commercial law firm** (N1 / N10).
- **NOT a state-granted legal personality** — 一般社団 / NPO / 公益財団 /
  宗教法人 法人格 NEVER per Preamble §0.4 Lv7+ unanimity lock (N2).
- **NOT an unauthorized practice of law** — chigiri provides procedural
  templates + on-chain attestation + routing; legal *advice* happens via
  human counsel contracted through Public Fund (Council Lv6+ approval)
  per ADR-2605192145. Lint hook `70-tools/scripts/lint/no-chigiri-
  legal-advice.mjs` (W1) is the structural enforcement of G14.

## Replaces

The legacy `lawfirm.etzhayyim.com` reference visible in
`20-actors/hanrei/CLAUDE.md` (etzhayyim-era cross-actor link) is replaced by
chigiri. chigiri is religious-corp native, Murakumo-only, SBT-gated,
Charter Rider §2-compliant; no etzhayyim lineage.

## 12 Pregel Cells (R0 path-reserved)

All cells are path-reserved at R0; the cell modules are created in W1
(post-Bootstrap-Council ratification) under
`40-engine/kotoba/crates/kotoba-kotodama/cells/chigiri_*/` and will be import-time
`RuntimeError("chigiri R0 scaffold: activate via Council ADR + R1 ratification")` at scaffold time.

| Cell | Murakumo node | Phase | I/O |
|---|---|---|---|
| `chigiri_charters_attestation` | reuben | continuous | finding → mediation OR enforcement routing |
| `chigiri_council_procedure` | reuben | event | proposal → Safe 5-of-7 multisig path |
| `chigiri_member_onboarding` | simeon | event | candidate DID + consent → Adherent SBT |
| `chigiri_member_offboarding` | simeon | event | DID → SBT revoke (voluntary OR excommunication) |
| `chigiri_covenant_ceremony` | simeon+levi | event | ceremony spec → covenantAttestation (musubi pair) |
| `chigiri_inheritance` | simeon | event | decedent + heir DID → inheritanceChain |
| `chigiri_dispute_mediation` | levi | session | claim → mediation cycle (≤3 rounds before arbitration) |
| `chigiri_ip_licensing` | gad | continuous | Rider scan finding → claim filing |
| `chigiri_tax_receipt` | gad | event | donation event → per-jurisdiction receipt routing |
| `chigiri_employment_compliance` | gad | continuous | steward registry → L-level classification |
| `chigiri_data_privacy` | gad | event | DSAR / breach → procedure routing |
| `chigiri_transparent_force_authorization` | naphtali | event | force request → 1 SBT = 1 vote chain + IHL checklist |

## 9 Lexicons under `com.etzhayyim.chigiri.*`

See `/00-contracts/lexicons/com/etzhayyim/chigiri/README.md` for the
canonical list + schemas.

## Constitutional Gates (G1–G14) — IMMUTABLE R0–R3

See ADR-2605262700 §5. Key:

- **G1** Charter Rider §2 scan on every legal document
- **G7** Defensive Just War only (offensive force REFUSED)
- **G8** Open-source legal templates (Apache 2.0 + Rider)
- **G10** Cooperative mediation precedes arbitration
- **G11** Murakumo-only inference
- **G12** Excommunication = Council Lv6+ ≥4/7 + 30-day cure
- **G13** Volunteer ≠ employee structural enforcement
- **G14** UPL strictly prohibited (lint hook scans for advice-issuing language)

## Non-Goals (N1–N12) — EXCLUDED from R0–R3

See ADR-2605262700 §6.

## Data substrate dependency

chigiri consumes the global legal corpus ingested under ADR-2605262800:

- statutes per jurisdiction (`90-docs/baien/datasets/law/statutes/<juris>/`)
- case law per court system (`90-docs/baien/datasets/law/cases/<court>/`)
- treaties (`90-docs/baien/datasets/law/treaties/<corpus>/`)
- procedures (`90-docs/baien/datasets/law/procedures/<body>/`)
- templates (`90-docs/baien/datasets/law/templates/<corpus>/`)

Sensors: `kotodama.organism.sensors.legal.*` (W1+ activation).

## Cross-actor relationships

| Actor | Direction | Purpose |
|---|---|---|
| `hanrei.etzhayyim.com` | → (read) | Case-law / 判例 lookup |
| `bunken.etzhayyim.com` | → (read) | Legal literature lookup |
| `musubi` (future) | ↔ | Covenant ceremony (chigiri attests) |
| `shidemori` (future) | ↔ | Memorial / cemetery (chigiri inherits) |
| `mitate` | → | Medical attestation interop |
| `yakushi` | ← | Pharmaceutical regulatory compliance routing |
| `hagukumi` | ↔ | Caregiver attestation + work-cap classification |
| `manabi` | → | Anti-credentialism legal framework consultation |
| ChartersComplianceRegistry | ↔ | Procedural front-end |
| TitheRouter | → | 10% Tithe transparency accounting |
| Public Fund Safe | → | External-counsel contract proposal |
| Land Registry | → | Inalienable-donation invariant cross-check |
| Force Authorization | ↔ | 1 SBT = 1 vote attestation chain |

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit). 12 cells path-reserved. 9 Lexicon skeletons. |
| **R1** | post-Bootstrap-Council (≥2026-06-19+) | 3 core cells activated + 3 Lexicons full schema |
| **R2** | post-R1 + 30-day public objection | +5 cells + 4 Lexicons full schema + multi-juris tax routing |
| **R3** | post-R2 + Council Lv7+ unanimity | All 12 cells + IHL force flow + DSAR full + musubi/shidemori |

## R0 Status

**Scaffold only.** No cell modules exist yet (W1). Lexicon schemas are
skeleton only — required-field validation lands at W1 ratification.

## Related Files

- `/20-actors/chigiri/manifest.jsonld`
- `/20-actors/chigiri/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/chigiri/` (9 Lexicons + README)
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md`
- `/90-docs/adr/2605262800-public-data-legal-corpus-ipfs-ingestion.md`
- `/70-tools/baien-moemoekyun-train/recipes/legal/` (training corpus recipes)
- `/40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/organism/sensors/legal/` (sensor stubs)
- `/CHARTER-RIDER.md` §2 — 8 prohibited categories
- `/COUNCIL.md` — Bootstrap Council roster + RFP
- `/MEMBERS.md` — 信者 roster
- `/CLAUDE.md` — Religious-corp status table
