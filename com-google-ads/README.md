# com-google-ads (広) — Charter-Clean Outreach & Performance-Marketing Actor

**DID**: `did:web:etzhayyim.github.io:com-google-ads`
**Namespace**: `com.etzhayyim.googleads.*`
**ADR**: ADR-2606292130 (R0 scaffold)
**Status**: R0 design scaffold (2026-06-29)
**Tier**: Tier-B

`com-google-ads` is a kotoba-native actor for the org's own **outreach** —
mission amplification (events, publications, mutual-aid drives,
land-sovereignty appeals, donation drives) — under constitutional invariants
that make the surveillance/ad-tech harms structurally impossible. It is the
**buy/create side** of the **akashi 証** disclose/verify loop: the org
discloses what it amplifies.

It is the charter-clean inversion of the "Google Ads" / performance-marketing
ad-platform category.

## Why an actor layer at all?

An ad platform's substrate *is* the harm: individual behavioral targeting,
cross-site tracking pixels, auto-actuated spend, purchased/scraped audiences,
microtargeting of protected categories. A commons actor cannot disable these
as features — they must be **structurally impossible** in the data model and
gates. So the intelligence (a Murakumo LLM advisor) is sealed into a Propose
node that returns **proposals only**; an independent PolicyGovernor censors
them; a human (finance/Council) signs off via `interrupt-before`; and every
published creative + spend range is mirrored to akashi.

## The core contract

```
cohort (talent / self-sovereign opt-in, G3)
   │
   ▼
 ┌──────────────┐  proposal   ┌──────────────────┐
 │ ProposeCell  │ ──────────▶ │ PolicyGovernor   │  (G2 cohort-only / G3 consent / G5 anti-manip)
 │ (sealed LLM) │             │  accept ◀──▶ reject/hold
 └──────────────┘             └────────┬─────────┘
                       [interrupt-before :request-approval]  ← finance DID (G1)
                                       ▼
 ┌──────────────┐  publish    ┌──────────────────┐
 │  SpendCell   │ ──────────▶ │  DiscloseCell    │ ──▶ akashi 証 (G7) + toritate/danjo
 │ (append-only,│             │  creative+spend  │
 │  finance-sig)│             └──────────────────┘
 └──────────────┘
       ▲
       │ aggregate performance (by cohort — no individual, G2; transient, itonami-G3 lineage)
 ┌──────────────┐
 │ PerformCell  │
 └──────────────┘
```

> **com-google-ads never publishes a campaign, bid, budget, or creative that
> a human has not approved, and never targets an identifiable individual.**

## Constitutional gates (G1–G9)

| Gate | Name |
|---|---|
| G1 | propose-not-actuate (human sign-off via interrupt-before) |
| G2 | cohort-scale-only (no `:person/*`; anti-surveillance is structural) |
| G3 | consent-gated-audience (self-sovereign opt-in; Signal-E2E PII; GDPR Art 17) |
| G4 | spend-append-only-finance-signed |
| G5 | anti-manipulation (no protected-category microtargeting / dark patterns) |
| G6 | murakumo-only-narration |
| G7 | akashi-transparency-mirror (buy/disclose one-loop) |
| G8 | no-ad-sdk-no-tracking-pixel |
| G9 | no-commercial-resale |

Full text in `manifest.edn` `:actor/gates`.

## Commands (NSID)

| Command | NSID | Access |
|---|---|---|
| proposeCampaign | `com.etzhayyim.apps.googleads.proposeCampaign` | operator (proposal only, G1) |
| approveProposal | `com.etzhayyim.apps.googleads.approveProposal` | finance DID (G1/G4) |
| registerAudience | `com.etzhayyim.apps.googleads.registerAudience` | caller = cohort-owner (G3) |
| forgetAudience | `com.etzhayyim.apps.googleads.forgetAudience` | caller = cohort-owner (GDPR Art 17) |
| getCohortPerformance | `com.etzhayyim.apps.googleads.getCohortPerformance` | public-aggregate (G2) |
| listSpend | `com.etzhayyim.apps.googleads.listSpend` | audit (G4) |
| discloseCreative | `com.etzhayyim.apps.googleads.discloseCreative` | system (G7 → akashi) |

## Cross-actor

- **Upstream**: `talent` (self-sovereign opt-in cohort supply), `isco` (taxonomy)
- **Peer**: `akashi` 証 (disclosure mirror — G7 one-loop), `moushibumi` (no-voter-persuasion boundary)
- **Downstream**: `toritate` (ledger), `danjo` (public accountability)
- **Bounded away**: `malak` (no CTI / no ad-fraud case creation)

## Layout

| File | Role |
|---|---|
| `manifest.edn` | actor manifest (kotoba-native): id, glyph, gates G1–G9, cells, lexicons, commands, cross-actor |
| `kotoba/schema.edn` | EAVT datom schema (audience/campaign/creative/spend/performance/proposal) |
| `lex/*.edn` | NSID lexicons (campaign, audience, creative, performanceReport, spendRecord, proposal) |
| `.well-known/did.json` | did:web identity |
| `DESIGN.md` | full architecture (charter-clean inversion, topology, gates, cross-actor, rollout) |
| `../90-docs/adr/2606292130-…md` | decision record |

## Status / roadmap

- **R0** — scaffold (this commit): manifest + EAVT schema + lexicons + gates + did + DESIGN.
- **R1** — benchtop: single cohort propose→approve→publish loop, mock performance, akashi mirror stub.
- **R2** — pilot: first real outreach campaign under G1–G9, full akashi mirror.
- **R3** — multi-cohort fleet, cohort spend caps, Council promotion gates via Datalog over the audit log.

## Follow-up (standing-authorized)

Split repo `etzhayyim/com-google-ads` → west entry `orgs/etzhayyim/com-google-ads`
→ RAD identity journal (`:rad/repo "github.com/etzhayyim/com-google-ads"`,
`:rad/did-web "did:web:etzhayyim.github.io:com-google-ads"`), per CLAUDE.md
«Actors» section. R0 lands the design; the repo + west + RAD follow.
