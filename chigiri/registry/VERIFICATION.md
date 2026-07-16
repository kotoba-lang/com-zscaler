# chigiri legal-aid REFERRAL registry — Verification Workflow (G14)

Per ADR-2605262700 §2 + §4 (G14 verified-referral-only routing). Every
`com.etzhayyim.chigiri.legalAidReferral` record in
`registry/legal-aid.seed.json` (key `referrals`, id field `referralId`) ships
`verificationStatus = unverified-seed`, and **no live action — referral
routing / wayfinding via the `chigiri_legal_aid_clinic` resolver
(`referral_match.py`) — may run against an unverified-seed or stale entry**.
This file documents how an entry is moved through the three tiers — the
human/Council checks that gate live referral routing.

> **R0 status**: this is the *process spec*. **No entry is verified yet**; all
> entries in `legal-aid.seed.json` remain `unverified-seed`. Verification
> execution begins at R1 (Council ratification + a referral-verification
> maintainer DID registered). The `chigiri_legal_aid_clinic` referral resolver
> is a pure registry query (`referral_match.py`); at R0 it routes only over
> unverified scaffolds and performs **no eligibility/means-test judgment**.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | best-effort public referral scaffold; wayfinding only | (initial) | registry/resolver design only — **no live referral routing** |
| `maintainer-verified` | a maintainer has re-checked all fields against the official source within the freshness window, and confirmed the target is a bona-fide public-interest legal-aid body with no consideration | referral-verification maintainer DID | **live referral routing** to the member (R2) via the `chigiri_legal_aid_clinic` resolver |
| `council-verified` | Council-reviewed; the durable, highest-assurance referral target | Council Lv6+ | **council-attested referral** eligibility (R3) — the top tier |

> **No 代行 tier.** Unlike toritsugi (whose top tier unlocks agent-on-behalf
> 提出代行), chigiri & musubi have **no agent-on-behalf path**. chigiri NEVER
> files, submits, or acts on a member's behalf; the top tier is
> `council-verified` **referral** — a high-assurance route to a licensed human
> counsel / legal-aid org, never a 代行 action.

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for routing
even if its status is `maintainer-verified`. Legal-aid hotlines, means-test
thresholds, scope, and URLs drift constantly — a stale entry is fail-closed.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each `referrals` entry, a maintainer confirms each field against the
**official authority source** (the `provenance` URL — see WORLDWIDE PROVENANCE
below for the per-jurisdiction official-domain rule):

1. **`referralId`** — stable, unique, descriptive (no collision; the
   machine-enforced floor pins uniqueness).
2. **`title`** — matches the official name of the legal-aid body / pro-bono
   program / court self-help centre (正式名称, native + EN where given).
3. **`jurisdiction`** — the ISO-coded jurisdiction the entry actually serves;
   note where the service is sub-national (state/province/自治体-scoped).
4. **`bloc`** — the coarse routing group is correct (e.g. `jpn-national`,
   `usa-state-ca`, `eu-member-de`, `ukcw-ca`, `eu-rest`).
5. **`authority`** — the named body is the bona-fide operator and is a
   **public-interest legal-aid body / bar pro-bono program / court self-help
   centre — NOT a for-profit firm solicitation** (the actor-specific check;
   see below).
6. **`channel`** — the access route (URL / hotline / walk-in) resolves and is
   the actual intake entry point, not a dead landing page; note where the
   concrete window resolves per the member's address (自治体/state variance).
7. **`legalBasis`** — the cited statute/instrument + article is current and
   actually establishes the legal-aid service (G8 non-fabrication). Re-check
   every verification: statutes are amended, section numbers drift.
8. **`language`** — the service-language code is correct.
9. **`provenance`** — resolves, is an **official source** (per the
   per-jurisdiction rule below), and actually supports the above fields.
   **If provenance cannot be confirmed official, the entry stays
   `unverified-seed`** (fail-closed).
10. **`confidence`** — honestly reflects residual uncertainty (`high` only when
    statute + provenance + official-domain all confirmed; `medium` when any
    is administrative/example-only/drifting — keep the caveat in `notes`).
11. **`lastVerified`** — set to the verification datetime (UTC).
12. **`verificationStatus`** — only flipped to `maintainer-verified` when all
    other checks pass.
13. **`notes`** — restates the UPL no-legal-advice boundary, names the residual
    drift/caveat, and confirms the no-consideration / referral-only regime.

Only when **all 13** pass — including the boundary re-check and the
no-consideration confirmation below — may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## WORLDWIDE PROVENANCE — the official-source check is per-jurisdiction

The registry is multi-jurisdiction (55 entries, 36 distinct jurisdictions at
iter-4). The `provenance` "official source" check is therefore **per-
jurisdiction**: the URL MUST be on the official-authority domain for that
jurisdiction, **NEVER a third-party blog, aggregator, or directory mirror**.
Representative official-domain families (non-exhaustive):

| Jurisdiction family | Official-domain signal (examples) |
|---|---|
| Japan | `*.go.jp` (e.g. `mhlw.go.jp`, `moj.go.jp`, `caa.go.jp`) + statutory bodies (`houterasu.or.jp`, `nichibenren.or.jp`) |
| USA (federal/state) | `*.gov` (`lsc.gov`, `uscourts.gov`, `nycourts.gov`, `courts.ca.gov`, `texasbar.com` as the official bar) |
| France | `*.gouv.fr` (`legifrance.gouv.fr`, `justice.fr`) |
| UK | `*.gov.uk` (`gov.uk`, charity register `charitycommission.gov.uk`) |
| EU | `europa.eu` (`eur-lex.europa.eu`, `e-justice.europa.eu`, `edpb.europa.eu`) |
| Germany | `*.bund.de` / `gesetze-im-internet.de` / `bmj.de` |
| Hispanophone | `*.gob.*` (e.g. `*.gob.es`, `*.gob.mx`, `*.gob.ar`) |
| Korea | `*.go.kr` |
| Other | the official-authority / statute / national-legislation domain (e.g. `legislation.nsw.gov.au`, `irishstatutebook.ie`, `nalsa.gov.in`, `sso.agc.gov.sg`, `ontario.ca`) |

**Fail-closed rule**: if provenance cannot be confirmed to sit on an
official-authority domain for that jurisdiction, the entry **stays
`unverified-seed`** — no exception for "looks authoritative". Aggregator/
directory targets that are themselves the legitimate operator (e.g.
`lawhelp.org`, a Pro Bono Net program) are routed-to as referral *targets* but
their provenance must still be confirmed; a generic blog summarizing the
program is never acceptable provenance.

## Staleness rule (`freshnessWindowDays = 180`)

An entry is treated as unverified for routing once
`now − lastVerified > freshnessWindowDays` days, **regardless of
`verificationStatus`**. `maintainer-verified` and even `council-verified`
entries fall back to fail-closed when stale and must be re-checked through the
full per-field checklist before routing resumes.

## Boundary re-check (encoded on every verification)

Verification is not complete until the constitutional boundary is re-confirmed
for the entry:

- **UPL strictly prohibited (G8/G14)** — chigiri renders **NO legal advice**.
  Confirm the entry's `notes` restates this and that routing to the target does
  not cause chigiri to give, draft, or characterize advice. The target's own
  licensed counsel / legal-aid staff give any advice — never chigiri.
- **Referral-routing only** — the entry is a routing **target** to licensed
  human counsel / a legal-aid org, not an advice source. The
  `chigiri_legal_aid_clinic` resolver routes; it does not adjudicate, file, or
  submit.
- **Zero compensation (no-consideration invariant)** — confirm routing to the
  target involves **NO consideration / fee** flowing in either direction
  (no referral fee in, no charge to the member out). Operation is
  Public-Fund-routed, donation-only.

## Actor-specific emphasis — CRITICAL

**Verification MUST confirm the referral target is a bona-fide public-interest
legal-aid body / bar pro-bono program / court self-help centre — NOT a
for-profit firm solicitation** — and that routing to it involves **NO
consideration / fee** (the no-legal-aid-consideration invariant). Specifically:

- Reject any target that is a private law firm's lead-generation / solicitation
  funnel dressed as "legal aid". The bona-fide signals: statutory legal-aid
  body, bar-association pro-bono / consultation program, court-run self-help /
  facilitator centre, or a funded access-to-justice nonprofit that routes
  on to licensed counsel.
- Confirm zero consideration in both directions: chigiri pays no referral fee
  and receives none; the member is charged nothing by chigiri for the routing.
  (Means-tested own-contributions paid by the member *to the legal-aid body
  itself* — e.g. NL `eigen bijdrage`, IE contribution — are the target's
  regime, not chigiri consideration; note them in `notes`, they do not breach
  the invariant.)
- **There is no 代行 tier.** The top tier is **council-verified referral** — a
  Council-reviewed, durable route to a licensed human counsel / legal-aid org.
  chigiri never files, submits, or represents.

## Current seed status (2026-06-02)

All entries in `legal-aid.seed.json` are `unverified-seed` (55 entries / 36
distinct jurisdictions at iter-4). Every entry carries `legalBasis` +
`provenance` (https) + `lastVerified` + a `notes` boundary caveat, but the
`authority` / `legalBasis` / `channel` / fee / threshold / URL fields are
**best-effort public references authored from the official sources and NOT yet
maintainer-verified** — they are wayfinding scaffolds, not authoritative
contacts. Drift is expected (hotline numbers, hours, means-test thresholds,
scope, brand renames, per-自治体/per-state variation). Several entries carry
`confidence: medium` with the reason recorded in `notes` (e.g. US-NY
administrative-order basis, US-TX Gov't Code section numbers, US-federal
per-district clinic unevenness, JP municipal free-consultation [no national
URL], FR point-justice locator drift, EU ECC-Net charter indirection, Ontario
FLIC example-only).

## Honest gaps (G8 — what is NOT yet done)

- **No entry is verified.** All 55 remain `unverified-seed`; no
  `maintainer-verified` / `council-verified` entry exists yet.
- The referral-verification **maintainer DID is not yet registered**, and
  Council ratification of ADR-2605262700 has not occurred — so R1 verification
  execution has not begun.
- This document is the **process spec only**; it does not itself verify any
  entry, and no field above has been independently re-checked against its
  official source as part of authoring it.

## Machine-enforced floor

`70-tools/scripts/audit/test_chigiri_registry_seed.py` (the fail-closed
registry-invariants test, R0-safe: test-only, network-free, no cell execution)
pins the constitutional properties of `legal-aid.seed.json` so a later refactor
cannot silently weaken them:

1. File parses as JSON and `referrals` is a non-empty list.
2. Every `referralId` is unique (duplicates fail-closed).
3. **Every entry ships `verificationStatus == "unverified-seed"` (G14)** — a
   seed shipped pre-verified fails CI.
4. Every entry has a non-empty `provenance` **https** URL + a `lastVerified`
   stamp.
5. Every entry has a `jurisdiction`; the registry spans **≥ 12 distinct
   jurisdictions** (guards JP-only AND core-only regression).
6. Every entry's `notes` is non-empty, and the registry restates its boundary
   regime — **UPL / referral-only / no legal advice / zero compensation** — on
   every entry's notes (the structural fail-closed caveat, not merely
   somewhere).
7. A top-level positive integer `freshnessWindowDays` is present.

These are the floor: passing them does **not** make an entry verified — it only
proves the seed is honestly shaped for the verification workflow above. The G14
routing refusal against an unverified/stale entry is enforced at routing time
by the `chigiri_legal_aid_clinic` resolver, not by this test.
