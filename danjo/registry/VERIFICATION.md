# danjo fiscal-source registry — Verification Workflow (G14)

Per ADR-2605301600 + ADR-2605302245 (global fiscal-flow extension), under the
danjo constitutional discipline (ADR-2605192100 §1.12 + §2(c)). Every
`com.etzhayyim.danjo.fiscalSource` record in `registry/sources.seed.json` ships
`verificationStatus = unverified-seed`, and **no live ingestion may run against
an unverified-seed or stale source** (G14 + G3 passive-only). This file
documents how a source is moved through the three tiers — the human/Council
checks that gate ingestion.

> **R0 status (honest, G8)**: this is the *process spec*. **0 of the seed
> sources are verified.** All entries remain `unverified-seed`. The catalog is a
> routing/ingestion scaffold of already-public official datasets, NOT an
> authoritative inventory. Verification execution begins at R1 (Council
> ratification + fiscal-source-verification maintainer DID registered). danjo
> finds + cross-references; kanae renders; **neither adjudicates**.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | catalog scaffold only; best-effort public refs | (initial) | catalog/wayfinding design only — **no live ingestion** |
| `maintainer-verified` | a maintainer has re-checked all fields against the official authority within the freshness window | fiscal-source-verification maintainer DID | passive ingestion of the already-public dataset (R1+) |
| `council-verified` | Council-reviewed; the source is part of the standing cross-reference corpus | Council Lv6+ | inclusion in `discrepancyObservation` cross-reference set (still NON-adjudicating) |

`freshnessWindowDays` (currently **180**) bounds staleness: a source whose
`lastVerified` is older than the window is treated as unverified for ingestion
even if its status is `maintainer-verified`.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each fiscal-source entry, a maintainer confirms against the **official
authority source** (the `provenance` URL, which MUST be an official-government /
recognized-international-body domain — never a third-party blog or aggregator):

1. **`title`** — matches the official dataset / portal name.
2. **`authority`** — the correct 省庁 / ministry / agency / supreme audit
   institution / international body owns and publishes this dataset.
3. **`jurisdiction`** — the ISO-3166-ish jurisdiction code (or `intl-*` body
   tag) is correct for the publishing authority.
4. **`sourceKind`** — correctly classifies the dataset
   (`budget-portal` / `open-spending` / `procurement-system` /
   `audit-institution` / `legislature-record` / `intl-aggregator`); must be in
   the allowed catalog set (machine-pinned, see below).
5. **`datasetUrl` + `format`** — the dataset URL resolves to the actual data
   (not a landing page), and `format` (HTML / CSV / JSON / API / XBRL / etc.)
   matches what is actually served. Note scraping vs. machine-readable API.
6. **`legalBasis`** — the cited statute / constitutional basis is current and
   actually establishes the publication mandate (G8 non-fabrication). Re-check
   on every verification: statutes are amended.
7. **`language`** — the dataset's primary language code is correct.
8. **`provenance`** — resolves, is an **official authority source**, and
   actually supports the above fields. **Per-jurisdiction official-domain
   check** (fail-closed): the provenance host must be an official-government or
   recognized-international-body domain for its jurisdiction, e.g.
   - `.gov` (US federal), `.go.jp` (Japan), `.gouv.fr` (France),
     `.gov.uk` (UK), `europa.eu` (EU institutions), `.gob.*` (Spanish-speaking
     gov, e.g. `.gob.mx` / `.gob.es` / `.gob.cl`), `.go.kr` (Korea),
     `.gov.br` / `.gob.* ` / `.gv.at` / `.gov.au` / `.gc.ca` / `.govt.nz` /
     `.gov.za` / `.gov.in` / `.gov.sg` and equivalents per jurisdiction;
   - recognized international bodies: World Bank, IMF, OECD, UN, IATI, OGP
     and their official `*.org` / `*.int` domains.

   **If provenance cannot be confirmed as an official authority domain, the
   entry stays `unverified-seed`** (fail-closed). No third-party aggregator,
   blog, news site, or mirror may stand in for the primary source.
9. **`lastVerified`** — set to the verification datetime (UTC, ISO-8601 Zulu).
10. **NON-adjudicating / observational re-check (boundary)** — confirm the
    source is ingested for FACTUAL cross-reference observation only. danjo MUST
    NOT assert that a crime / 不正 / law violation occurred; every downstream
    `discrepancyObservation` carries `nonAdjudicatingNotice=true`. If a source
    would only be usable to make a legal characterization (not a factual
    cross-reference), it does not belong in danjo — legal characterization is
    routed to human counsel via chigiri (Council Lv6+), never inside danjo. The
    catalog is a MIRROR of already-public data, **NOT a target-list, NOT
    enforcement, NOT 会計検査院 itself**.

Only when **all 10** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## maintainer-verified → council-verified (standing-corpus inclusion)

Additional to the above, for a source to enter the standing cross-reference
corpus used by `discrepancyObservation`:

- Council Lv6+ review of the source + its boundary exposure (does ingesting it
  risk drifting danjo from observation toward surveillance / §2(c) covert-ops?
  G3 passive-only must hold — danjo reads the already-published, IPFS-pinned
  `gov.dataset.*` corpus, it does NOT re-fetch from portals);
- confirmation that the open-method discipline (G6) holds: any detector
  heuristic that uses this source publishes a versioned `methodNote`.

Council inclusion still does NOT grant any adjudicating power. The censor's EYE
only, never the censor's SWORD.

## Current seed status (2026-06-02)

All seed entries `unverified-seed`; all carry `legalBasis` + https `provenance`
+ `lastVerified` + a non-empty `notes` boundary caveat; the catalog spans 30+
distinct jurisdictions plus international bodies (IMF / World Bank / OECD / UN /
IATI / OGP). **0 sources are maintainer-verified or council-verified.** The
`legalBasis` citations + provenance URLs were authored from the official sources
but are **not yet maintainer-verified** — they are ingestion scaffolds, not
authoritative inventory (drift expected, esp. for cross-jurisdiction portals
that reorganize URLs).

## Machine-enforced floor

`70-tools/scripts/audit/test_danjo_registry_seed.py` pins, fail-closed (8
invariants): the file parses + has a non-empty `sources` list; every `sourceId`
is unique; **every entry is `unverified-seed`** (G14 — a seed shipped
pre-verified fails CI); every entry has an https `provenance` + ISO-8601
`lastVerified`; the catalog spans **>= 12 distinct jurisdictions** (worldwide
guard against JP-only regression); every `sourceKind` is in the allowed catalog
set; every entry's `notes` is non-empty AND the registry references its
NON-adjudicating / observational boundary; a top-level integer
`freshnessWindowDays`. This is the machine floor; the per-field /
per-jurisdiction official-domain checks above are the human ceiling above it.
