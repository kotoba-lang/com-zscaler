# com-google-ads (広) — Charter-Clean Outreach & Performance-Marketing Actor Design

> **kotoba-native** (ADR-2606292130, R0 scaffold). Canonical manifest:
> `manifest.edn`; data model: `kotoba/schema.edn`; lexicons: `lex/*.edn`;
> DID: `.well-known/did.json`. The buy/create side of the **akashi 証**
> disclose/verify loop.

## 1. Premise: what an ad platform is, and what we will not inherit

"Google Ads" stands in for the whole performance-marketing ad-platform
category. Its telos is **surveillance-based conversion optimization**:

| Ad platform substrate (the harm) | Why a commons actor cannot inherit it |
|---|---|
| Individual behavioral targeting / profiling / retargeting | Surveillance capitalism; incompatible with self-sovereignty (talent G1/G2). |
| Cross-site tracking pixel / ad SDK / third-party identity | Builds a cross-context identity graph the org must never hold (G8). |
| Auto-optimized spend, no human sign-off | Spend actuated by an optimizer answerable to no one (G1). |
| Opaque spend | No audit trail; finance has no lever (G4). |
| Purchased / scraped / third-party PII audiences | Prohibited sources, license notwithstanding (G3, talent-G1 lineage). |
| Microtargeting protected categories / dark patterns | Manipulation of religion/ethnicity/health/politics (G5). |
| Vendor LLM narration | External dependency, opaque (G6, ADR-2605215000). |
| Disclosures are the platform's, not the advertiser's | The org amplifies without disclosing — a governance failure (G7). |

The design problem is **not** "use Google Ads to amplify the mission." It is
**"how does a religious-corp/commons actor do outreach — surface its mission
to people who would opt into hearing about it — under invariants that make
the surveillance/ad-tech harms structurally impossible."**

akashi (証) already defines the **disclose/verify** side for this org:
passive, public, non-adjudicating ad-transparency evidence. com-google-ads
is the missing **buy/create** side. Together they are one loop: **the org
discloses what it amplifies.**

## 2. The core contract

```
cohort (from talent / self-sovereign opt-in, G3)
        │
        ▼
   ┌──────────────┐   proposal    ┌──────────────────┐
   │ ProposeCell  │ ────────────▶ │  PolicyGovernor  │  (independent: G2/G3/G5)
   │ (Murakumo    │  campaign +   │  cohort-only? •  │
   │  LLM, sealed)│  bid/budget/  │  consent-proven? │
   │              │  creative     │  anti-manip?     │
   └──────────────┘               └────────┬─────────┘
                              accept ◀─────┴────▶ reject / hold
                                │                     │
                          [interrupt-before           │  zero-spend pause
                           :request-approval]        │  (the MRC analog)
                                │                     │
                          finance DID signs off       │
                                ▼
   ┌──────────────┐   publish      ┌──────────────────┐
   │  SpendCell   │ ─────────────▶ │  DiscloseCell    │ ──▶ akashi 証 (G7)
   │ (append-only,│  campaign +    │  creative + spend│     + toritate / danjo
   │  finance-    │  spend datoms  │  range disclosure│
   │  signed, G4) │                └──────────────────┘
   └──────────────┘
        ▲
        │ aggregate performance (impressions/clicks/conv by cohort — no individual, G2)
   ┌──────────────┐
   │ PerformCell  │  (read-time aggregates flagged :bond/is-transient — never durable verdicts)
   └──────────────┘
```

**The single invariant** (analogous to robotaxi's "AR1 never actuates a
rejected trajectory"):

> **com-google-ads never publishes a campaign, bid, budget, or creative that
> a human (finance/Council) has not approved, and never targets an
> identifiable individual.**

The ProposeCell is a sealed intelligence node (Murakumo LLM advisor) returning
**proposals only**. The PolicyGovernor is an independent system (rules, not
LLM) that checks the cohort/consent/anti-manipulation invariants and can
**reject** a proposal — the MRC analog is a *zero-spend pause* (safe fallback
when governance fails or confidence is low). `interrupt-before
#{:request-approval}` is a real finance/Council sign-off, not a rubber stamp.
Performance is **observe-only and aggregate-first**; no per-individual journey
is representable.

## 3. Actor topology

```
GoogleAdsSystem (root)
├── AudienceCell ……… self-sovereign opt-in cohort registry (k-anon, hard-delete; G3)
│     ← cohorts flow in from talent's consented registry
├── ProposeCell ……… sealed Murakumo LLM advisor → campaign/bid/budget/creative proposals
├── PolicyGovernor … independent rules censor (G2 cohort-only / G3 consent / G5 anti-manip)
├── ApprovalActor …… human finance/Council sign-off (interrupt-before :request-approval; G1)
├── SpendCell ………… append-only, finance-signed ledger (G4); cumulative ≤ budget-cap
├── PerformCell ……… aggregate-first performance read (transient; G2)
├── DiscloseCell …… mirrors creative + spend range → akashi 証 (G7), spend → toritate/danjo
└── (bounded away from malak — no CTI / no ad-fraud case creation)
```

A child fault escalates to the parent, which falls the campaign back to a
**zero-spend pause**. Everything is checkpointed on the kotoba Datom log, so
"why did we spend here / who approved this / what cohort was targeted" is a
Datalog query over the proposal+spend audit trail.

## 4. Data model (kotoba EAVT — `kotoba/schema.edn`)

Six entity families. Governance is structural, not advisory:

- `:audience/*` — self-sovereign opt-in cohort, k-anonymous, **hard-retractable**
  (GDPR Art 17; no `:_alive` flag). **There is deliberately no individual-member
  attribute** — only cohort-level aggregates (G2 anti-surveillance is structural).
- `:campaign/*` — cohort-bound (`:campaign/cohort` MUST resolve to an audience),
  budget-capped, approval-gated (`:campaign/approved-by` required for `:publishing`).
- `:creative/*` — content hash + G5 review verdict + `:creative/akashi-ref` (set on publish, G7).
- `:spend/*` — append-only, finance-signed, monotonic `:tx`; `:spend/range-bucket` is the
  only field disclosed publicly (a RANGE, G7).
- `:performance/*` — aggregate, `:db/is-transient true` (read-time, itonami-G3 lineage — never
  durable verdicts). Finest grain is the cohort.
- `:proposal/*` — proposal + governor verdict + human disposition = the audit trail.

Identifying contact PII (if any) is `signal:v1:` ciphertext (ADR-2605181100);
plaintext is refused at the gate.

## 5. Constitutional gates (G1–G9)

| Gate | Name | Rule (one-line) |
|---|---|---|
| G1 | propose-not-actuate | publish requires human sign-off via `interrupt-before`; actor only proposes. |
| G2 | cohort-scale-only | no `:person/*`; audiences are aggregate cohorts; anti-surveillance. |
| G3 | consent-gated-audience | self-sovereign opt-in + public-credential only; Signal-E2E PII; GDPR Art 17 hard delete. |
| G4 | spend-append-only-finance-signed | durable EAVT, finance DID-signed; no off-book spend. |
| G5 | anti-manipulation | no protected-category microtargeting / dark patterns / deceptive creatives. |
| G6 | murakumo-only-narration | ADR-2605215000; no vendor LLM. |
| G7 | akashi-transparency-mirror | every published creative + spend range mirrored to akashi 証. |
| G8 | no-ad-sdk-no-tracking-pixel | no Meta Pixel / GA4 ads / third-party ad SDK / affiliate code. |
| G9 | no-commercial-resale | no audience/lead resale, no competitor-intel SaaS; mission-bound. |

Full text in `manifest.edn` `:actor/gates`.

## 6. Cross-actor boundaries

| Direction | Actor | Relationship |
|---|---|---|
| upstream | `talent` | self-sovereign opt-in cohort supply (consented registry) |
| upstream | `isco` | occupation taxonomy for cohort keys |
| peer | `akashi` 証 | disclosure mirror target (G7) — the one-loop buy/disclose pair |
| peer | `moushibumi` | shared no-political-profiling boundary (no voter persuasion) |
| downstream | `toritate` | ledger — spend records |
| downstream | `danjo` | public accountability — spend transparency |
| bounded-away | `malak` | no CTI / no ad-fraud case creation; fraud/malware landing evidence routes akashi→malak |

## 7. Phased rollout

- **R0** (this ADR) — scaffold: manifest + EAVT schema + lexicons + gates + did + DESIGN. Cells raise at import until configured (mirrors akashi R0).
- **R1** — benchtop: single cohort, propose→approve→publish loop with mock performance, finance sign-off via interrupt, akashi mirror stub.
- **R2** — pilot: first real outreach campaign (e.g. a donation drive) under G1–G9, aggregate performance read, full akashi mirror.
- **R3** — multi-cohort fleet, cohort-level spend caps, Council promotion gates as Datalog queries over the spend+proposal audit log.

## 8. What is real vs. mocked (at R0/R1)

**Real & designed**: the propose-not-actuate invariant, cohort-only anti-surveillance
(structural — no individual attribute exists), consent-gated audience, append-only
finance-signed spend, akashi transparency mirror, creative anti-manipulation gate,
Murakumo-only narration, the buy/disclose one-loop with akashi.

**Mocked (R1 swap points)**: ProposeCell LLM (→ Murakumo LiteLLM loopback), the live
ads-API publish adapter (→ the real first-party/consented channel — NOT a tracking-pixel
ad network), performance ingestion (→ platform aggregate reports, never individual).

**Structurally impossible by design**: individual targeting, tracking pixels, purchased
audiences, off-book spend, voter microtargeting.

## 9. Relationship to `googleads-compat`

`googleads-compat` (ADR-260607, L4, REST clean-room, 30 endpoints) is an
API-compatible **external surface** — Google's entity shapes cloned for
interop. com-google-ads does NOT extend it. The native actor owns its own
EAVT data model, governance gates, and cross-actor boundaries. The compat
shell may, at R2+, be wired as one (gated, first-party) publish adapter
behind the DiscloseCell — but the gates, not the compat surface, are
authoritative.

## 10. Outward registration (owner-authorized 2026-06-29, standing-auth per CLAUDE.md «Actors»)

Per CLAUDE.md «Actors», `20-actors/com-google-ads` (in-root design home) is
**unseparated** until the child repo + west entry + RAD identity are landed.
Owner directive 2026-06-29 authorizes the outward flow as standard (not
per-step-confirmed). Completion condition:

- **child repo**: `github.com/etzhayyim/com-google-ads` (private, plain-git;
  west path `orgs/etzhayyim/com-google-ads`). Per user directive the repo is
  `com-google-ads` (the `com-` prefix plays the role `com-etzhayyim-` plays
  for other actors), NOT `com-etzhayyim-google-ads`.
- **west entry**: `manifest/repos.edn` `:extra-projects` → `manifest/west.yml`
  via the API single-entry commit (CLAUDE.md «manifest-workflow»; `--check`
  canonical, pin == child HEAD).
- **RAD identity**: `80-data/kotoba-rad/com-google-ads.identity.journal.edn`
  — `:rad/name "com-google-ads"`, `:rad/repo "github.com/etzhayyim/com-google-ads"`,
  `:rad/did-web "did:web:etzhayyim.github.io:com-google-ads"`,
  `:rad/aozora-collection "com.etzhayyim.apps.googleads"`. Identity SSoT =
  `00-contracts/schemas/actor-profile-seed.kotoba.edn`; the RAD journal +
  `did.json`/`profile.json` are generated from it.

**Status (landed)**: ADR + scaffold (manifest + EAVT schema + lexicons + did + DESIGN)
authored. Outward registration: ✅ child repo `github.com/etzhayyim/com-google-ads`
(private, HEAD `e9ffefb`) created + pushed; ✅ west pin — `repos.edn`
`:extra-projects` + `west.yml` block (pin `e9ffefb` == child HEAD) via API
single-entry commit `d57f86f` on `com-junkawasaki/root` main (survived a
concurrent rebase to tip `640a8f9`); ✅ `actor-profile-seed.kotoba.edn` SSoT
entry (apex `did:web:etzhayyim.com:actor:com-google-ads`, glyph 広, lexicon
`com.etzhayyim.googleads`) added in this PR. ⏳ signed RAD identity journal —
operator-key step via `70-tools/src/etzhayyim/kotoba_rad_sign.clj`
(no-server-key; the seed entry is the precursor). `gen-west-manifest.bb --check`
to be run once the superproject is synced (origin/main is volatile / force-pushed
by concurrent agents).
