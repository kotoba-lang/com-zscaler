# hagukumi care-support registry — Verification Workflow (G14)

Per ADR-2605261030 (hagukumi — L4 Care Tier-B actor). Every
`com.etzhayyim.hagukumi.careSupportProgram` record in
`registry/programs.seed.json` ships `verificationStatus = unverified-seed`. This
directory is a **routing scaffold** that points families to OFFICIAL public
care-support programs (child + elder) worldwide; **no entry may be presented as
authoritative, and no eligibility or benefit amount may be asserted, until it has
passed the human/Council checks below**.

> **R0 status (honest, G8)**: this is the *process spec*. **0 of the seed entries
> are verified.** All entries remain `unverified-seed` — they are best-effort
> routing references authored from official sources, NOT confirmed-current data.
> Verification execution begins at R1 (Council ratification + a
> care-program-verification maintainer DID registered). Until then the registry
> is informational wayfinding only.

## What this registry is NOT (constitutional boundary — re-check on every entry)

hagukumi is a community **CARE substrate** (Liberation Ladder L4 Care):

- **NOT a benefits-determination service.** Eligibility, amounts, ages, income
  rules, and assessment outcomes are **NOT determined here**. They vary by case
  and drift over time. The member MUST confirm eligibility and amounts directly
  with the named authority — the registry only points them to the official door.
- **NOT a licensed care provider.** hagukumi provides **no care service** through
  this registry. It does not deliver, broker, or guarantee any care.
- **NOT an official channel.** The registry mirrors official sources; it is never
  the government's own intake. The member always deals with the authority itself.

Every entry's `notes` re-states this boundary; the machine floor (below) enforces
that the no-eligibility/benefit-determination phrase **and** the
"NOT a licensed care provider" phrase appear in every entry's `notes`.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | routing scaffold only; best-effort public refs | (initial) | wayfinding / "here is the official program" pointers — **no assertion of eligibility or amounts** |
| `maintainer-verified` | a maintainer re-checked every field against the official source within the freshness window | care-program-verification maintainer DID | surfaced as a **current** official pointer (R2) |
| `council-verified` | Council-reviewed; cleared for prominent surfacing in care routing | Council Lv6+ | prominent care-routing surfacing (R3) |

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for surfacing
even if its status is `maintainer-verified`. Care programs drift frequently
(annual amount revisions, ministry reorganizations, statutory amendments), so
re-verification is mandatory, not optional.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each program entry, a maintainer confirms against the **official authority
source** (the `provenance` / `accessUrl` URL, which MUST be an official
government / inter-governmental domain for that jurisdiction — never a
third-party blog, aggregator, or commercial portal):

1. **`title`** — matches the official program name (native + transliteration).
2. **`jurisdiction`** — the ISO-style code maps to the authority that actually
   runs the program (national, or the correct inter-governmental body for
   `intl-*` reference entries).
3. **`careKind`** — correctly classifies the program within the care taxonomy
   (`child-allowance` / `parental-leave` / `childcare-subsidy` /
   `elder-care-benefit` / `disability-care-support` / `family-support-service` /
   `intl-reference`).
4. **`authority` (所管)** — the named ministry/agency/municipal role is the
   correct owner; note where concrete intake resolves locally (e.g. per-自治体
   / per-state) rather than at the national page.
5. **`accessUrl`** — resolves to the actual official program page (the door the
   family should walk through), not a dead link or generic landing page. If a
   legitimate official source is **http-only**, that is recorded as-is and
   surfaced (not masked) — but the maintainer flags it.
6. **`legalBasis` (根拠法令)** — the cited statute/instrument is current and
   actually establishes the program (G8 non-fabrication). Statutes are amended —
   re-check every verification.
7. **`summary` + `eligibilityNote`** — describe the program faithfully and make
   clear that **eligibility and amounts are not determined here**. No monetary
   amount is asserted as determinative (amounts drift; the seed deliberately
   avoids pinning figures).
8. **`provenance`** — resolves, is an official source, and actually supports the
   above fields. **If provenance cannot be confirmed official, the entry stays
   `unverified-seed`** (fail-closed).
9. **`lastVerified`** — set to the verification datetime (UTC, ISO-8601 Zulu).
10. **no-eligibility-determination + non-provider re-check** — confirm the entry
    and its surfacing make NO eligibility/benefit determination and never present
    hagukumi as a care provider or official channel; the family is always routed
    to confirm with the authority. If the program inherently requires
    case-specific determination (it always does), the surfacing MUST stay a
    pointer + "confirm with the authority", never an answer.

Only when **all 10** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## maintainer-verified → council-verified

Additional to the above, for an entry to be cleared for prominent care-routing
surfacing:

- Council Lv6+ review of the program + its care-boundary exposure (does any part
  of the surfacing risk reading as eligibility determination, care provision, or
  an official channel?);
- a recorded Council gate reference; the no-eligibility-determination +
  non-provider boundary is re-affirmed as immutable (G-series, ADR-2605261030).

## Current seed status (2026-06-02)

**0 verified.** All entries `unverified-seed`, spanning **32 jurisdictions**
(incl. `intl-*` inter-governmental reference entries) across the full care
taxonomy. Every entry carries a non-empty `accessUrl` + `provenance` +
`lastVerified` + boundary-bearing `notes`. All `accessUrl` and leading
`provenance` URLs are currently `https://` (no http-only official source has yet
been needed; the test permits http so a legitimate http-only source would be
surfaced, never masked). Citations are authored from official sources but **not
yet maintainer-verified** — they are routing scaffolds, expected to drift
(amounts revised annually; ministry pages reorganized).

## Machine-enforced floor

`70-tools/scripts/audit/test_hagukumi_registry_seed.py` pins, fail-closed:

- the file parses + `programs` is non-empty;
- every `programId` is unique;
- **every entry is `unverified-seed`** (no seed shipped pre-verified — G14);
- every entry has a non-empty http(s) `accessUrl` + `provenance` + ISO-8601
  `lastVerified`;
- the registry spans **>= 12 distinct jurisdictions** (guards worldwide
  coverage / regression to JP-only);
- every `careKind` is in the allowed care taxonomy;
- **every entry's `notes` re-states the no-eligibility/benefit-determination AND
  the non-provider boundary**;
- a top-level integer `freshnessWindowDays` is present.

A seed shipped pre-verified, missing a source, or missing the boundary caveat
fails CI. This test is the machine floor; the human/Council checklist above is
the substantive verification that this floor cannot perform.
