# manabi open-education resource-registry — Verification Workflow (G14)

Per ADR-2605261045 (manabi R0 scaffold) + the G14 verified-resource-only
routing discipline. Every `com.etzhayyim.manabi.openEducationResource` record
ships `verificationStatus = unverified-seed`, and **no live learner-routing
surface may present an entry as confirmed against an `unverified-seed` or stale
record**. This file documents how an entry is moved through the three tiers —
the human/Council checks that gate confirmed routing.

> **R0 status (honest, G8)**: this is the *process spec*. **0 entries are
> verified.** All seed entries remain `unverified-seed`; they are best-effort
> routing scaffolds (URL + scope + license authored from public knowledge),
> NOT yet re-checked against the live official/recognized source. Verification
> execution begins at R1 (Council ratification + a resource-verification
> maintainer DID registered).

## What manabi IS (boundary re-statement — read before verifying)

manabi is a **lifelong-learning / open-curriculum substrate** (Liberation
Ladder L4–L5). It issues **NO degrees / transcripts / GPA** (ANTI-CREDENTIALISM,
G7 + Charter §2(e)) — only replayable `skillAttestation`. This registry is an
**OPEN-EDUCATION RESOURCE directory**: it **ROUTES** learners to free/open
learning resources. It does **NOT** credential, accredit, grade, or rank, and
it is **NOT** a school/university. Verification must never drift this directory
toward acting as an accreditor or toward routing learners into paid-credential
funnels.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | routing scaffold only; best-effort public refs | (initial) | discovery/guide design only — **no "confirmed source" presentation** |
| `maintainer-verified` | a maintainer has re-checked every field against the official/recognized source within the freshness window | resource-verification maintainer DID | surfaced as a confirmed routing target (R2) |
| `council-verified` | Council-reviewed for curriculum alignment (G4) + charter compliance | Council Lv6+ ≥3 | inclusion in a Council-reviewed curriculum module (R3) |

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for routing
even if its status is `maintainer-verified`.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each resource entry, a maintainer confirms against the **official or
recognized-authority source** (the `provenance` URL — the operator/institution's
own site, a government education ministry, or the recognized OER steward; never
a third-party blog or unaffiliated aggregator). **Fail-closed: if any check
cannot be confirmed, the entry stays `unverified-seed`.**

1. **`title`** — matches the official resource/initiative name (incl. native
   name where applicable).
2. **`provider`** — the named operating institution actually runs the resource.
3. **`jurisdiction`** — correct ISO-style country/region code (or a documented
   `global-*` steward); reflects where the operating institution / steward sits.
4. **`resourceKind`** — one of the closed vocabulary
   {`oer-repository`, `open-courseware`, `public-digital-library`,
   `open-textbook`, `gov-open-learning-portal`, `global-oer`} and is the
   accurate kind for this resource.
5. **`accessUrl`** — resolves to the actual learner entry point (catalog /
   course / library), not a marketing landing page; reachable worldwide
   (note any geo-restriction).
6. **FREE-ACCESS verification** — confirm the resource is genuinely free to
   access at the routed entry point. If only an audit/free tier is free while
   certificates/credentials cost money, the entry MUST say so and route ONLY to
   the free path. **An entry that is not free-to-access at the routed URL stays
   `unverified-seed`.**
7. **LICENSE verification** — confirm the stated `license`
   (e.g. `CC-BY` / `CC-BY-NC-SA` / `public-domain` / `free-access` / `mixed`).
   For `mixed`, confirm the per-item-rights caveat is in `notes` — a `mixed`
   collection MUST NOT be presented as wholesale openly-licensed. Distinguish
   *openly-licensed* (reusable/remixable) from merely *free-to-view*.
8. **`provenance`** — resolves, is an official/recognized source, and actually
   supports the title/provider/license/free-access claims above. **If provenance
   cannot be confirmed official/recognized, the entry stays `unverified-seed`.**
9. **`lastVerified`** — set to the verification datetime (UTC, ISO-8601 Zulu).
10. **ANTI-CREDENTIALISM boundary re-check (G7 + §2(e))** — confirm the entry's
    `notes` still re-states that manabi only ROUTES to this resource and does
    NOT credential/accredit/grade/rank; and that routing to this resource does
    not implicitly turn manabi into an accreditation path. For resources that
    themselves grant a state/institutional credential (e.g. an accredited public
    program), confirm `notes` makes explicit that the credential is issued by
    that external authority, **never by manabi**, and that manabi routes only to
    the free-learning use.

Only when **all 10** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## Worldwide per-jurisdiction provenance discipline (fail-closed)

The registry asserts worldwide coverage (>= 12 distinct jurisdictions). For
**each** jurisdiction, the maintainer confirms its entries' `provenance` is the
official/recognized source **for that jurisdiction** (national education
ministry, the operating public university/library, or the recognized OER
steward). A resource attributed to a jurisdiction whose provenance cannot be
tied to a recognized source there stays `unverified-seed` — fail-closed; no
"close-enough" cross-border substitution.

## maintainer-verified → council-verified (curriculum inclusion, G4)

Additional to the above, for an entry to be included in a Council-reviewed
curriculum module, Council Lv6+ ≥3 attestation per ADR-2605261045 / G4 confirms:

- multi-generational priority + anti-individualism + Wellbecoming alignment
  (no anti-addiction-violating funnels — §2(d)/G3);
- non-eschatology + comparative-religion stance (G14, for any religion content);
- Charter Rider §2 compliance scan (automated) on the routed resource framing.

## Current seed status (2026-06-02)

All entries `unverified-seed`; all carry `accessUrl` + https `provenance` +
`lastVerified` + non-empty `notes`; every `resourceKind` is within the allowed
vocabulary; coverage spans 12+ jurisdictions. Field values (URLs / scope /
license) were authored from established public knowledge and are routing
scaffolds, **not yet maintainer-verified** — drift expected (URLs move, license
terms change). **0 verified.**

## Machine-enforced floor

`70-tools/scripts/audit/test_manabi_registry_seed.py` pins, fail-closed: the
file parses with a non-empty `resources` list; every `resourceId` is unique;
**every** entry is `unverified-seed` (G14); every entry has a non-empty
`accessUrl` + https `provenance` + ISO-8601 `lastVerified`; coverage spans >= 12
jurisdictions; every `resourceKind` is in the allowed vocabulary; every `notes`
is non-empty and references the anti-credentialism / open-resource routing
boundary; and a top-level integer `freshnessWindowDays` is present. A seed
shipped pre-verified, missing a field, or drifting the boundary fails CI. This
is the machine floor only — it does NOT assert any entry is accurate; that is
the human checklist above.
