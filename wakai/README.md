# wakai (和会) — Non-profit Religious-Corp Mutual Aid Substrate

**DID**: `did:web:wakai.etzhayyim.com`
**Namespace**: `com.etzhayyim.wakai.*`
**ADR**: ADR-2605263500 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 6 cells path-reserved + 5 Lexicon skeletons
**Cross-actor**: kazaori (emergency pool; path-reserved wakai cross-actor at ADR-2605263200) / iyashi+hagukumi+yakushi (health event) / toritate (accounting + Public Fund backstop) / chigiri (membership + procedural)

## Overview

Religious-corp mutual aid substrate. Member-to-member solidarity
pooling per Charter §1.7 反個人主義 + 多世代 + harmony invariant.
Complements Public Fund (religious-corp → member flow) with member →
member solidarity flow.

Etymology: 和会 (wakai) = harmony/reconciliation gathering; classical
互助会 mutual aid society.

## Identity (CRITICAL — IMMUTABLE)

- **NOT insurance** (G3 + N1) — no premium-as-contract; no actuarial
  pricing; no claim adjudication; no policy denial; no underwriting.
- **NO commercial insurance software** (G4 + N8) — Guidewire / Duck
  Creek / Insurity / Sapiens / Majesco / SAP Insurance / Oracle
  Insurance / Lemonade-as-vendor / Hippo-as-vendor PROHIBITED per
  Charter Rider §2(e) + §2(c).
- **NO commercial re-insurance** (G5 + N6) — Munich Re / Swiss Re /
  SCOR / Hannover Re / Berkshire Hathaway Re PROHIBITED; risk stays
  in community + Public Fund backstop is the only escalation.
- **NO investment return promise** (G6 + N2) — Charter Rider §2(b)
  speculative finance prohibition; pool held in stable-asset form
  (USDC on Base L2 only); NO DeFi yield farming; NO token
  speculation; `mutualAidContributionAttestation.investmentReturnPromised`
  const false structural.
- **NO discrimination on pre-existing condition** (G7) — no
  underwriting; no exclusion; no risk-based rejection;
  `mutualAidDistributionAttestation.noPreExistingConditionExclusion`
  const true structural.
- **Contribution voluntary + ability-scaled** (G8) — Charter §1.7
  反個人主義; no minimum amount; member self-attests ability.
- **Distribution by community discernment** (G9) — Council Lv6+ ≥3
  + ≥3 community attestation chain; NOT claim adjudication; need-
  based only.
- **Public Fund backstop** (G10) — when pool insufficient; Council
  Lv6+ ≥4/7 (cross-actor toritate).
- **NO payroll for administrators** (G11) — vocation-flow L5 stewards.
- **Murakumo-only inference** (G12) — commercial insurance-AI
  (Lemonade NLP / Tractable / Carpe Data) PROHIBITED.

## 6 Pregel Cells (R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `wakai_mutual_aid_pool_contribution` | asher | continuous | voluntary contribution → mutualAidContributionAttestation |
| `wakai_mutual_aid_distribution` | asher | event | need + Council ≥3 + ≥3 community attestations → mutualAidDistributionAttestation |
| `wakai_emergency_pool_activation` | asher (kazaori-paired) | event | kazaori emergencyDeclaration active → emergency dispatch |
| `wakai_health_event_support` | asher (iyashi+hagukumi+yakushi-paired) | event | health event cross-link → distribution routing |
| `wakai_public_fund_backstop_request` | asher (toritate-paired) | event | pool insufficient → Council Lv6+ ≥4/7 backstop |
| `wakai_pool_state_reporting` | asher | continuous (monthly) | aggregate state → mutualAidPoolStateReport (no individual amounts) |

## 5 Lexicons under `com.etzhayyim.wakai.*`

| Lexicon | Purpose |
|---|---|
| `mutualAidContributionAttestation` | G6 STRUCTURAL: investmentReturnPromised const false; G8 voluntary + ability-scaled |
| `mutualAidDistributionAttestation` | G9 STRUCTURAL: communityDiscernmentAttestations minLength 3 + Council Lv6+ ≥3; G7 noPreExistingConditionExclusion const true |
| `mutualAidPoolStateReport` | Per-period aggregate pool state; NO individual member amounts public |
| `publicFundBackstopRequest` | When pool insufficient; Council Lv6+ ≥4/7 attestations + toritate cross-link |
| `silenWakaiReview` | Quarterly Council review; G3/G4/G5/G6/G7/G9/G11 const-field structural enforcement |

See `/00-contracts/lexicons/com/etzhayyim/wakai/README.md`.

## Constitutional Gates (G1–G12)

See ADR-2605263500 §5. Key:

- **G3** NOT insurance (no premium / no actuarial / no claim denial)
- **G4** NO commercial insurance software
- **G5** NO commercial re-insurance
- **G6** NO investment return promise (Charter Rider §2(b))
- **G7** NO pre-existing condition discrimination
- **G8** Voluntary + ability-scaled contribution
- **G9** Community discernment distribution (NOT claim adjudication)
- **G10** Public Fund backstop

## Non-Goals (N1–N12)

See ADR-2605263500 §6.

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Council + pool seed Lv6+ ≥4/7 + ≥3 witnesses | 3 core cells + ≤50 members + ≤$50k pool |
| **R2** | post-R1 + 30-day public + 3 site attestations | +2 cells (health event + backstop) + ≤500 members + ≤$500k pool |
| **R3** | post-R2 + Council Lv7+ + distribution cycle completed | +1 cell (emergency pool) + ≤25,000 members + ≤$5M pool |

## Related Files

- `/20-actors/wakai/manifest.jsonld`
- `/20-actors/wakai/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/wakai/` (5 Lexicons + README)
- `/90-docs/adr/2605263500-wakai-mutual-aid-tier-b-actor-r0.md`
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — cross-actor emergency
- `/90-docs/adr/2605192145-etzhayyim-public-fund-architecture.md` — backstop source
- `/CHARTER-RIDER.md` §2(b) + §2(e) + §2(c) — gate sources
- `/CLAUDE.md` — Status table row 74
