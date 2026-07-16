# kokoro crisis-support registry — Verification Workflow (G14, SAFETY-CRITICAL)

Per ADR-2605263700 + Charter §1.13 Wellbecoming. Every
`app.etzhayyim.kokoro.supportLine` record ships `verificationStatus =
unverified-seed`, and **no kokoro cell (peer-support, crisis-escalation,
referral, or any router) may surface a support line to a person against an
`unverified-seed` or stale entry**. This file documents how an entry is moved
through the three tiers — the human/Council checks that gate live routing.

> **SAFETY-CRITICAL — read first.** This is a *crisis-support* directory. A
> person reaching for one of these numbers may be in life-threatening danger. A
> **wrong, transposed, decommissioned, or stale contact number is actively
> harmful** — it can send someone in crisis to a dead line or the wrong party.
> Verification here is therefore stricter than for any other kokoro/Tier-B
> registry: the **re-verification of each contact value against the official
> source is the foreground duty**, not a formality. When in doubt, the entry
> STAYS `unverified-seed` (fail-closed). It is always safer to withhold an
> unverified line than to route someone to a wrong number.

## kokoro boundary (re-checked every verification — G3)

kokoro (心) is **community + spiritual + relational mental-health SUPPORT
routing** — NOT clinical psychiatry, NOT state-licensed psychology, NOT
diagnosis/treatment (ADR-2605263700 G3 + Charter §1.13). This registry only
**ROUTES** a person to an OFFICIAL / recognized crisis-support line, and for
immediate life-threatening danger to the local EMERGENCY number. It renders **no
clinical opinion**. Every verification pass MUST re-confirm that the entry's
`notes` still frames the line as support-routing and does not drift into
implying kokoro provides clinical care, triage, or diagnosis.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | best-effort public-reference scaffold; **not safe for live routing** | (initial) | directory design / review only — **no live routing** |
| `maintainer-verified` | a maintainer has re-checked the contact value + every field against the **official source** within the freshness window | crisis-support-verification maintainer DID | live routing of THIS entry (R1) |
| `council-verified` | Council-reviewed as a stable, official, life-safety-grade entry | Council Lv6+ | inclusion in the default crisis-escalation surface (R2+) |

`freshnessWindowDays` (currently **90** — deliberately tighter than the
toritsugi 180-day window, because crisis numbers change and the cost of staleness
is a person sent to a dead line) bounds staleness: an entry whose `lastVerified`
is older than the window is treated as **unverified for routing** even if its
status is `maintainer-verified`.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each support-line entry, a maintainer confirms against the **official
authority source** (the operator's own site, or the relevant government / health
ministry / national-network page named in `provenance` — never a third-party
blog or unmaintained aggregator):

1. **`contact` (SAFETY-CRITICAL — verify first, character by character)** —
   re-key the exact number / short-code / text-code / chat URL from the
   official source. Confirm digit-for-digit (no transposition), the correct
   country/area context, that the line/short-code is **still in service**, and
   that any IVR path (e.g. "press 2", "option 4") is current. A single wrong
   digit fails the whole entry — keep it `unverified-seed`.
2. **`provenance` (worldwide per-jurisdiction official-source check —
   fail-closed)** — the URL resolves, is an **official / authoritative source
   for that jurisdiction** (national government, health ministry, the operating
   charity/NGO's own canonical site, or a recognized national network), and
   actually supports the `contact` + `hours` + `cost`. For a `*-intl-*`
   directory entry, provenance must be the directory's own canonical site. **If
   the source cannot be confirmed official/authoritative for that jurisdiction,
   the entry STAYS `unverified-seed`** (fail-closed). Do not "upgrade" an entry
   on the strength of an aggregator alone.
3. **`title` / `organization`** — match the official operator name (and its
   native-language name where applicable).
4. **`jurisdiction`** — the ISO/registry code is correct and the line actually
   serves that jurisdiction (note geographic limits, e.g. a short-code that only
   works in one city/region — record the limit in `notes`).
5. **`supportKind`** — correctly classifies the line (`emergency-number` /
   `crisis-hotline` / `text-or-chat-line` / `youth-line` / `specialized-line` /
   `intl-directory`).
6. **`hours`** — match the official posted hours; many lines are NOT 24/7 —
   confirm and, if hours rotate/are revised, mark as confirm-on-site.
7. **`cost`** — confirm toll-free vs navi-dial/local-rate; cost asymmetry
   between a free and a charged number for the same operator must be recorded
   (a person in crisis may have no credit).
8. **`languages`** — the supported language(s) and any dedicated
   foreign-language option are accurate.
9. **non-clinical support-routing boundary re-check (G3)** — confirm `notes`
   still asserts kokoro is NON-CLINICAL support-routing, names the boundary, and
   does not imply clinical care/diagnosis/triage. If a line has become
   clinical-only intake (not a support line), route it out — do not promote it.
10. **`lastVerified`** — set to the verification datetime (UTC) **only after 1–9
    all pass**.

Only when **all 10** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`. Because of
the SAFETY-CRITICAL nature, contact re-verification (step 1) and
official-source provenance (step 2) are hard gates: failing either keeps the
entry `unverified-seed` regardless of the rest.

## maintainer-verified → council-verified (default crisis surface)

For an entry to enter the default crisis-escalation surface (the lines kokoro
proactively offers, vs. lines only shown on explicit lookup):

- Council Lv6+ review that the entry is a stable, official, life-safety-grade
  line for its jurisdiction, with two independent maintainer verifications on
  record within the freshness window;
- the entry's `notes` re-asserts the non-clinical support-routing boundary and
  the emergency-number fallback;
- a recorded Council gate reference.

## Current seed status (2026-06-02) — honest framing (G8)

**0 of 127 entries verified.** All 127 entries are `unverified-seed`. All carry
a non-empty `contact`, a `provenance` URL, a `lastVerified` stamp, and a
non-empty `notes` that re-asserts the non-clinical support-routing boundary; the
registry spans 31 jurisdictions plus international directories. These are
**routing scaffolds authored from public references, NOT authoritative
contacts** — drift is expected and a maintainer MUST run the checklist above
before any line is routed to a person in crisis. The freshness window is held at
a tight **90 days** for this reason.

## Machine-enforced floor

`70-tools/scripts/audit/test_kokoro_registry_seed.py` pins (fail-closed): the
seed parses with a non-empty `lines` list; every `lineId` is unique; **every
entry is `unverified-seed`** (G14 — no crisis line ships pre-verified); every
entry has a non-empty `contact` + https `provenance` + ISO-8601 `lastVerified`;
the registry spans ≥12 jurisdictions; every `supportKind` is in the allowed
crisis-support set; every `notes` is non-empty and references the non-clinical
support-routing boundary; and `freshnessWindowDays` is a positive top-level
integer. A seed shipped pre-verified, missing a contact/provenance, or with a
blank/boundary-less note fails CI. Run:

```bash
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest \
  70-tools/scripts/audit/test_kokoro_registry_seed.py -q
```

The machine floor proves the seed is *well-formed and fail-closed*; it does NOT
prove any contact number is correct. Only the human checklist above does that.
