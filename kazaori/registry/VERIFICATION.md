# kazaori disaster-agency directory — Verification Workflow (G14)

Per ADR-2605263200 (kazaori CIVILIAN-ONLY disaster-coordination substrate) +
the G14 verified-source-only discipline. Every
`com.etzhayyim.kazaori.disasterAgency` record in `registry/agencies.seed.json`
ships `verificationStatus = unverified-seed`. This file documents how an entry
is moved through the three tiers — the human/Council checks that gate any
downstream routing/surfacing of an agency entry.

> **kazaori boundary (re-asserted here, IMMUTABLE)**: kazaori is a
> **CIVILIAN-ONLY** emergency-coordination substrate. This directory is
> **OBSERVATIONAL** only — a wayfinding mirror of OFFICIAL public civilian
> disaster-management agencies + early-warning / public-alert systems, for
> civilian preparedness and routing to official sources. kazaori **issues no
> alerts of its own, commands no response, and is NOT an official emergency
> service / channel**. The directory MUST route to official sources, never
> become or impersonate one, and MUST NOT coordinate armed-force actions
> (force authorization is separate per ADR-2605192315). No entry may be a
> target-list item; every entry is a public official source citation.

> **R0 status (G8 honest framing)**: this is the *process spec*. **0 entries
> are verified.** All seed entries remain `unverified-seed` — they are
> best-effort public source citations authored from official references, NOT
> maintainer-confirmed contacts (drift expected, esp. recently-reorganized
> agencies and recently-launched alert channels). Verification execution begins
> at R1 (Council ratification + an agency-verification maintainer DID
> registered).

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | wayfinding scaffold only; best-effort public refs | (initial) | directory display with an "unverified" badge — **no authoritative routing** |
| `maintainer-verified` | a maintainer has re-checked every field against the official source within the freshness window | agency-verification maintainer DID | surfacing as a confirmed official-source pointer (R1+) |
| `council-verified` | Council-reviewed; civilian-only + observational boundary independently re-confirmed | Council Lv6+ | inclusion in any cross-actor consumed view (e.g. damage_assessment routing) (R2+) |

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for routing
even if its status is `maintainer-verified`.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each agency entry, a maintainer confirms against the **official authority
source** (the `provenance` / `accessUrl` URL, which MUST resolve to the agency's
own official domain or an authoritative official reference — never an
aggregator/blog as the *sole* basis; where a seed cites Wikipedia or a store
listing as `provenance`, verification MUST replace it with the agency's own
official URL before flipping the tier):

1. **`title`** — matches the agency's official name (incl. local-language form).
2. **`jurisdiction`** — the ISO-style code is correct, and (for `intl-*`) the
   body is a genuine international/multilateral disaster body, not a national
   one mislabeled.
3. **`agencyKind`** — correctly classifies the entry within the closed
   taxonomy {`disaster-management-agency`, `early-warning-system`,
   `official-alert-channel`, `civilian-relief-coordination`,
   `intl-disaster-body`}; re-confirm the entry is the *kind* it claims (e.g. a
   pure relief NGO is not an `official-alert-channel`).
4. **`organization`** — the named ministry/agency/society currently owns this
   function (re-check after government reorganizations — several seed entries
   note very recent restructurings).
5. **`accessUrl`** — resolves to the live official public entry point (not a
   dead link, not a parked/aggregator domain).
6. **CIVILIAN status re-check (CRITICAL, ADR-2605263200)** — the operating body
   is a **civilian** authority, NOT a military command. Where a civilian agency
   *may task* military logistics in disasters (noted on several entries), confirm
   the listed entity is the civilian coordinating body itself. **If the entity
   is a military command, the entry MUST be removed, not verified** (fail-closed
   on the civilian-only invariant).
7. **`hazards` + `alertChannel`** — accurately describe scope and the official
   dissemination path; do not overstate (G8 non-fabrication) — an entry that is
   a coordination/policy body must not be described as a direct public alerter.
8. **`provenance`** — resolves, is an official/authoritative source, and
   actually supports the above fields. **If provenance cannot be confirmed
   official, the entry stays `unverified-seed`** (fail-closed).
9. **`lastVerified`** — set to the verification datetime (UTC, ISO-8601 Zulu).
10. **Observational-boundary re-check (G14 + ADR-2605263200)** — confirm the
    entry's `notes` still re-assert kazaori's CIVILIAN-ONLY + OBSERVATIONAL
    boundary, that the entry remains a *pointer to an official source* (not a
    channel kazaori operates), and that nothing in the entry could read as a
    target-list item or an operational command surface.

Only when **all 10** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## maintainer-verified → council-verified

Additional to the above, for an entry to be surfaced in any cross-actor
consumed view:

- Council Lv6+ independent re-confirmation of the **civilian-only** status
  (item 6) and the **observational** boundary (item 10) — the two invariants
  that, if breached, would put kazaori outside its constitutional discipline;
- a recorded Council gate reference; and note that any *operational* emergency
  action remains gated by the kazaori G8/G10 emergency-declaration lifecycle
  (Council Lv6+ ≥4/7 declaration), independent of this directory tier.

## Current seed status (2026-06-02)

All seed entries `unverified-seed`; **0 verified** (G8 honest). Every entry
carries a non-empty `accessUrl` + `provenance` (http(s)) + ISO-8601
`lastVerified`, an `agencyKind` in the allowed taxonomy, a `jurisdiction`
(spanning ≥12 distinct jurisdictions worldwide, incl. `intl-*` bodies), and a
`notes` field re-asserting the CIVILIAN-ONLY + OBSERVATIONAL boundary. Several
entries cite a Wikipedia or store-listing `provenance` and/or note recent
agency reorganizations or recently-launched alert channels — these are flagged
medium-confidence and MUST have provenance upgraded to the agency's own
official URL at verification time. None are authoritative contacts yet — they
are routing scaffolds.

## Machine-enforced floor

`70-tools/scripts/audit/test_kazaori_registry_seed.py` pins (fail-closed): the
file parses + `agencies` non-empty; `agencyId` unique; **every** entry
`unverified-seed` (G14); every entry has a non-empty http(s) `accessUrl` +
`provenance` + ISO-8601 `lastVerified`; ≥12 distinct jurisdictions; every
`agencyKind` in the allowed civilian-disaster taxonomy; every `notes` non-empty
and re-asserting the CIVILIAN-ONLY + OBSERVATIONAL boundary; and a top-level
integer `freshnessWindowDays`. A seed shipped pre-verified, missing a citation,
drifting out of the taxonomy, or dropping the boundary re-assertion fails CI.
This is the **machine floor**; the human checklist above is the verification
ceiling. The R0 routing/operational refusal itself lives in the kazaori cells
(R0: import-RuntimeError).
