# 20-actors/sonae — CLAUDE.md

## Identity

- **Name**: sonae (備え — preparedness / provisioning; 備えあれば憂いなし)
- **DID**: `did:web:sonae.etzhayyim.com`
- **ADR**: ADR-2606091200 (R0 scaffold, 2026-06-09)
- **Parent ADR**: ADR-2605192100 (Mission Charter — Wellbecoming + §1.12 + §2(c))
- **Downstream sibling**: ADR-2605263200 (kazaori — disaster response; sonae hands off to it)
- **Force-separation sibling**: ADR-2605192315 (Transparent Force — civilian/military boundary)
- **Status**: R0 scaffold — 6 cells path-reserved + 6 Lexicon skeletons
- **Form**: 任意団体 internal civilian pre-disaster substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

sonae is the **before** half of the disaster cycle: prevention,
mitigation, preparedness, early warning. It is a **parallel community
substrate**, NOT a state-licensed early-warning entity and NOT a
disaster responder. Seven discipline boundaries are structural:

1. **Civilian-only (G5+N1)** — force authorization separate per
   ADR-2605192315; sonae MUST NOT coordinate armed-force action.
2. **No false authority (G8 — defining gate)** — sonae MUST NOT
   originate official seismic/tsunami/typhoon warnings. It relays
   authoritative-agency warnings with `authoritativeSource` cited;
   `relayOnly` const true. A pre-disaster actor that fakes an
   authoritative alert can kill — this is forbidden at the schema level.
3. **No unilateral declaration (G10)** — sonae signals imminence and
   recommends; only kazaori's Council Lv6+ ≥4/7 may declare an
   emergency state. Institutional integrity over urgency
   (ADR-2605262200 precedent).
4. **No commercial disaster-prediction software (G4)** — One Concern /
   FloodFlash / Jupiter Intelligence / RMS / Tomorrow.io enterprise /
   Everbridge / OnSolve / AlertMedia PROHIBITED per Charter Rider
   §2(e)+§2(c). OPEN gov hazard feeds only (USGS / PHIVOLCS / JMA /
   GDACS / Copernicus EMS / WMO GTS).
5. **No surveillance (G6)** — open geophysical/met feeds + opt-in
   self-reporting only; NO individual tracking / NO person-profiling /
   NO household risk-scoring.
6. **Murakumo-only inference (G7)** — hazard correlation + needs
   forecast via judah LiteLLM; commercial disaster-AI PROHIBITED.
7. **Community-scale only (G3)** — religious-corp community sites +
   adjacent partner sites; NOT a national early-warning replacement.

## Phase boundary with kazaori (DO NOT CROSS)

```
sonae (備え, BEFORE)            kazaori (風折, DURING)         after
─────────────────────          ─────────────────────         ─────
prevention / mitigation        emergency declaration         silenKazaoriReview
preparedness / stockpiling     damage assessment             shidemori (fatalities)
hazard watch                   supply dispatch                kokoro (mental health)
official-warning RELAY    ───▶ mass evacuation
risk assessment                medical surge
drills
imminence signal ─────────────▶ (recommends; Council declares)
```

- sonae **never** does response (N3). When a hazard materializes,
  `sonae_handoff_trigger` emits `disasterImminenceSignal` and hands to
  `kazaori.emergency_declaration`. kazaori's Council declaration is the
  only emergency-state authority (preserves kazaori G10).
- `sonae_drill_attestation` records double-satisfy kazaori's R1
  activation gate (≥1 community-pilot tabletop drill).

## Architecture

6 Pregel cells, all naphtali node (witness pair pattern):

```
hazard_watch ──────────── naphtali (continuous; open feeds only)
risk_assessment ───────── naphtali (standing; per-site profile)
early_warning_relay ───── naphtali (event; relay-only, source cited)
preparedness_plan ─────── naphtali (periodic; stockpile/safe-site/route)
drill_attestation ─────── naphtali (event; opt-in participants)
handoff_trigger ───────── naphtali (event; → kazaori, recommend only)
```

All path-reserved under
`40-engine/kotoba/crates/kotoba-kotodama/cells/sonae_*/`; import-time
`RuntimeError` until R1.

## 6 Lexicons (com.etzhayyim.sonae.*)

| Lexicon | Cell | Structural invariant |
|---|---|---|
| `hazardSignalRecord` | hazard_watch | `openDataOnlyAttested` const true (G6); `sourceFeed` enum of open gov feeds (G4) |
| `siteRiskProfile` | risk_assessment | community-scale only (G3); no individual-level fields (G6) |
| `earlyWarningRelay` | early_warning_relay | `relayOnly` const true + `authoritativeSource` required (G8) |
| `preparednessPlan` | preparedness_plan | cross-actor $ref (mizuho/mitsuho/tatekata/hagukumi); opt-in registry only (G6) |
| `drillAttestation` | drill_attestation | opt-in `participants`; satisfies kazaori R1 drill gate |
| `sonaeReadinessReview` | (Council attestation scope) | Sendai + Sphere alignment audit (G9); forecast-accuracy honesty |

## Cross-actor map

| Actor | Relationship |
|---|---|
| **kazaori** | Downstream handoff (response). sonae feeds preparednessPlan + imminence signal; kazaori owns declaration + response. |
| **mizuho / mitsuho** | Pre-positioning stockpile targets (water / food reserves) sized from siteRiskProfile. |
| **tatekata** | Building-stock exposure + safe-site pre-designation (feeds risk_assessment + preparedness_plan). |
| **hagukumi** | Vulnerable-population OPT-IN pre-registry (children / elderly / chronic-care). |
| **hikari / watatsuna** | Lifeline + comms (grid + submarine-cable) resilience inputs. |
| **kawaraban** | Warning-relay channel (actor-to-actor wire + member notification). |
| **toritate** | Preparedness fund disbursement + transparent accounting. |
| **chigiri** | Declaration-handoff procedural attestation. |

## Roadmap (R0 → R3)

| Phase | Gate | Scope | Murakumo |
|---|---|---|---|
| R0 | ADR-2606091200 (PROPOSED) | scaffold; 6 cells path-reserved; 6 Lexicon skeletons | none |
| R1 | Council Lv6+ ≥3 + open-feed baseline + Sendai self-assessment + ≥1 drill | hazard_watch + risk_assessment | naphtali |
| R2 | Council Lv6+ ≥4 + 30-day public + 3 site attestations | +early_warning_relay + preparedness_plan + drill_attestation | naphtali + dan |
| R3 | Council Lv7+ unanimity + ≥1 live hazard-to-handoff cycle | +handoff_trigger + sonaeReadinessReview cycle | naphtali + dan + levi |

## Constraints when editing

- Never add a cell or Lexicon field that lets sonae (a) originate an
  authoritative warning, (b) declare an emergency, or (c) perform
  response actions. Those violate G8 / G10 / N3 respectively.
- Hazard inputs must remain OPEN gov feeds (G4) — no vendor API keys,
  no closed prediction services.
- No individual-level person data anywhere (G6). Registries are opt-in
  and member-signed (ADR-2605181100 encrypted-records pattern).
