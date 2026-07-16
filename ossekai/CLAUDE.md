# 20-actors/ossekai — CLAUDE.md

## Identity

- **Name**: ossekai (御節介 — caring proactive intervention)
- **DID**: `did:web:ossekai.etzhayyim.com`
- **ADR**: ADR-2605264000 (R2 Autonomous, upgraded from R0 scaffold)
- **Parent ADR**: ADR-2605192100 (Mission Charter — Wellbecoming §1.13 + Anti-individualism §1.4 + Covert-ops-avoidance §2(c))
- **AT-Proto-substrate parent**: ADR-2605231902 (`app.bsky.feed.post` membrane + L1-projection feed-discover preserved unchanged)
- **Status**: R2 — fully operational targeted + aggregate paths. **Live broadcast is G7/G10 outward-gated (no-server-key)**: composition/analysis/consent/rate-limit/G13-attestation all run autonomously, but the actual AT-Proto post stays `:draft` (nothing broadcast) unless an operator attestation (`operatorRef`) OR a presented member-signed, scoped, revocable capability (`memberCapability`, ibuki/mimamori precedent ADR-2606111400) authorizes the leg — the write is then attributed to that member/operator, never a server-held key (FINDING-G7 resolved 2026-06-17, ADR-2605231525).
- **Form**: 任意団体 internal artificial-organism information-arbitrage substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

ossekai walks the cultural knife-edge of 御節介 (caring proactive
intervention vs. unwelcome meddling). The constitutional discipline
structurally pins the actor on the caring side via four invariants:

1. **AT Protocol first-touch only** (N1) — `app.bsky.feed.post` +
   custom feed generator + `@mention`; NO email / SMTP at R0-R2.
   Resolves seven distinct constitutional tensions that arise with
   email-based outreach (see ADR §Context table).
2. **Aggregate-first publication** (G4) — anonymized AT Proto feed
   is the DEFAULT mode; targeted dispatch is the SECONDARY mode;
   silenOssekaiReview quarterly audit enforces aggregate-share ≥50%
   by volume. Inverting this ordering at runtime is structurally
   prohibited.
3. **Council-gated single-touch for non-members** (G13) — Council
   Lv6+ ≥3 attestation per non-member @mention campaign (≥4 if
   campaignSize >50 handles); EITHER `memberImpactAttestationCid` OR
   per-recipient `externalMentionConsent` path required.
4. **AT Proto native mute/block honored at projection layer** (G15) —
   `mention_dispatcher` cell rejects dispatch BEFORE composing post.
   The post never enters MST. Cleaner than email-based unsubscribe
   (rejection BEFORE composition, not after).

## Architecture

8 Pregel cells, all on `issachar` Murakumo node (witness pair pattern
for organism observation; existing pattern from ADR-2605240200
KaizenObserverCell + ADR-2605232345 UNSPSC organism Wave 1):

```
arbitrage_observer ──── issachar (continuous heartbeat; sensor consume)
intel_analyzer ──────── issachar (joucho-cadence; cross-correlate + framing audit)
aggregate_publisher ─── issachar (hourly; AT Proto feed-post DEFAULT mode)
member_digest ──────── issachar paired-PDS (weekly; encrypted envelope)
mention_dispatcher ──── issachar (event; Council attestation gated)
consent_registry ────── issachar (continuous; AT Proto block/mute ingest)
kaizen_observer ─────── issachar (quarterly; KaizenObserverCell pattern)
emergency_advisory ──── issachar paired-kazaori (event; kazaori cross-actor)
```

All cell modules at R0 are import-time `RuntimeError`. R1 activation
requires e7m-dataset Tier-A foundations + legal-foundations-r1 recipe
ratified + chigiri R1 (UPL boundary) + iyashi R1 (medical boundary).

## Aggregate-First Discipline (G4) — Constitutional Novelty

G4 is constitutionally novel for ossekai compared to adjacent
information-delivery actors. Most "intel delivery" actors in adjacent
ecosystems are individual-targeted by default (HubSpot / Mailchimp /
Salesforce / etc.) with aggregate publication as the secondary mode.
ossekai **inverts this**: aggregate publication (public-good,
anonymized) is the default; targeted contact requires Council
attestation + per-recipient consent OR documented member-impact.

This is the structural enforcement of Charter §2(c) covert-ops
avoidance + Charter §1.4 anti-individualism in the information-delivery
domain. The discipline holds via:

1. `silenOssekaiReview.anonAggregateSharePctIntegerHundredths`
   minimum 5000 (≥50.00%) STRUCTURAL schema constraint;
2. Quarterly Council Lv6+ ≥3 audit attestation;
3. If aggregate-share drops below 50% in any quarter, the next
   quarter's mention_dispatcher campaign cap is structurally halved
   until aggregate-share recovers.

## Why AT Protocol First-Touch Resolves Email's Constitutional Tensions

| Tension under email model | Resolution under AT Proto first-touch |
|---|---|
| Vendor CRM (Salesforce / Mailchimp) data-sovereignty | N/A — AT Proto is open-spec; no SaaS dependency |
| Signed sender (DKIM / SPF / DMARC) brittleness | Built-in — every post is DID-bound + cryptographically signed |
| 1-click unsubscribe friction (RFC 8058) | Built-in — `app.bsky.graph.block` + `mute` are spec-native |
| Spam-classifier evasion risk | Mooted — Bluesky-compatible clients render mute/block transparently |
| Per-jurisdiction email law (GDPR / CCPA / CAN-SPAM / APPI / LGPD) | Largely resolved — AT Proto activity is voluntary publication |
| Dark patterns (urgency / shame / engagement-hacking) | Discoverable — every post is public + replayable + auditable |
| Auditability of unsent drafts | Cleaner — G15 rejection BEFORE composition; no "unsent draft" record |

## G15 — AT Proto Native Mute/Block at Projection Layer (Constitutional Novelty)

The `mention_dispatcher` cell rejects dispatch to muted/blocked
handle BEFORE composing the post. The post never enters MST. This
is the structural enforcement of "the moment a target signals 'do not
contact,' ossekai is structurally prevented from contacting them
again."

Compared to email-based unsubscribe (where mail is composed first
then suppressed), AT Proto + projection-layer rejection is cleaner —
the negative-signal arrives before composition, so there is no
auditable "unsent draft" record. Combined with G14 quarterly audit
(`silenOssekaiReview.reEngagementAfterOptOutCount: const 0`), any
post-revokedAtUtc dispatch attempt is a critical finding triggering
`chigiri.disputeMediation` + immediate cell halt.

## Cross-Actor Boundaries (UPL / Medical / Financial)

ossekai MUST NOT render legal / medical / financial advice. The
`wellbecomingAdvisory.crossActorCitation` field is REQUIRED for any
advisory in those domains:

| Domain | Citation target | Boundary |
|---|---|---|
| Legal | `chigiri.ipLicenseClaim` (ADR-2605262700) | UPL prohibited; cite licensed-counsel routing |
| Medical | `iyashi` cross-actor (ADR-2605263000) | Clinical-advice rendering PROHIBITED |
| Diagnostic | `mitate` cross-actor | Diagnostic-advice rendering PROHIBITED |
| Pharmaceutical | `yakushi` cross-actor (ADR-2605250500) | Medication-advice PROHIBITED |
| Financial | `toritate` cross-actor (ADR-2605262900) | Financial-advice PROHIBITED; toritate is intel SOURCE only |

## R1 Activation Triggers

1. ADR-2605264000 Council Lv6+ ≥3 ratify;
2. e7m-dataset Tier-A foundations available (ADR-2605262400 W1 LANDED
   2026-05-26 ✓);
3. legal-foundations-r1 recipe ratified (ADR-2605262800 W1 anchor
   sources fetchers + sensors);
4. chigiri R1 active (cross-actor UPL boundary for legal-themed
   advisories);
5. iyashi R1 active (cross-actor medical-advice boundary);
6. `50-infra/etzhayyim-did-web/` PDS extension for custom feed
   generator `feed.ossekai.wellbecoming` (Bluesky-compatible
   AppView feed-gen surface);
7. ≥1 Charter Rider scan integration validation (Murakumo G12
   inference of framing-audit Lexicon `wellbecomingAdvisory.framingAuditCid` confirmed working).

## R1 Cell Activation Order

1. `ossekai_arbitrage_observer` (sensor consume; no publication yet);
2. `ossekai_intel_analyzer` (Wellbecoming framing audit + advisory
   composition; no publication yet);
3. `ossekai_aggregate_publisher` (AT Proto feed-post + custom feed
   generator live; ≤100 advisories/week ceiling).

R2 adds `ossekai_member_digest` (encrypted envelope per ADR-2605181100;
≤500 opt-in members) + `ossekai_mention_dispatcher` (Council Lv6+ ≥3
attestation gate; ≤50 non-member handles/quarter) + `ossekai_consent_registry` (AT Proto block/mute ingestion live).

R3 adds `ossekai_kaizen_observer` (KaizenObserverCell per ADR-2605240200
with 6 new rules R12..R17) + `ossekai_emergency_advisory` (kazaori
cross-actor; expedited Wellbecoming-positive emergency advisory; NO
fear-amplification per G10).

## Build & Deploy

**R0 status**: Scaffold only. R0 cells RuntimeError on import.

R1 smoke test (when cells created):
```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.ossekai_arbitrage_observer import _r0_marker" 2>&1 | grep "R0 scaffold"
```

R1 AT Proto smoke test (when cells created):
```bash
# Verify ossekai DID resolves
curl -s https://ossekai.etzhayyim.com/.well-known/did.json | jq .id
# Verify custom feed generator endpoint
curl -s https://ossekai.etzhayyim.com/xrpc/app.bsky.feed.getFeedGenerator?feed=at://did:web:ossekai.etzhayyim.com/app.bsky.feed.generator/wellbecoming
```

## Related Files

- `/20-actors/ossekai/manifest.jsonld`
- `/20-actors/ossekai/README.md`
- `/00-contracts/lexicons/com/etzhayyim/ossekai/` (9 Lexicons + README)
- `/90-docs/adr/2605264000-ossekai-information-arbitrage-tier-b-actor-r0.md`
- `/90-docs/adr/2605231902-feed-post-membrane-and-feed-discover-projection.md` — AT Proto first-touch substrate (preserved unchanged)
- `/90-docs/adr/2605262400-public-data-ingestion-via-ipfs-datalad.md` — Sensor source
- `/90-docs/adr/2605262800-global-legal-corpus-ingestion-via-ipfs-datalad.md` — Legal corpus source
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — UPL boundary cross-actor
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — Medical-advice boundary cross-actor
- `/90-docs/adr/2605263200-kazaori-disaster-response-tier-b-actor-r0.md` — Emergency advisory cross-actor
- `/CHARTER-RIDER.md` §2(c) + §2(e) — G3 + G5 sources
- `/CLAUDE.md` — Status table row 73
