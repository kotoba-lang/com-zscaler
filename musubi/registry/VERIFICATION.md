# musubi ceremony-recognition registry — Verification Workflow (G14)

Per ADR-2605263400 §2 + §4 (G14 verified-recognition-only reliance). Every
`com.etzhayyim.musubi.ceremonyRecognition` record in
`ceremony-recognition.seed.json` ships `verificationStatus = unverified-seed`,
and **no live action (the `musubi_recognition_resolver` resolve / any
member-facing surfacing of a civil-recognition mapping) may run against an
unverified-seed or stale entry**. This file documents how an entry is moved
through the three tiers — the human/Council checks that gate live reliance.

> **R0 status**: this is the *process spec*. No entry is verified yet; all 54
> seed entries (across 36 distinct jurisdictions) remain `unverified-seed`.
> Verification execution begins at R1 (Council ratification per ADR-2605263400
> + a recognition-verification maintainer DID registered — see
> `musubi_recognition_resolver/cell.py`, which import-raises at R0). The
> resolver itself is coded to never opine legally and never confer civil status
> (`is_legal_opinion` / `confers_civil_status` pinned `False`), but that
> read-side guarantee is independent of, and does not substitute for, this
> verification gate.

## Why this registry is the harmful-if-wrong surface

This registry maps, **informationally**, whether and how a jurisdiction
recognises a religious / covenant rite (marriage / naming / funeral) and — the
field that actually matters — **what SEPARATE civil-registration step the member
must perform themselves**. A wrong `legalBasis`, a stale officiant-authorization
rule, or an implied "this rite is civilly valid" is a real-world harm: a member
could believe they are legally married / their child legally named / a death
lawfully registered when they are not. Verification exists to prevent exactly
that. **No "verified" entry may imply musubi performs the civil registration.**

## Tiers (`verificationStatus`)

musubi performs covenant ceremonies (Reformed 万人祭司, **NO clergy class**) and
**does NOT confer civil status**; like chigiri, musubi has **no 代行 tier** — it
never files the civil step for the member. The tiers therefore unlock
*informational surfacing*, never substitution for the member's own civil act.

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | wayfinding scaffold only; best-effort public refs, expected to drift | (initial) | registry/resolver design only — **no live surfacing to a member** |
| `maintainer-verified` | a maintainer has re-checked all fields against the official per-jurisdiction source within the freshness window | recognition-verification maintainer DID | **member-facing informational surfacing** of the civil-recognition mapping + the separate civil step (R1) |
| `council-verified` | Council-reviewed; the boundary framing (no clergy class / no civil status / no legal advice) and per-jurisdiction official provenance audited | Council Lv6+ (per ADR-2605263400 ratification) | **eligible for default inclusion** in the resolver's surfaced result set (R2+) |

There is deliberately **no agent-on-behalf / 代行 tier** (cf. toritsugi G15): no
maintainer or Council tier ever authorizes musubi to perform the member's civil
registration. Civil registration is always the member's own act.

`freshnessWindowDays` (currently **180**, top-level in the seed) bounds
staleness: an entry whose `lastVerified` is older than the window is treated as
unverified for live reliance even if its status is `maintainer-verified`.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each `recognitions[]` entry, a maintainer confirms against the **official
per-jurisdiction authority source** (the `provenance` URL — see WORLDWIDE
PROVENANCE below):

1. **`recognitionId`** — stable, unique, and still describes the entry's
   jurisdiction + ceremony track (no silent re-scoping under a stale id).
2. **`title`** — matches the official civil-procedure / recognition name in the
   jurisdiction (e.g. 婚姻届, Eheschließung beim Standesamt, county marriage
   license), including the local-language form.
3. **`jurisdiction`** — the ISO-style jurisdiction code is correct and the entry
   genuinely describes that jurisdiction's regime (not a neighbour's).
4. **`ceremonyType`** — `marriage` / `naming` / `funeral` /
   `cross-border-recognition` correctly classifies what civil step this maps.
5. **`authority`** — the named civil-registry / official authority is the one
   that actually registers the civil status (e.g. 市区町村役場 戸籍課,
   Standesamt, county clerk, Registro Civil, Civil Affairs Bureau); note where
   it resolves per-municipality at guide time rather than being pinned.
6. **`channel`** — the official filing channel / portal resolves to the real
   civil-registration entry point (not a landing page, not a third-party site),
   and any per-jurisdiction / per-municipality variance is flagged.
7. **`legalBasis` (根拠法令 / statute)** — the cited statute + article is
   **current** and actually establishes the recognition / civil step (G8
   non-fabrication). Re-check on every verification: statutes are amended
   (several entries already carry DRIFT WARNINGs about 2024–2025 reforms).
8. **`language`** — matches the jurisdiction's official source language
   (ja/en/de/fr/it/es/pl/nl/no/sv/zh/th/vi/id/ms/he/ar … as applicable).
9. **`provenance`** — resolves, is an **official per-jurisdiction source**
   (see below), and actually supports the `legalBasis` + `channel` claims.
   **If provenance cannot be confirmed official, the entry stays
   `unverified-seed`** (fail-closed).
10. **`confidence`** — `high`/`medium`/`low` honestly reflects how settled the
    mapping is; a `medium`/`low` entry (doctrine-based, state-specific, or in
    legal flux) must be re-checked with extra care and may not be promoted past
    `maintainer-verified` until the uncertainty is resolved.
11. **`lastVerified`** — set to the verification datetime (UTC) on every pass.
12. **`notes` (boundary caveat)** — confirm it (a) names the SEPARATE civil step
    the member must do themselves, (b) states the rite carries no civil effect /
    musubi does not confer civil status, (c) gives no legal advice (UPL), and
    (d) does not claim musubi performs the civil registration. This is the
    harmful-if-wrong field — see "Civil-recognition mapping" below.
13. **`verificationStatus`** — only set to `maintainer-verified` when **all of
    the above** pass; otherwise it stays `unverified-seed` (fail-closed).

Only when **all 13** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## WORLDWIDE PROVENANCE refinement (per-jurisdiction official source)

The registry is multi-jurisdiction (36 distinct jurisdictions and growing), so
the `provenance` "official source" check is **per-jurisdiction**, not a single
domain. A `provenance` URL is acceptable only if it is an **official-authority
source for that jurisdiction**, e.g.:

- Japan — `.go.jp` (e.g. `laws.e-gov.go.jp`, `moj.go.jp`, `mhlw.go.jp`)
- United States — `.gov` (state legislature / county clerk / SSA / CDC)
- France — `.gouv.fr` (`legifrance.gouv.fr`, `service-public.gouv.fr`)
- United Kingdom — `.gov.uk` / `legislation.gov.uk`
- EU-wide — `europa.eu` (`eur-lex.europa.eu`, `e-justice.europa.eu`)
- Germany — `gesetze-im-internet.de` / official Bund/Land portals
- Spanish-speaking — `.gob.*` (`gob.es`, `gob.mx`, `gob.pe`, `argentina.gob.ar`)
  / official Registro Civil / BOE / InfoLeg portals
- Korea — `.go.kr` (where applicable)
- and the equivalent **official-authority domain** for every other jurisdiction
  (e.g. `.gov.au`, `.govt.nz`, `gov.za`, `ris.gov.tw`, `mca.gov.cn`,
  `sso.agc.gov.sg`, `irishstatutebook.ie`, `lovdata.no`, `riksdagen.se`,
  `normattiva.it`, `uaelegislation.gov.ae`, `indiacode.nic.in`, etc.).

**NEVER** a third-party blog, aggregator, or commercial summary as the
authoritative `provenance`. **Fail-closed**: if provenance cannot be confirmed
to be an official source for that jurisdiction, the entry stays
`unverified-seed` — no exceptions, regardless of how plausible the content looks.
(A few seed entries currently cite non-`.gov`-style but official channels, e.g.
`law.justia.com` / `texas.public.law` / `commonlii.org`; these MUST be
re-anchored to the primary official source during verification or downgraded.)

## Staleness rule (`freshnessWindowDays = 180`)

An entry is live-reliable only if BOTH `verificationStatus` is at least
`maintainer-verified` AND `lastVerified` is within `freshnessWindowDays` (180)
of "now". An entry past the window is treated as `unverified-seed` for live
reliance until re-verified. Given the density of 2024–2025 statutory reforms
noted in the seed (UK medical-examiner regime, Chile Ley 21.676, Thailand
marriage-equality, PRC 2025 registration revisions, UAE civil-marriage law,
etc.), maintainers should expect frequent re-verification rather than treating
180 days as a comfortable margin.

## Boundary re-check (every verification, all tiers)

Independent of the field checklist, every verification MUST re-confirm the
structural boundary — this is a constitutional invariant, not a field value:

- musubi performs **covenant ceremonies** under Reformed **万人祭司** with **NO
  clergy class** (G3); it is not a clergy-bearing or state-licensed religious
  entity.
- musubi **does NOT confer civil status** and **does NOT perform civil
  registration**. The registry is an **INFORMATIONAL civil-recognition
  mapping only**.
- The registry gives **no legal advice** (UPL boundary) and **never claims to
  register a civil marriage** (or birth/death).
- A verified entry must make clear *whether/how* the jurisdiction recognises a
  religious rite **and** *what separate civil step* the member must do
  themselves — and must never imply musubi does that step for them.

If any entry's framing drifts toward "this rite is civilly valid through musubi"
or "musubi registers …", the entry fails verification and is reset to
`unverified-seed`.

## Civil-recognition mapping (the actor-specific emphasis)

The civil-recognition mapping is the **harmful-if-wrong** field. Verification of
each entry must therefore positively establish, against the **official civil-
registry authority** of the jurisdiction:

1. **Whether** the jurisdiction recognises a religious / covenant rite at all
   for the given `ceremonyType` (e.g. Japan/Argentina/PRC: none — civil
   registration is constitutive; Germany/Netherlands/France: civil act only,
   with ordering rules; Texas/Hong Kong/Kenya/Nigeria/Philippines: hybrid, only
   via an authorized/registered/licensed officiant or licensed venue).
2. **How** it recognises it, if at all (licensed venue, registered solemniser,
   concordat/intesa transcription, officiant return within N days, etc.) — and
   explicitly whether a musubi rite (no clergy class) could *ever* satisfy it
   (in most jurisdictions it cannot, and the entry must say so).
3. **What SEPARATE civil-registration step the member must perform themselves**,
   and **against which official authority** (the precise registry office /
   portal). This is the load-bearing output of the entry.

A verified entry that states or implies musubi performs, files, or completes the
civil registration is **disqualified** — reset to `unverified-seed`. The only
correct posture is: "here is the civil step, the member does it themselves at
this official authority."

## Machine-enforced floor

`70-tools/scripts/audit/test_musubi_registry_seed.py` pins the fail-closed
invariants of the seed so a future edit cannot silently weaken them:

1. file parses + non-empty `recognitions` list;
2. every `recognitionId` is unique (no duplicates);
3. **every** entry ships `verificationStatus == "unverified-seed"` (G14 — no
   seed entry may be pre-marked verified);
4. every entry has a non-empty `https://` `provenance` + an ISO-8601-ish
   `lastVerified`;
5. every entry has a `jurisdiction` AND the registry spans **≥ 12 distinct**
   jurisdictions (worldwide coverage; guards against regression to JP-only);
6. every entry's `notes` is non-empty AND references the boundary regime
   (informational-only / does-not-confer-civil-status / no-legal-advice), and
   the top-level `_comment` documents the regime;
7. a top-level integer `freshnessWindowDays` is present.

A seed shipped pre-verified, missing a citation, lacking the boundary caveat, or
regressing jurisdiction coverage fails CI. (Run network-free; the seed's
langsmith-incompatible environment is avoided with
`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1`.) The G14 live-reliance refusal itself lives
in `kotodama/py/.../musubi_recognition_resolver/cell.py` (R0: import-raise).

## Current seed status (2026-06-02)

All 54 entries `unverified-seed`; all carry `legalBasis` + `https` `provenance`
+ `lastVerified=2026-06-02` + the standard boundary caveat; 36 distinct
jurisdictions. **Nothing here is maintainer-verified or council-verified** — the
`authority` / `channel` / `legalBasis` values are best-effort public wayfinding
scaffolds, expected to drift (especially per-municipality / per-state and across
the flagged 2024–2025 reforms), and are **not authoritative, not for live
reliance, and not legal advice**. The `confidence=medium`/`low` entries (e.g.
内縁/事実婚, 自治体パートナーシップ, India Special/Hindu Marriage Act, Italy/Spain/
Poland concordat-transcription, Israel, Egypt) are doctrine- or state-specific
and need particularly careful re-verification before any promotion.
