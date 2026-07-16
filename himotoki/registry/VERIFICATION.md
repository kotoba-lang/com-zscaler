# himotoki disclosure-target registry — Verification Workflow (G14)

Per ADR-2605302130 §2 + §4 (G14 verified-target-only dispatch). Every
`com.etzhayyim.himotoki.disclosureTarget` entry in `targets.seed.json` ships
`verificationStatus = unverified-seed` and **no live disclosure request
(`himotoki_dispatch`) may run against an unverified-seed or stale entry**. This
file documents how an entry is moved through the three tiers — the human/Council
checks that gate `himotoki_dispatch`.

> **R0 status**: this is the *process spec*. No entry is verified yet; all 65
> seed entries remain `unverified-seed`. Verification execution begins at R1
> (Council ratification + disclosure-verification maintainer DID registered).
> At R0 cells do not run and `himotoki_dispatch` does not transmit — live use is
> structurally impossible; the registry is a routing/wayfinding scaffold only.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | routing/wayfinding scaffold only; best-effort public refs | (initial) | compose/draft + guide design only — **no live dispatch** |
| `maintainer-verified` | a maintainer has re-checked all fields against the **official disclosure channel** within the freshness window | disclosure-verification maintainer DID | **member self-directed dispatch** of the member's OWN DSAR / a public-records FOIA via `himotoki_dispatch` (R2) |
| `council-verified` | Council-reviewed; eligible for any 代行/bulk-sensitive routing + FOIA public re-disclosure | Council Lv6+ (FOIA public re-disclosure additionally needs the §1.12 / 1-SBT-1-vote path per G11) | broader-exposure dispatch eligibility + re-disclosure (R3) |

`freshnessWindowDays` (currently **180**, set at the top of `targets.seed.json`)
bounds staleness: an entry whose `lastVerified` is older than the window is
treated as unverified for dispatch even if its status is `maintainer-verified`.
DSAR/FOIA contact data (portal URLs, controller DPO addresses, agency intake
points, statutory deadlines) drifts constantly across jurisdictions, so the
freshness window is load-bearing here, not cosmetic.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each disclosure-target entry, a maintainer confirms against the **official
disclosure channel** (the `provenance` URL — see WORLDWIDE PROVENANCE below):

1. **`organization`** — names the actual data controller (DSAR) or the
   record-holding public organ (FOIA), unambiguously; it is the unique id and
   MUST stay unique (the machine floor fails on duplicates).
2. **`jurisdiction`** — the ISO-style jurisdiction code is correct (`jpn` /
   `usa` / `eu-wide` / `deu` / `fra` / `gbr` / `kor` / … ) and matches the
   controller's / organ's seat for the cited statute.
3. **`regime`** — the disclosure regime is the one that actually governs this
   target (e.g. `appi-33`, `gdpr-15`, `ccpa-110`, `foia-us-5usc552`,
   `privacy-act-us-5usc552a`, `foia-jp-gyousei`, `lgpd-18`, `pipa-35` …) and is
   classified correctly as **DSAR (own-data)** vs **public-records (FOIA)**.
   Confirm `altRegimes` (where present) are genuine alternative routes, not
   decorative.
4. **`legalBasis`** — the cited statute + article is current and actually
   establishes the access right (G8 non-fabrication). Re-check on EVERY
   verification: statutes are amended (e.g. UK Data (Use and Access) Act 2025,
   Mexico post-INAI LFPDPPP 2025, CA CPRA recodification to Gov. Code §7920).
5. **`authority`** — the named first-line responder is correct, and the
   distinction between the **responder** (controller / agency FOIA office) and
   the **supervisor/complaint body** (DPA / regulator) is accurate — many DSAR
   regimes require sending the request to the controller, NOT the regulator.
6. **`channelType` + (`portalUrl` | `contactEmail` | `postalAddress`)** — the
   filing channel is correct and the concrete endpoint resolves to the actual
   request entry point (not a landing/policy page). Exactly one channel endpoint
   is populated consistent with `channelType` (`web-portal` / `email` /
   `postal`).
7. **`statutoryDeadlineDays`** — matches the statutory/official response window
   for this regime against the controller/authority (e.g. GDPR ~30 / CCPA 45 /
   LGPD 15 / UK FOI 20 working days / JP 行政文書開示 30). Where the regime has
   no fixed numeric deadline, leave it absent rather than invent one (G8). A
   present value is verified, not guessed.
8. **`feeJpy`** (where present) — matches the official fee (note variance; the
   seed value is a 目安). For non-JP fee-bearing regimes record the basis in
   `notes`, not a fabricated JPY figure.
9. **`formRef`** (where present) — points at a real chigiri template family
   (`chigiri:dsar:*` / `chigiri:foia:*`); himotoki pulls templates from chigiri
   and does not duplicate them.
10. **`language`** — the channel language code (ja/en/de/fr/pt/es/ko/…) matches
    the official channel's working language.
11. **`provenance`** — resolves, is an official source (per WORLDWIDE
    PROVENANCE), and actually supports fields 3–8. **If provenance cannot be
    confirmed official, the entry stays `unverified-seed`** (fail-closed).
12. **`lastVerified`** — set to the verification datetime (UTC).
13. **`notes` / boundary re-check (G3/G4/G10)** — the entry's caveat is intact
    and the boundary rules below re-confirmed.

Only when **all of the above** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

### WORLDWIDE PROVENANCE refinement (multi-jurisdiction)

The registry now spans 41 distinct jurisdictions across blocs (JP / US / EU /
UK-CW / EU-REST / ASIA-REST / AMERICAS-REST / MEA-OCEANIA / INTL-ROW). The
`provenance` "official source" check is therefore **per-jurisdiction**: the URL
MUST be an official-authority domain for the relevant jurisdiction —

- `.go.jp` (JP), `.gov` / `.gov.<state>` (US federal + states),
  `.gouv.fr` / official FR authority (e.g. cnil.fr, cada.fr),
  `.gov.uk` / ico.org.uk (UK), `europa.eu` (EU institutions),
  `.gob.*` (e.g. `.gob.mx`), `.go.kr` (KR), `.gov.br` / planalto (BR),
  `.gov.au` / oaic.gov.au (AU), `.gc.ca` (CA), `.gov.in` / rti.gov.in (IN),
  pdpc.gov.sg (SG), and the analogous official-authority / official-legislation
  / official-regulator domain for every other jurisdiction —

and **NEVER** a third-party blog, news article, law-firm summary, or
aggregator. NGO-run helper portals that appear in some seed `notes` (e.g.
FragDenStaat, AsktheEU.org, RTI helper sites) are explicitly **NOT** official
sources for verification purposes — confirm the official channel itself.

**Fail-closed**: if provenance for an entry cannot be confirmed as an official
per-jurisdiction source, the entry stays `unverified-seed` and is ineligible for
`himotoki_dispatch`. Inflated coverage is never preferred over honest
verifiability (G8).

## maintainer-verified → council-verified

Additional to the above, for an entry to be eligible for broader-exposure
dispatch (e.g. any 代行/bulk-sensitive routing, or FOIA public re-disclosure):

- Council Lv6+ review of the target + its re-disclosure / reserved-practice
  exposure (does filing or re-publishing cross a UPL line, or expose third-party
  PII?); legal characterization + appeal strategy route to **chigiri + external
  counsel** (G5), never authored here;
- FOIA public re-disclosure of disclosed records flows ONLY via the §1.12 /
  1-SBT-1-vote path (G11) — disclosed PII is never published or re-disclosed
  (N13).

## Boundary re-check (encoded into every verification)

Re-confirmed at maintainer-verify time and re-asserted at dispatch time
(ADR-2605302130 §4):

- **Consent-gated, identity-bound, own-data-only (DSAR) / public-records
  (FOIA)** — a DSAR is filed only for the **consenting member's own data** with
  Adherent-SBT/DID binding; never a third party's personal data (G3). FOIA is
  public-records and identifies the true requester.
- **Transparent, non-pretextual** — every request names the **true requester**;
  no pretext / sockpuppet / false identity / impersonation (G4, §2(c)).
- **Rate-limited** — no mass-filing, bulk agency enumeration, or flooding (G8);
  no profiling beyond the active request need.
- **Lawful-channel-only** — the official disclosure channel only; no
  unauthorized access, paywall/rate-limit circumvention, or ToS evasion (G10).
- **No legal advice** — templates, characterization, and appeal strategy route
  to chigiri + external counsel (G5, UPL). This registry and its verification
  produce no legal opinion.

### Dispatch gate (G3/G4 confirmation)

The `maintainer-verified` (within freshness window) tier is what gates
`himotoki_dispatch`. Immediately before any dispatch the operator MUST confirm,
per request:

1. **own-data-only** — the request is for the consenting member's OWN data
   (DSAR) or is a public-records (FOIA) request; consent + Adherent-SBT/DID
   binding present (G3); and
2. **non-pretextual** — the request names the true requester with no
   pretext/impersonation, on the lawful official channel (G4).

A failure of either confirmation, or a target that is `unverified-seed` /
stale, blocks dispatch — fail-closed.

## Current seed status (2026-06-02)

All 65 entries `unverified-seed`; all carry `provenance` + `lastVerified`; the
registry spans 41 distinct jurisdictions (worldwide long-tail coverage). Field
values (portal URLs, controller/DPO contacts, agency intake points,
`statutoryDeadlineDays`, fees) are best-effort public references authored from
the cited sources but **not yet maintainer-verified** — they are routing
scaffolds, never authoritative contacts, and drift is expected (entries tagged
`confidence: medium`, e.g. Mexico post-INAI reorganization, flag known
in-transit regimes).

## Machine-enforced floor

`70-tools/scripts/audit/test_himotoki_registry_seed.py` pins the fail-closed
registry invariants (R0-safe: test-only, network-free, no cell execution, no
live dispatch):

1. the file parses as JSON and ships a non-empty `targets` list;
2. every entry has a **unique** `organization` id (no duplicates);
3. **G14**: every entry ships `verificationStatus == "unverified-seed"` — no
   seed entry may be pre-marked verified;
4. every entry cites a non-empty `https` `provenance` URL and a parseable
   `lastVerified` timestamp;
5. every entry declares a `jurisdiction`, and the registry spans **≥ 12**
   distinct jurisdictions (worldwide regression guard against JP-only drift);
6. every entry carries a non-empty `notes` caveat, and the registry as a whole
   references its boundary regime (`own-data-only`, `unverified-seed`, and a
   `lawful-channel` / `consent-gated` marker);
7. a top-level positive integer `freshnessWindowDays` is present.

A seed shipped pre-verified, missing a citation, regressing jurisdiction
coverage, or dropping the boundary caveat fails CI. The G14 dispatch refusal
itself lives in the himotoki cell layer (R0: import-raise `RuntimeError`; Council
ratification is the only activation switch).

## What is NOT yet done (honest framing — G8)

- **No entry is verified.** All 65 remain `unverified-seed`; none has passed the
  per-field checklist above.
- **No `himotoki_dispatch`.** Cells raise on import at R0; nothing transmits.
- **No maintainer DID registered** for disclosure-verification; the
  `unverified-seed → maintainer-verified` transition has no authorized signer
  yet (R1).
- **provenance not re-fetched this session** for many entries; several carry
  `confidence: medium` pending the provenance re-fetch workflow (MATURITY.md
  item #9, medium → high promotion).
- **No kotoba KG / fleet placement** for himotoki cells yet (MATURITY.md #10,
  #11; R1-deferred).
