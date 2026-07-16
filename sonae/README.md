# sonae (備え) — Non-profit Religious-Corp Civilian Pre-Disaster Foresight + Preparedness + Early-Warning Substrate

**DID**: `did:web:sonae.etzhayyim.com`
**Namespace**: `com.etzhayyim.sonae.*`
**ADR**: ADR-2606091200 (R0 scaffold)
**Status**: R0 scaffold (2026-06-09) — 6 cells path-reserved + 6 Lexicon skeletons
**Phase boundary**: sonae handles **before** (prevention / mitigation / preparedness / early warning). Response (during) is **kazaori** (ADR-2605263200). sonae MUST NOT do response.
**Triad**: 備え sonae (before) → 風折 kazaori (during) → silenKazaoriReview / 死出守 shidemori (after)
**Cross-actor**: kazaori (downstream handoff) / mizuho + mitsuho (pre-positioning) / tatekata (exposure + safe-site) / hagukumi (vulnerable pre-registry, opt-in) / hikari + watatsuna (lifeline + comms resilience) / kawaraban (warning relay channel) / toritate (preparedness fund) / chigiri (procedural)
**Standards reference (NOT membership)**: Sendai Framework for Disaster Risk Reduction 2015–2030 / Sphere Standards / UN OCHA / WMO + IOC tsunami warning system / GDACS — all open-publication

## Overview

Civilian **pre-disaster** substrate. It closes the gap that kazaori
explicitly does not cover: the **before** of the disaster cycle —
hazard watch, community risk assessment, official-warning relay,
preparedness planning + stockpile pre-positioning, and drills. When a
watched hazard crosses a materialization threshold, sonae emits an
**imminence signal** and hands off to kazaori — it never declares an
emergency itself.

Etymology: 備え (sonae) = "preparedness / provisioning for what is to
come." From the proverb 備えあれば憂いなし — *if you are prepared, you
have no regret* — the canonical Japanese expression of disaster
readiness. The name carries the dual meaning of (a) the foresight to
read the coming hazard, and (b) the provisioning a community lays down
in answer to it. It is what stands ready **before** 風折 (kazaori, the
wind-broken branch).

## Identity (CRITICAL — IMMUTABLE)

- **Civilian-only** (G5 + N1) — sonae is civilian preparedness; force
  authorization separate per ADR-2605192315 Transparent Force.
- **NO false authority** (G8 — the defining gate) — sonae MUST NOT
  issue official seismic / tsunami / typhoon warnings as if it were a
  state authority. It **relays** warnings from authoritative agencies
  (PHIVOLCS / JMA / USGS / PTWC / national met services) with mandatory
  `authoritativeSource` citation; `relayOnly` is structurally `const
  true`. Fabricating authoritative alerts is the single most dangerous
  failure mode for a pre-disaster actor and is forbidden structurally.
- **NO unilateral disaster declaration** (G10) — sonae may signal
  imminence and recommend, but the only path to an emergency state
  remains kazaori's Council Lv6+ ≥4/7 declaration. sonae cannot
  shortcut institutional integrity under urgency.
- **NO commercial disaster-prediction / early-warning software** (G4)
  — One Concern / FloodFlash / Jupiter Intelligence / RMS (Moody's) /
  Tomorrow.io enterprise / Everbridge / OnSolve / AlertMedia PROHIBITED
  per Charter Rider §2(e) anti-gatekeeping + §2(c) (vendor closed
  query-tracking on a community's risk + readiness posture is
  structurally unacceptable). **OPEN government hazard feeds only**
  (USGS, PHIVOLCS, JMA, GDACS, Copernicus EMS, WMO GTS) — open
  publication, reference not membership.
- **NO surveillance** (G6) — hazard sensing from public
  geophysical / meteorological feeds + **opt-in** community
  self-reporting only. NO individual tracking, NO predictive
  person-profiling, NO household risk-scoring.
- **Murakumo-only inference** (G7) — hazard correlation + needs
  forecasting via judah LiteLLM; commercial disaster-AI PROHIBITED.
- **Community-scale only** (G3) — preparedness for religious-corp
  community sites + adjacent partner sites. NOT a national
  early-warning replacement (PHIVOLCS / JMA / state agencies remain
  authoritative; sonae consumes their public warnings).
- **Sendai + Sphere reference** (G9) — open-publication frameworks;
  sonaeReadinessReview audits alignment.

## 6 Pregel Cells (R0 path-reserved)

All cells path-reserved under
`40-engine/kotoba/crates/kotoba-kotodama/cells/sonae_*/`.
Cell modules created at R1 ratification, import-time
`RuntimeError("sonae R0 scaffold: activate via Council ADR + R1 ratification + open-feed baseline + Sendai self-assessment + ≥1 community drill")`.

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `sonae_hazard_watch` | naphtali | continuous | OPEN hazard feeds (USGS/PHIVOLCS/JMA/GDACS/Copernicus) → hazardSignalRecord |
| `sonae_risk_assessment` | naphtali | standing | per-site exposure + vulnerability (tatekata/hagukumi/mizuho/hikari) → siteRiskProfile |
| `sonae_early_warning_relay` | naphtali | event | official agency warning → opt-in member relay; authoritativeSource REQUIRED → earlyWarningRelay |
| `sonae_preparedness_plan` | naphtali | periodic | stockpile + safe-site + route + opt-in registry targets → preparednessPlan |
| `sonae_drill_attestation` | naphtali | event | tabletop / field drill record → drillAttestation (also satisfies kazaori R1 drill gate) |
| `sonae_handoff_trigger` | naphtali | event | hazard crosses materialization threshold → disasterImminenceSignal → kazaori.emergency_declaration (recommend only) |

## 6 Lexicons under `com.etzhayyim.sonae.*`

| Lexicon | Purpose |
|---|---|
| `hazardSignalRecord` | Open-feed hazard signal; sourceFeed enum; `openDataOnlyAttested` const true; G6 structural |
| `siteRiskProfile` | Per-site exposure / vulnerability; cross-actor $ref; NO individual-level data; community-scale |
| `earlyWarningRelay` | Relay of OFFICIAL warning; `authoritativeSource` REQUIRED; `relayOnly` const true; G8 structural |
| `preparednessPlan` | Stockpile / safe-site / route / opt-in registry targets; cross-actor mizuho/mitsuho/tatekata/hagukumi |
| `drillAttestation` | Drill record (opt-in participants); satisfies kazaori R1 drill activation gate |
| `sonaeReadinessReview` | Periodic review: forecast accuracy + readiness + Sendai alignment; G9 structural |

See `/00-contracts/lexicons/com/etzhayyim/sonae/README.md`.

## Constitutional Gates (G1–G12)

See ADR-2606091200 §5. Key:

- **G3** Community-scale only (NOT national early-warning replacement)
- **G4** NO commercial disaster-prediction software; OPEN gov feeds only
- **G5** NO armed enforcement (civilian only; force separate)
- **G6** NO surveillance (open feeds + opt-in self-report only)
- **G7** Murakumo-only inference
- **G8** NO false authority (relay-only; authoritativeSource cited)
- **G9** Sendai Framework + Sphere reference
- **G10** NO unilateral disaster declaration (kazaori Council path only)
- **G11** NOT a state-licensed early-warning entity
- **G12** NO payroll (vocation-flow L5 stewards)

## Non-Goals (N1–N12)

See ADR-2606091200 §6. Key: **N3** NOT response (clean phase boundary
to kazaori), **N5** NOT state early-warning replacement, **N12** NOT
issuing authoritative public alerts (relay-only).

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-06-09 | Scaffold (this commit) |
| **R1** | post-Council + open-feed baseline + Sendai self-assessment + ≥1 drill | 2 core cells (hazard_watch + risk_assessment) |
| **R2** | post-R1 + 30-day public + 3 site attestations | +3 cells (early_warning_relay + preparedness_plan + drill_attestation) |
| **R3** | post-R2 + Council Lv7+ + ≥1 live hazard-to-handoff cycle | +1 cell (handoff_trigger) + sonaeReadinessReview cycle |

## Run (charter-gate conformance tests)

```bash
bb run_tests.clj
```

Native Clojure port (`methods/test_charter_gates.cljc`, chigiri/narashi idiom) of the
R0-era Python invariant suites — 22 tests / 249 assertions, test-only / network-free /
no cell execution. The Python originals (`70-tools/scripts/audit/
test_sonae_lexicon_invariants.py` + `test_sonae_warning_sources_seed.py`) remain in
place for one cycle per MATURITY.md.

## Related Files

- `/20-actors/sonae/manifest.jsonld`
- `/20-actors/sonae/CLAUDE.md`
- `/20-actors/sonae/methods/test_charter_gates.cljc` — charter-gate conformance tests (native)
- `/00-contracts/lexicons/com/etzhayyim/sonae/` (6 Lexicons + README)
- `/90-docs/adr/2606091200-sonae-pre-disaster-foresight-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — downstream response actor (handoff target)
- `/90-docs/adr/2605192315-etzhayyim-transparent-force-authorization.md` — G5 + N1 separation
- `/CHARTER-RIDER.md` §2(e) + §2(c) — G4 + G6 sources
