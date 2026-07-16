# ossekai (御節介) — Non-profit Religious-Corp Information-Arbitrage Elimination + Wellbecoming-Nudge Artificial-Organism Actor

**DID**: `did:web:ossekai.etzhayyim.com`
**Namespace**: `com.etzhayyim.ossekai.*`
**ADR**: ADR-2605264000 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — 8 cells path-reserved + 9 Lexicon skeletons
**First-touch channel**: AT Protocol — `app.bsky.feed.post` (existing membrane per ADR-2605231902) + custom feed generator `feed.ossekai.wellbecoming` + `@mention` (NO email / SMTP at R0-R2)
**Cross-actor**: e7m-dataset + legal corpus + toritate (sources) / chigiri + iyashi + mitate + yakushi (boundaries) / kazaori (emergency advisory) / baien-moemoekyun + kotoba + feed-discover (substrate)

## Overview

Religious-corp artificial-organism actor whose dual mandate is:

1. **AGGREGATE PUBLICATION** — the default mode — anonymized public-good
   intel summaries surfaced via AT Protocol `app.bsky.feed.post` +
   custom feed generator (Bluesky-compatible reach without vendor
   lock-in);
2. **OSSEKAI-MODE NUDGE** — the secondary mode — caring proactive
   notification to opted-in Adherent SBT members (encrypted private
   digest per ADR-2605181100) + Council-gated single-touch `@mention`
   to non-member companies / individuals on AT Protocol.

The cultural framing — 御節介 (ossekai / o-sekkai) — captures the
deliberate ambivalence the actor must hold: caring proactive
intervention walking the knife-edge between compassionate-helpfulness
and unwelcome-meddling. The constitutional discipline (15 gates)
structurally pins the actor on the caring side.

## Identity (CRITICAL — IMMUTABLE)

- **First-touch = AT Protocol** (N1) — `app.bsky.feed.post` + custom
  feed generator + `@mention`. NO email / SMTP at R0-R2. Email-bridge
  to AT Proto DM for opted-in members is R3+ Council Lv7+ unanimity
  gate; non-member email contact permanently prohibited at this charter
  level.
- **PASSIVE-ONLY collection** (G3) — no live DNS / port-probe /
  traceroute / WHOIS / RDAP / DoH / handle-enumeration against third
  parties; only pre-published public archives (e7m-dataset Tier-A
  foundations + legal corpus) + voluntarily-published AT Proto
  activity. Per ADR-2605262400.
- **Aggregate-first publication** (G4) — anonymized AT Proto feed-post
  is the DEFAULT mode; targeted dispatch is the SECONDARY mode;
  quarterly silenOssekaiReview enforces aggregate-share ≥50% by
  volume. The default order MUST NOT be inverted at runtime.
- **NO commercial intel/CRM/marketing software** (G5) — Salesforce /
  HubSpot / Marketo / Mailchimp / SendGrid commercial / Constant
  Contact / Pardot / ZoomInfo / Apollo / Clay / Lemlist / Outreach /
  SalesLoft / Gong / Chorus / 6sense / Cognism / LeadIQ / Drift
  PROHIBITED per Charter Rider §2(e) + §2(c). The AT Protocol +
  PDS-native architecture sidesteps the entire category.
- **NO dark patterns** (G6) — AT Proto native mute/block/quote-block
  honored structurally at projection layer (G15 — `mention_dispatcher`
  rejects dispatch BEFORE composing post); no urgency / no scarcity /
  no engagement-hacking / no A/B-test-for-conversion / no manipulation.
- **Rate limit hard-coded** (G7) — per non-member handle ≤1 @mention /
  90-day rolling window; per Adherent-SBT member ≤1 digest/week + ≤1
  ad-hoc/month UNLESS member opts to higher cadence via
  `memberDigestSubscription.cadenceOverride`.
- **Encrypted envelope MANDATORY** for member digest (G8) — per
  ADR-2605181100; `memberDigestRecord.encryptedPayloadCid` REQUIRED;
  plaintext rejected at schema layer.
- **Signed sender DID transparent** (G9) — every AT Proto post
  (feed-post OR @mention) carries `did:web:ossekai.etzhayyim.com` +
  cryptographic signature; no spoofing / no white-labeling / no
  proxy-sender.
- **Wellbecoming-positive framing only** (G10) — Charter §1.13 — no
  Gore / no fear-amplification / no shame / no zero-sum framing;
  framing-audit attestation in every `wellbecomingAdvisory`.
- **Anti-individualism** (G11) — Charter §1.4 — community + multi-gen
  context preferred over individual nudge; campaign metadata records
  audience-share-distribution.
- **Murakumo-only inference** (G12) — per ADR-2605215000; commercial
  AI (OpenAI direct / Anthropic-direct from vendor key / AWS Bedrock /
  Vertex AI direct / RunPod GPU / Lambda Labs / CoreWeave / Vast.ai)
  PROHIBITED.
- **Council Lv6+ ≥3 attestation for non-member @mention campaign**
  (G13) — ≥4 attestation if campaignSize >50 handles;
  `mentionDispatchAttestation.attestingCouncilDids` minLength
  structural.

## 8 Pregel Cells (R0 path-reserved)

All cells path-reserved under `40-engine/kotoba/crates/kotoba-kotodama/cells/ossekai_*/`.
Cell modules created at R1 ratification with import-time
`RuntimeError("ossekai R0 scaffold: activate via Council ADR + R1 ratification + e7m-dataset Tier-A foundations available + legal-foundations-r1 recipe ratified + chigiri R1 active for UPL boundary + iyashi R1 active for medical boundary")`.

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `ossekai_arbitrage_observer` | issachar | continuous | sensor stream → arbitrageGapReport |
| `ossekai_intel_analyzer` | issachar | joucho-cadence | arbitrageGapReport → wellbecomingAdvisory + framing audit |
| `ossekai_aggregate_publisher` | issachar | hourly | wellbecomingAdvisory (anon) → AT Proto feed-post + feedPostAttestation |
| `ossekai_member_digest` | issachar (paired-PDS) | weekly | wellbecomingAdvisory (filtered) → encrypted envelope → memberDigestRecord |
| `ossekai_mention_dispatcher` | issachar | event | mentionDispatchAttestation + consent + mute/block check → AT Proto `@mention` |
| `ossekai_consent_registry` | issachar | continuous | externalMentionConsent + unsubscribeRecord + AT Proto block/mute → consent state |
| `ossekai_kaizen_observer` | issachar | quarterly | unsubscribe rate / spam-flag / re-engagement-after-opt-out / staleness → KaizenProposal |
| `ossekai_emergency_advisory` | issachar (kazaori-paired) | event | kazaori.emergencyDeclarationAttestation → expedited Wellbecoming-positive advisory |

## 9 Lexicons under `com.etzhayyim.ossekai.*`

| Lexicon | Purpose |
|---|---|
| `arbitrageGapReport` | Per-detection record of information-asymmetry pocket; G3 passive-only structural |
| `wellbecomingAdvisory` | Curated advisory; G10 framing-audit + G11 audience-share + UPL/medical boundary citation |
| `feedPostAttestation` | Per AT Proto post emission audit-trail; G9 signed sender + G15 mute/block check |
| `memberDigestSubscription` | Adherent SBT member opt-in subscription; per-category granularity |
| `memberDigestRecord` | Per-delivery audit-trail; G8 encryptedPayloadCid REQUIRED |
| `mentionDispatchAttestation` | Council Lv6+ ≥3 attestation; G13 structural |
| `externalMentionConsent` | Non-member explicit prior consent; per-category; ≤365-day expiry |
| `unsubscribeRecord` | Unified unsubscribe; ingests AT Proto block/mute; G15 effective-immediately |
| `silenOssekaiReview` | Quarterly Council audit; G4/G5/G10/G14/G15 const-field structural enforcement |

See `/00-contracts/lexicons/com/etzhayyim/ossekai/README.md`.

## Constitutional Gates (G1–G15)

See ADR-2605264000 §5. Key novel disciplines:

- **G4** Aggregate-first publication (anonymized AT Proto feed = DEFAULT)
- **G5** NO commercial intel/CRM/marketing software
- **G7** Rate limit hard-coded (≤1/90d/handle / ≤1/week/member)
- **G13** Council Lv6+ ≥3 attestation for non-member @mention
- **G15** AT Proto native mute/block honored at projection layer

## Non-Goals (N1–N12)

See ADR-2605264000 §6. Key:

- **N1** NOT email-based outreach at R0-R2
- **N2** NOT surveillance / OSINT-for-hire
- **N3** NOT marketing automation
- **N5** NOT social pressure / public shaming
- **N6** NOT lobbying / political campaign
- **N11** NOT children-targeted

## Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| **R0** | 2026-05-26 | Scaffold (this commit) |
| **R1** | post-Council + Tier-A foundations + legal-foundations-r1 + chigiri R1 + iyashi R1 | 3 cells: arbitrage_observer + intel_analyzer + aggregate_publisher; AT Proto feed live; ≤100 advisories/week |
| **R2** | post-R1 + 30-day public + first silenOssekaiReview + ≥1 cross-actor cycle | +3 cells: member_digest (≤500 members) + mention_dispatcher (Council gate live; ≤50 handles/quarter) + consent_registry |
| **R3** | post-R2 + Council Lv7+ + multi-juris consent compliance + 4-quarter G14 compliance | +2 cells: kaizen_observer + emergency_advisory (kazaori cross-actor); ≤5000 members; ≤200 handles/quarter |

## AT Protocol Integration

ossekai is a **producer** for the existing membrane per ADR-2605231902
(`app.bsky.feed.post` + L1-projection feed-discover preserved
unchanged). New surface:

- Custom feed generator: `feed.ossekai.wellbecoming` (Bluesky-compatible
  custom feed at AT-URI `at://did:web:ossekai.etzhayyim.com/app.bsky.feed.generator/wellbecoming`)
- Signed-sender DID: `did:web:ossekai.etzhayyim.com` (resolvable via
  existing `50-infra/etzhayyim-did-web/` CF Worker)
- Mute/block ingestion: `app.bsky.graph.block` + `app.bsky.graph.mute`
  consumed via firehose subscribe (read-only; no server-side key per
  ADR-2605231525)

## Related Files

- `/20-actors/ossekai/manifest.jsonld`
- `/20-actors/ossekai/CLAUDE.md`
- `/00-contracts/lexicons/com/etzhayyim/ossekai/` (9 Lexicons + README)
- `/90-docs/adr/2605264000-ossekai-information-arbitrage-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605231902-feed-post-membrane-and-feed-discover-projection.md` — AT Proto first-touch substrate (preserved unchanged)
- `/90-docs/adr/2605262400-public-data-ingestion-via-ipfs-datalad.md` — Sensor source
- `/90-docs/adr/2605262800-global-legal-corpus-ingestion-via-ipfs-datalad.md` — Legal corpus source
- `/CHARTER-RIDER.md` §2(c) + §2(e) — G3 + G5 sources
- `/CLAUDE.md` — Status table row 73
