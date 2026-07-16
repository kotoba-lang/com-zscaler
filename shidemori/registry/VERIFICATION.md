# shidemori death-registration registry — Verification Workflow (G14)

Per ADR-2605263800 (G14 verified-entry-only routing). Every entry in
`registry/registries.seed.json` ships `verificationStatus = unverified-seed`, and
**no entry may be presented to a bereaved member as an authoritative
death-registration deadline until a human has re-verified it against the cited
law**. This file documents how an entry is moved through the three tiers — the
human/Council checks that gate any verified-status flip.

> **R0 status (honest, G8)**: this is the *process spec*. **0 entries are
> verified.** All entries remain `unverified-seed` — they are routing scaffolds
> that point the bereaved at the OFFICIAL process, not authoritative contacts or
> deadlines. Verification execution begins at R1 (Council ratification +
> death-registration-verification maintainer DID registered).

## What shidemori IS / IS NOT (boundary re-check — do this FIRST)

Before verifying any field, re-confirm the entry stays inside shidemori's
constitutional boundary (ADR-2605263800):

- shidemori is a **community memorial substrate** and an **informational
  directory of OFFICIAL death-registration / civil-registry authorities** for
  **bereavement wayfinding**. It routes the bereaved to the official process; it
  files nothing on anyone's behalf.
- shidemori is **NOT a state-licensed mortuary**, **NOT a commercial
  funeral/cemetery/funeral-home business**, and **NOT a legal-advice service**.
- An entry that drifts toward selling a funeral/cemetery service, toward acting
  as the notifier on a family's behalf, or toward rendering legal advice on who
  must file MUST be rejected — keep it `unverified-seed` and route the bereaved
  to the official authority + (for legal questions) licensed counsel.

This boundary re-check is **fail-closed**: if you cannot confirm the entry is
purely informational official-authority wayfinding, it does not get verified.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | routing/wayfinding scaffold only; best-effort official refs | (initial) | informational routing only — **never presented as an authoritative deadline** |
| `maintainer-verified` | a maintainer has re-checked every field — above all the **statutory deadline** — against the official source within the freshness window | death-registration-verification maintainer DID | member-facing wayfinding with the deadline shown as verified |
| `council-verified` | Council-reviewed; cleared for any onward integration that surfaces the deadline as load-bearing guidance | Council Lv6+ | onward integration eligibility |

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified even if its
status is `maintainer-verified` — death-registration deadlines and authorities
change, and a stale deadline is as harmful as a wrong one.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each entry, a maintainer confirms against the **official authority source**
(the `provenance` URL, which MUST be the OFFICIAL government / civil-registry
domain for that jurisdiction — never a third-party blog, funeral-home site, or
aggregator):

1. **`deadline` (FOREGROUND — the most safety-critical field)** — re-verify the
   statutory registration deadline against the CITED LAW (`legalBasis`), not
   from memory and not from a secondary source. A wrong deadline directly harms
   a grieving family (missed statutory window → penalties, blocked burial /
   cremation, delayed estate steps). Confirm: (a) the number of days/months is
   exactly what the cited statute/article says; (b) the trigger event is correct
   (date of death vs. date the notifier became aware vs. date the certificate
   issued); (c) any abroad-death / unnatural-death / weekend-holiday variation
   is captured. **If the deadline cannot be confirmed verbatim against the cited
   law, the entry stays `unverified-seed`** (fail-closed).
2. **`legalBasis` (根拠法令 / statute)** — the cited statute + article is current
   and actually establishes the registration duty and its deadline (G8
   non-fabrication). Re-check on every verification: statutes are amended.
3. **`recordKind`** — matches what the authority actually does
   (death-registration-authority / death-certificate-issuer /
   burial-cremation-permit / civil-registry-office / intl-guidance); it is one of
   the five closed kinds (no mortuary/commercial kind).
4. **`authority` (所管)** — the correct office/ministry owns this step; for
   per-municipality steps, note that the concrete 窓口/address resolves at
   wayfinding time (not pinned in the seed).
5. **`accessUrl`** — resolves to the actual official entry point for this step
   (the registration office / certificate request / permit page), not a generic
   landing page.
6. **`procedure`** — the described who-files / what-to-bring sequence matches the
   official instructions; flag anything that varies by locality.
7. **`language`** — the stated official language(s) and any multilingual-guide
   availability are accurate.
8. **`jurisdiction`** — the entry is filed under the correct country/region code.
9. **`provenance`** — resolves, is the OFFICIAL source for this jurisdiction, and
   actually supports the deadline + legal basis above. **Worldwide rule
   (fail-closed): every jurisdiction's provenance must be that jurisdiction's
   own official government / civil-registry source.** If provenance cannot be
   confirmed official, the entry stays `unverified-seed`.
10. **`lastVerified`** — set to the verification datetime (UTC, ISO-8601 Zulu).
11. **Non-mortuary / non-commercial boundary re-check (repeat of the top
    section)** — confirm the entry remains pure official-authority wayfinding:
    no funeral/cemetery sales, no acting-as-notifier, no legal advice. Route
    legal questions (who must file, contested estates) to licensed counsel.

Only when **all 11** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## maintainer-verified → council-verified

Additional to the above, for an entry to be cleared for onward integration that
surfaces the deadline as load-bearing guidance:

- Council Lv6+ review of the entry, with explicit sign-off that the **deadline**
  and **legal basis** were independently re-verified against the official source;
- confirmation that no part of the entry crosses the non-mortuary /
  non-commercial / non-legal-advice boundary.

## Current seed status (honest, G8)

**0 entries verified.** All entries are `unverified-seed`; all carry
`accessUrl` + `provenance` + `lastVerified` + a non-empty `notes` boundary
caveat, and span 12+ jurisdictions. The cited deadlines + legal bases are
authored from official sources but are **routing scaffolds, not yet
maintainer-verified** — they MUST NOT be presented as authoritative deadlines
(drift expected, especially per-municipality and across jurisdictions).

## Machine-enforced floor

`70-tools/scripts/audit/test_shidemori_registry_seed.py` pins the fail-closed
data invariants: JSON parses + non-empty `registries`; unique `registryId`;
every entry `verificationStatus == "unverified-seed"` (G14); every entry has a
non-empty `accessUrl` + `provenance` + `lastVerified`; the registry spans >= 12
distinct jurisdictions; every `recordKind` is one of the five closed kinds;
every `notes` is non-empty and references the non-mortuary / non-commercial
boundary; a top-level integer `freshnessWindowDays` is present. A seed shipped
pre-verified, missing a citation/URL, or drifting out of the closed record-kind
set fails CI. **This test is the machine floor; it cannot confirm a deadline is
*correct* — only a human, per the checklist above, can do that.**
