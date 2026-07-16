# kataribe publication-channel registry — Verification Workflow (G14)

Per ADR-2605263600 (kataribe community press / publishing / translation
substrate). Every `com.etzhayyim.kataribe.publicationChannel` record in
`channels.seed.json` ships `verificationStatus = unverified-seed`, and **no
cell may route a member to a channel, ingest from it, or surface it as
authoritative while its entry is `unverified-seed` or stale (G14)**. This file
documents how an entry is moved through the three verification tiers — the
human/Council checks that gate any downstream use.

> **R0 status (honest, G8)**: this is the *process spec*. **0 entries are
> verified.** All seed entries remain `unverified-seed` — they are routing
> scaffolds pointing at OFFICIAL public-record channels + recognized
> press-freedom bodies, not authoritative, machine-confirmed contacts. Drift is
> expected (gazette URL paths, court search endpoints, NGO domains all move).
> Verification execution begins at R1 (Council ratification + channel-
> verification maintainer DID registered).

## Boundary re-check (CRITICAL — re-assert on EVERY verification)

kataribe is a **community press / publishing / translation substrate — NOT
commercial journalism, NOT a paid press class, NOT an official publisher**. This
registry is an **OBSERVATIONAL directory** of OFFICIAL public-record publication
channels (national gazettes / legal publications) + recognized press-freedom
bodies, for public-knowledge access + routing to official sources only. It
publishes nothing official itself and editorializes nothing.

A verifier MUST, before flipping any tier, re-confirm that the entry preserves
that boundary:

- the channel is an **official public-record source** (gazette / legislature /
  court / official translation body) OR a **recognized press-freedom body** —
  never a commercial outlet, paywalled vendor, ad-supported aggregator, or a
  channel kataribe could be mistaken for *operating*;
- the per-entry `notes` still carry the non-commercial / observational caveat
  (the machine floor below pins this — do not strip it);
- nothing in the entry implies kataribe issues official records, editorializes,
  or runs a paid press class (G3 no-ad-revenue, G5 no-commercial-platform).

**If the boundary cannot be re-confirmed, the entry stays `unverified-seed`**
(fail-closed).

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | routing/directory scaffold only; best-effort public refs | (initial) | directory display design only — **no authoritative routing/ingest** |
| `maintainer-verified` | a maintainer re-checked all fields against the official source within the freshness window | channel-verification maintainer DID | member-facing **routing + read access** to the channel (R2) |
| `council-verified` | Council-reviewed; eligible for ingestion into kataribe chronicle/translation pipelines | Council Lv6+ | **chronicle / translation ingestion** eligibility (R3) |

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for routing/
ingest even if its status is `maintainer-verified`.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each channel entry, a maintainer confirms against the **official authority
source worldwide** (the `provenance` URL must be the issuing government /
judiciary / official body, or — for a press-freedom body — that NGO's own
canonical domain; never a third-party blog or mirror as the authority of
record):

1. **`title`** — matches the official channel name (gazette / publication /
   body) in its own language and in English transliteration.
2. **`jurisdiction`** — the ISO-style code is correct AND the channel is in
   fact the official source *for that jurisdiction* (per-jurisdiction official-
   source provenance — fail-closed if the channel is unofficial or for another
   country).
3. **`channelKind`** — exactly one of `{official-gazette, legal-publication,
   press-freedom-org, open-access-archive, translation-resource}`; matches the
   channel's actual function (e.g. an aggregator is `open-access-archive`, not
   `official-gazette`).
4. **`publisher`** — the named issuing body (省庁/裁判所/legislature/official
   printer, or the NGO) is the true operator; note where a portal is operated by
   a different body than the one that issues the record.
5. **`accessUrl`** — resolves to the actual channel entry point (search/landing
   page of the gazette/publication/body), not a dead or moved path. http(s)
   accepted as-is (we do not mask real channel URLs).
6. **`provenance`** — resolves, is the official / canonical source, and actually
   supports the above fields (G8 non-fabrication). **If provenance cannot be
   confirmed official/canonical, the entry stays `unverified-seed`.**
7. **`contentType` / `access` / `language`** — match the channel (laws /
   case-law / official-notices / journalism-safety; free/open; languages
   served).
8. **`notes`** — non-empty, factually supports the entry, AND still carries the
   kataribe non-commercial / observational boundary caveat (see boundary
   re-check above).
9. **`lastVerified`** — set to the verification datetime (UTC, ISO-8601 Zulu).
10. **Non-commercial / observational boundary re-check (G3/G5)** — re-assert the
    boundary block above for this specific entry.

Only when **all 10** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## maintainer-verified → council-verified (ingestion eligibility)

Additional to the above, for an entry to be eligible for ingestion into
kataribe chronicle / translation pipelines:

- Council Lv6+ review of the channel + its boundary exposure (does routing to /
  ingesting from it risk presenting kataribe as a commercial outlet or official
  publisher? G3/G5/G6);
- confirmation that any downstream translation honours G12 (Murakumo-only) and
  any whistleblower-adjacent material honours G10 (encrypted envelope);
- a recorded Council gate reference.

## Current seed status (2026-06-02)

All entries `unverified-seed`; every entry carries `accessUrl` + `provenance` +
`lastVerified` + non-empty boundary-bearing `notes`; the registry spans 30+
jurisdictions across the official-gazette / legal-publication / press-freedom /
open-access-archive / translation-resource kinds. **0 verified.** The channel
identities were authored from official sources but **not yet maintainer-
verified** — they are routing scaffolds, not authoritative contacts (drift
expected, especially gazette/court endpoint paths).

## Machine-enforced floor

`70-tools/scripts/audit/test_kataribe_registry_seed.py` pins, fail-closed:
parses + non-empty `channels`; unique `channelId`; every entry
`verificationStatus == "unverified-seed"` (G14); every entry non-empty
`accessUrl` + `provenance` + `lastVerified` (http(s) allowed); `>= 12` distinct
jurisdictions; every `channelKind` in the closed enum; every `notes` non-empty
**and** carrying the non-commercial / observational boundary; top-level integer
`freshnessWindowDays`. A seed shipped pre-verified, missing a field, or with a
notes block that drops the boundary caveat fails CI. This test is the machine
*floor*; the per-field human checklist above is the *ceiling* that gates any
real verification.
