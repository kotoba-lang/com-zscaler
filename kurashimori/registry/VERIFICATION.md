# kurashimori remedy-registry — Verification Workflow (G14)

Per ADR-2605312500 §2 + §4 (G14 verified-remedy-only send). Every
`com.etzhayyim.kurashimori.remedyTarget` entry under `targets.seed.json` ships
`verificationStatus = unverified-seed`, and **no live action (`kurashimori_send`)
may run against an `unverified-seed` or stale entry**. This file documents how an
entry is moved through the three tiers — the human/Council checks that gate
`kurashimori_send`.

> **R0 status**: this is the *process spec*. **No entry is verified yet**; all
> 65 seed entries remain `unverified-seed`. Verification execution begins at R1
> (Council ratification + remedy-verification maintainer DID registered — see the
> `kurashimori_*` cells, which stay import-raise until then). At R0 no cell runs
> and nothing is sent.

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | diagnosis/guide scaffold only; best-effort public refs | (initial) | 診断/起草補助 design only — **no live send** |
| `maintainer-verified` | a maintainer has re-checked all fields against the official source within the freshness window | remedy-verification maintainer DID | **member self-send** of the drafted 通知/苦情 (R2, `kurashimori_send` to the member's own merchant) |
| `council-verified` | Council-reviewed; eligible for the 代行 path | Council Lv6+ (per G15 the 代行 path additionally needs Council Lv7+ + 司法書士法/行政書士法 clearance) | **agent-on-behalf (代行)** eligibility (R3) |

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for dispatch
even if its status is `maintainer-verified`. A stale `maintainer-verified` entry
must be re-checked through the full per-field checklist before it can gate
`kurashimori_send` again.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each `remedyTarget` entry, a maintainer confirms against the **official
authority source** (the `provenance` URL — see WORLDWIDE PROVENANCE below; it
MUST be an official-authority domain, never a third-party blog or aggregator):

1. **`title`** — matches the official remedy/scheme name (制度・条文の正式名称),
   in the entry's `language`.
2. **`jurisdiction`** — the ISO bloc/country code is correct; for federal
   systems (e.g. `usa`) note when an entry is a *representative state example*
   (CA/NY/TX) and not the only applicable rule.
3. **`remedyKind`** — the coded kind (`cooling-off` / `return-policy` /
   `chargeback` / `warranty` / `escalation-public` / `escalation-adr`) actually
   matches the cited basis; a withdrawal right is not a faulty-goods right, a
   shipment rule is not a change-of-mind right (US Mail-Order vs EU CRD), etc.
4. **`statutoryWindowDays`** — see the CRITICAL emphasis below; this is the
   highest-risk field. Re-verify the number against the statute on **every**
   verification.
5. **`windowStart`** — the 起算 rule (inclusive vs翌日起算 / calendar vs business
   days / "from possession" vs "from contract conclusion" vs "from receipt of
   the written copy") matches the statute; a correct `statutoryWindowDays` with a
   wrong `windowStart` is just as harmful.
6. **`legalBasis` (根拠法令)** — the cited statute + article/section is current
   and actually establishes the remedy (G8 non-fabrication). Re-check on every
   verification: statutes are amended (e.g. EU 2023/2673, EU ODR repeal — see the
   drift warnings already in entry notes).
7. **`formRef`** — the referenced `chigiri:consumer:*` template id exists / is
   the right template for this remedy kind (kurashimori pulls templates from
   chigiri; it authors none of its own).
8. **`deliveryChannel`** — the channel is correct (e.g. 内容証明郵便 / written
   notice / issuer billing-inquiries address / online portal) and any cited
   portal URL resolves to the actual entry point, not a landing page.
9. **`escalationForum`** — the named センター / ADR / ombudsman / public authority
   still exists, still has jurisdiction, and the contact (phone/portal) resolves
   (e.g. confirm the EU ODR platform is NOT cited — it was repealed 2025-07).
10. **`provenance`** — resolves, is an official source for this `jurisdiction`
    (see below), and actually supports the above fields. **If provenance cannot
    be confirmed official, the entry stays `unverified-seed`** (fail-closed).
11. **`lastVerified`** — set to the verification datetime (UTC, ISO-8601 Z).
12. **`notes`** — the per-entry 弁護士法/司法書士法/UPL boundary caveat + any
    drift/confidence warning is present and still accurate after the re-check.
13. **弁護士法/司法書士法/UPL re-check (G5)** — confirm kurashimori's use of this
    entry remains 診断 + 起草補助 + 本人送付 only; the cooling-off eligibility
    output is an **INFORMATIONAL date computation, NOT a legal opinion**. No
    representation, no claims-buying, no fees. If the remedy inherently requires
    legal characterization, 代理, 差止, or claims recovery (e.g. 適格消費者団体
    差止, ADR 代理), the entry's guide MUST route to chigiri + licensed counsel
    (or warifu for the card-side chargeback, wakai for irrecoverable loss), not
    kurashimori. National analogs apply per jurisdiction (German RDG, French
    monopole de l'avocat / loi n°71-1130, Advocates Act 1961, Legal Profession
    Act, Law Society Act, 변호사법, etc.).

Only when **all 13** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

### CRITICAL — `statutoryWindowDays` + `windowStart` are the harm surface

A wrong cooling-off / withdrawal 日数 (or a wrong 起算 rule) is **actively
harmful**: a member who relies on it can miss a statutory deadline and lose a
right entirely. The per-field checklist therefore **foregrounds** items 4 and 5.
These windows drift and vary by transaction type *and* jurisdiction — JP 訪問販売
8日 vs 連鎖販売 20日 (起算日=1日目, inclusive); US FTC 3 **business** days; EU/DE/FR/UK
14 **calendar** days (翌日起算, +12 months if the trader failed to inform);
Ontario 10 days; KR/BR 7 days; US FCBA 60 days. A maintainer MUST re-derive both
`statutoryWindowDays` and `windowStart` from the *cited statute text on the
official source* — not from a guide, not from the seed value, not from memory —
before flipping the entry. When in doubt, leave it `unverified-seed`.

## WORLDWIDE provenance — per-jurisdiction official-source check

The registry is multi-jurisdiction (65 entries spanning 41 jurisdictions). The
provenance "official source" check is therefore **per-jurisdiction**: a maintainer
confirms `provenance` resolves to the *official authority / official statute
publisher domain for that entry's `jurisdiction`*, for example —

| Bloc | Acceptable official-source domains (non-exhaustive) |
|---|---|
| JP (`jpn`) | `*.go.jp` (e.g. `caa.go.jp`, `no-trouble.caa.go.jp`, `kokusen.go.jp`) |
| US (`usa`) | `*.gov` / `ecfr.gov` / `law.cornell.edu` (official statute mirror) / state `*.ca.gov`, `*.ny.gov`, `texas.gov` / `consumerfinance.gov` |
| EU (`eu-wide`) | `eur-lex.europa.eu` / `*.europa.eu` / `commission.europa.eu` |
| DE (`deu`) | `gesetze-im-internet.de` / `*.europa.eu` directory |
| FR (`fra`) | `legifrance.gouv.fr` / `*.gouv.fr` (e.g. `signal.conso.gouv.fr`, `economie.gouv.fr`) |
| UK (`gbr`) | `legislation.gov.uk` / `gov.uk` / `financial-ombudsman.org.uk` / `citizensadvice.org.uk` |
| CA (`can`) | `*.gc.ca` / provincial `ontario.ca` etc. |
| KR (`kor`) | `*.go.kr` |
| Other (`bra`/`mex`/`aus`/`ind`/`sgp`/`isr`/`zaf`/…) | the national `.gob.*` / `.gov.*` / `.go.*` / statutory-authority official domain for that country, or a recognised international body (`econsumer.gov`, ICPEN/OECD/UNCTAD) |

It is **NEVER** acceptable to verify an entry against a third-party blog, a law
firm's marketing page, a news article, a wiki, or a commercial aggregator.
**Fail-closed**: if the official source for the jurisdiction cannot be confirmed,
the entry stays `unverified-seed` — no exceptions, in any bloc.

## maintainer-verified → council-verified (代行 eligibility)

Additional to the above, for an entry to be eligible for the 代行
(`agent-on-behalf`) send path:

- Council Lv6+ review of the remedy + its 司法書士法/行政書士法/弁護士法 (and the
  jurisdiction's analog) reserved-practice exposure — does sending the 通知/苦情
  on a member's behalf cross a reserved-practice or 取立 line?
- a recorded council gate reference — and note that **executing** 代行 still
  requires the **R3** gate (Council Lv7+ unanimity + 司法書士法/行政書士法
  clearance), independent of this registry tier (G15). At R0/R1 the 代行 path is
  not enabled; **self-send is the default**, and `kurashimori_send` is gated to
  `maintainer-verified` entries for the member's own matter only.

## Current seed status (2026-06-02)

All **65** entries `unverified-seed`, all with `legalBasis` + `provenance` +
`lastVerified` present, all `provenance` on per-jurisdiction official sources
across 41 jurisdictions (JP/US/EU/UK-CW/INTL-ROW + long-tail). Statutory windows
and citations were authored from the official sources but are **not yet
maintainer-verified** — they are best-effort routing/diagnosis scaffolds, not
authoritative deadlines (drift expected; several entries already carry explicit
drift/confidence warnings, e.g. EU ODR repeal 2025-07, EU 2023/2673 cancel-button
~2026-06, NY door-to-door section-numbering caveat, US dollar-threshold drift).
No entry may gate `kurashimori_send` until it passes the checklist above.

## Machine-enforced floor

`70-tools/scripts/audit/test_kurashimori_registry_seed.py` pins the fail-closed
registry invariants: the file parses with a non-empty `targets` list + integer
`freshnessWindowDays`; every `remedyId` is unique (fail-closed on duplicates);
**every** entry ships `verificationStatus == "unverified-seed"` (G14 — no seed
shipped pre-verified); every entry carries a non-empty `https` `provenance` + an
ISO-8601 Z `lastVerified`; the registry spans ≥ 12 distinct `jurisdiction`
values (worldwide-coverage / anti-JP-only regression guard); and the
弁護士法 / 司法書士法 / UPL boundary caveat is present in entry `notes` (not only a
top-level comment). A seed shipped pre-verified, missing a citation, with a
duplicate id, or that regresses to shallow single-bloc coverage fails CI. The
G14 send refusal itself lives in the `kurashimori_*` cells (R0: import-raise — no
cell runs, nothing is sent).
