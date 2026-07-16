# moushibumi participation-registry — Verification Workflow (G14)

Per ADR-2605312400 §2 + §4 (G14 verified-target-only submission). Every
`com.etzhayyim.moushibumi.participationTarget` entry in the `targets` list ships
`verificationStatus = unverified-seed` and **no live action (`moushibumi_submit`)
may run against an unverified-seed or stale entry**. This file documents how an
entry is moved through the three tiers — the human/Council checks that gate
`moushibumi_submit`.

> **R0 status**: this is the *process spec*. **No entry is verified yet**; all
> entries in `targets.seed.json` remain `unverified-seed`. Verification execution
> begins at **R1** (Council ratification + participation-verification maintainer
> DID registered — see `moushibumi_*` cell scaffolds). At R0 no cell runs and
> nothing is submitted or dispatched anywhere.

The registry is now **worldwide / multi-jurisdiction** (JP + US/CA/NY/TX +
EU-level/DE/FR + GB/CA/AU/IN/SG + BR/MX/KR/UN + EU-REST/ASIA-REST/AMERICAS-REST/
MEA-OCEANIA). Verification is therefore **per-jurisdiction**: the "official
source" check below is evaluated against the *correct* national/supranational
authority domain for each entry, never a global default.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | routing/wayfinding scaffold only; best-effort public refs | (initial) | 案内 / 起草補助 design only — **no live action** |
| `maintainer-verified` | a maintainer has re-checked all fields against the official authority source within the freshness window | participation-verification maintainer DID | **member self-submission** guidance (R2) of 請願 / 陳情 / パブリックコメント / 意見 |
| `council-verified` | Council-reviewed; eligible for the gated 代行 path | Council Lv6+ (per G15 the 代行 path additionally needs Council Lv7+ unanimity + 行政書士法/UPL clearance) | **agent-on-behalf (代行) `moushibumi_submit`** eligibility (R3) |

`freshnessWindowDays` (currently **180**, top-level in `targets.seed.json`)
bounds staleness: an entry whose `lastVerified` is older than the window is
treated as unverified for dispatch **even if** its status is
`maintainer-verified`. A stale entry must be re-run through the per-field
checklist before `moushibumi_submit` will transmit.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each `targets` entry, a maintainer confirms each field against the **official
authority source** (the `provenance` URL — see WORLDWIDE PROVENANCE below):

1. **`targetId`** — stable, unique, descriptive (jurisdiction-prefixed); not
   reused for a different organ/channel.
2. **`title`** — matches the official channel name (請願 / 陳情 / パブコメ /
   citizen-initiative / petition / consultation の正式名称, in the source language).
3. **`jurisdiction`** — the ISO-style code matches the organ's actual
   jurisdiction (e.g. `jpn` / `usa` / `eu-wide` / `deu` / `intl-un`).
4. **`channelKind`** — correct discriminator (`petition` / `public-comment` /
   `election-info` / `citizen-initiative`); confirm it is not, e.g., a judicial
   or executive-complaint channel mis-tagged as legislative petition.
5. **`organ`** — the correct legislature / commission / ministry / committee owns
   this channel; confirm it is the *legislative-petition* / *consultation* organ,
   not a confusable sibling (e.g. KR National Assembly 국민동의청원 ≠ executive
   e-People; ZA Parliament petitions ≠ provincial legislature).
6. **`channelType` + `portalUrl`** — the filing channel is correct and, if a
   web portal, `portalUrl` resolves to the actual participation entry point (not
   a landing page or a defunct platform — e.g. CL `chileconvencion.cl` is dead).
7. **`introducingMemberRequired`** — TRUE only where a sitting member must
   sponsor/present the petition (e.g. JP 国会 請願 国会法 §79; CA Commons;
   MY Dewan Rakyat; CA-BC MLA). For those, the **member secures the
   introduction; moushibumi never lobbies, never solicits, never斡旋s a sponsor**.
8. **`submissionForm`** — the form / required identity (e.g. FranceConnect,
   ID Austria, constituent ZIP) + any signature threshold is current and
   complete.
9. **`deadline`** — matches the current statutory / convention window or
   signature threshold; many are per-rule/per-call and time-bound — flag the
   `(per-jurisdiction; confirm at source)` placeholders before any live use.
10. **`legalBasis` (根拠法令)** — the cited statute / constitution article /
    standing order is current and actually establishes the channel (G8
    non-fabrication). Re-check on every verification: laws, article numbers and
    standing-order numbering drift (the seed `notes` carry explicit DRIFT
    WARNINGs for TX / FR / KR / BR etc.).
11. **`language`** — the language code matches the official source.
12. **`provenance`** — resolves, is an **official** source for *that*
    jurisdiction, and actually supports the above fields (see next section).
    **If provenance cannot be confirmed official, the entry stays
    `unverified-seed`** (fail-closed).
13. **`lastVerified`** — set to the verification datetime (UTC).
14. **`notes`** — carries the boundary caveat (G3 neutrality, UPL/G5, any
    political-risk flag for the jurisdiction) and any unresolved confidence/DRIFT
    note honestly (G8).
15. **G3 political-neutrality re-check** — first-class (see boundary step below).
16. **行政書士法 / UPL re-check (G5)** — confirm the guide for this channel is
    案内 + 起草補助 only; if the channel inherently requires 作成代理 or legal
    judgment (e.g. drafting an articulated bill / ballot-measure text, assessing
    constitutionality), the entry's guide MUST route to **chigiri + licensed
    counsel**, not moushibumi.

Only when **all 16** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## WORLDWIDE PROVENANCE (per-jurisdiction official-source check)

Because the registry spans many jurisdictions, the `provenance` "official
source" test is evaluated **per jurisdiction** against the relevant authority's
own domain — never a third-party blog, aggregator, news site, or NGO tracker.
Indicative (non-exhaustive) official-domain families:

| Bloc / jurisdiction | Accept (official) | Examples in seed |
|---|---|---|
| Japan | `.go.jp` (+ `.lg.jp` for 自治体) | shugiin.go.jp · soumu.go.jp · public-comment.e-gov.go.jp |
| United States (fed/state) | `.gov` / `.state.*.us` | regulations.gov · federalregister.gov · congress.gov · vote.gov · sos.ca.gov · sos.state.tx.us |
| EU / supranational | `europa.eu` | citizens-initiative.europa.eu · europarl.europa.eu · eur-lex.europa.eu |
| France | `.gouv.fr` / `assemblee-nationale.fr` / `senat.fr` | service-public.gouv.fr · petitions.assemblee-nationale.fr |
| UK / Commonwealth | `.gov.uk` / `parliament.uk` / `.gov.za` / `.gov.ke` / `parl.*` / `aph.gov.au` / `ourcommons.ca` | petition.parliament.uk · electoralcommission.org.uk |
| Other national | official-authority domain for that state (`.go.kr` · `.gob.mx`/`.gob.*` · `.leg.br`/`.gov.br` · `bund`/`bundestag.de` · `parlament.gv.at` · `bk.admin.ch` · `npc.gov.cn` · `parlimen.gov.my` · `u.ae` …) | bundestag.de · senado.leg.br · petitions.assembly.go.kr |
| International org | the org's own domain | ohchr.org (UN) |

**Fail-closed rule**: if the maintainer cannot confirm `provenance` is the
official authority's own domain for that jurisdiction — or the entry currently
cites a helper/tracker microsite or aggregator (the seed flags several:
prsindia.org for IN, nasspublicpetitions.org for NG, etc., as *non-official*) —
the entry **stays `unverified-seed`** and `moushibumi_submit` refuses it.

## maintainer-verified → council-verified (代行 eligibility)

Additional to the per-field checklist, for an entry to be eligible for the gated
代行 (`agent-on-behalf`) `moushibumi_submit` path:

- Council Lv6+ review of the channel + its 行政書士法/UPL reserved-practice
  exposure (does submitting it on a member's behalf cross a reserved-practice
  line, or — for `introducingMemberRequired` channels — improperly substitute
  for the sponsoring member's role?);
- a recorded Council gate reference — and note that **executing** 代行 still
  requires the R3 gate (Council Lv7+ unanimity + 行政書士法 clearance),
  independent of this registry tier (G15);
- **self-submission remains the default**; 代行 is the explicit, narrow R3
  exception, not the norm.

## Boundary re-check (G3 political neutrality — first-class)

Political-neutrality (公選法-equivalent) is encoded as a **first-class checklist
item**, not a footnote. For every entry a verifier MUST confirm:

- **INFO + procedure ONLY.** The entry surfaces *how* to participate and the
  *official* channel — never *whether*, *for whom*, or *which way*.
- **No campaigning / endorsement / candidate or party ranking / vote
  solicitation / GOTV targeting** anywhere in the entry's guide or framing.
- For **`channelKind = election-info` entries**: confirm the entry links **ONLY
  to the official electoral authority** for that jurisdiction (e.g. 総務省 /
  選挙管理委員会 · Vote.gov / EAC / state election office · Electoral
  Commission UK · service-public.gouv.fr/elections.interieur.gouv.fr · CNE PT ·
  Servel CL · IEBC KE · INEC NG · Central Elections Committee IL) and **carries
  no partisan framing whatsoever**. An election-info entry that points anywhere
  but the official electoral authority, or that editorializes, **fails** and
  stays `unverified-seed`.
- For **`introducingMemberRequired` entries** (e.g. JP 国会 請願 国会法 §79;
  CA House of Commons; MY Dewan Rakyat; CA-BC): the note must record that **the
  member secures the introducing member; moushibumi never lobbies, solicits, or
  斡旋s a sponsor** (G3 + G9 non-partisan / non-commercial).
- Where the jurisdiction carries political-risk caveats (the seed flags HK / CN /
  MY / AE / TH), confirm the entry preserves the honest risk flag in `notes`.

This G3 gate protects Charter §1.12 / 1 SBT = 1 vote: moushibumi conveys the
member's own voice into the state, and must never bend that voice.

## Machine-enforced floor

`70-tools/scripts/audit/test_moushibumi_registry_seed.py` pins the fail-closed
registry invariants (pure JSON inspection — no cell import, no network, R0-safe):

1. file parses as JSON and ships a non-empty `targets` list;
2. every entry has a **unique** `targetId` (duplicates fail-closed);
3. **every** entry ships `verificationStatus == "unverified-seed"` (G14 — no
   entry may be pre-marked verified in the seed);
4. every entry carries a non-empty **https** `provenance` URL + a `lastVerified`
   stamp (G8);
5. every entry declares a `jurisdiction`, and the registry spans **≥ 12 distinct
   jurisdictions** (worldwide-coverage regression guard);
6. every entry's `notes` is non-empty AND the registry references its
   `公選法` / political-neutrality boundary regime (G3);
7. a top-level integer `freshnessWindowDays` is present.

A seed shipped pre-verified, missing a citation, or regressed to shallow/JP-only
coverage **fails CI**. The G14 dispatch refusal itself (no `moushibumi_submit`
against an `unverified-seed`/stale entry) lives in the `moushibumi_*` cell
scaffolds (R0: import-raise / gate-closed; see MATURITY.md R1 core notes).

> Run: `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest \
> 70-tools/scripts/audit/test_moushibumi_registry_seed.py -q` (plugin autoload
> disabled to avoid an unrelated environment-side langsmith/pydantic collection
> failure).

## What is NOT yet done (honest framing, G8)

- **No entry is `maintainer-verified` or `council-verified`** — every entry is
  `unverified-seed`. These are routing/wayfinding scaffolds, not authoritative
  contacts.
- The non-JP entries' `lastVerified = 2026-06-02` is **best-effort public
  reference**, not maintainer verification; several `notes` carry explicit DRIFT
  WARNINGs and `confidence medium` flags (TX statute sections · FR Assemblée/
  Sénat Règlement article numbers · KR 国会法 article · BR Câmara Ato da Mesa ·
  others) that MUST be re-confirmed at source before any live use.
- `moushibumi_submit` (live action / 代行) does not run at R0; verification
  execution begins at R1 with the maintainer DID registered and Council
  ratification recorded.
