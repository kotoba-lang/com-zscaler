# akashi 証 — Coverage Matrix

Honest R0 coverage for public ad-disclosure sources. **R0 ships source planning
and schema coverage, not live collection.** A source counts as covered only when
it has:

1. `sourcePolicySnapshot` with `collectionStatus=allowed`
2. a methodNote for its adapter/parser
3. at least one fixture, public page, or public file sample parsed into
   `adDisclosureSnapshot`
4. source lineage preserved through creative/delivery/landing records

## Source Coverage Status

| Source family | Platforms | R0 status | Counts as covered? |
|---|---|---|---|
| social ad libraries | Meta/Facebook/Instagram, X/Twitter | public-page scribe ready; fixture parser still covered | public page/file only |
| social ad libraries | TikTok | registry seed only; no adapter | no |
| messaging / portal ad disclosures | LINE | registry seed only; access-mode requires manual review | no |
| search/video ad libraries | Google / YouTube | registry seed only; no adapter | no |
| regulator repositories | EU / DSA-style repositories, election-ad archives | fixture-only bulk parser with lexicon validation; no live adapter | no |
| regional transparency portals | jurisdiction-specific ad libraries | placeholder only | no |

## Field Coverage

| Field group | Lexicon | R0 schema? | Live data? |
|---|---|---|---|
| source policy / ToS / robots / cadence | `sourcePolicySnapshot` | yes | no |
| raw public snapshot + payload CID/hash | `adDisclosureSnapshot` | yes | no |
| disclosed advertiser identity | `advertiserIdentity` | yes | no |
| creative text/media/category | `creativeDisclosure` | yes | no |
| delivery period, spend/impression ranges | `deliveryDisclosure` | yes | no |
| landing URL/domain/hash evidence | `landingEvidence` | yes | no |
| non-adjudicating cross-platform links | `adDisclosureLink` | yes | no |
| aggregate transparency report | `adTransparencyReport` | yes | no |
| malak evidence candidate | `malakEvidenceCandidate` | yes | no |

Closure fixture coverage exists for `adDisclosureLink`, `adTransparencyReport`,
and `malakEvidenceCandidate`; all remain fixture-only and non-adjudicating.
Meta/Instagram and X fixture parser coverage exists for source-disclosed
platform ad-library records and projects them into the same advertiser,
creative, landing, delivery, and snapshot lexicons as regulator bulk fixtures.
`adapters/ingest_platform_ad_library.cljc` accepts reviewed local
Meta/Instagram/X-style JSON snapshots from an operator and emits records,
DataScript/kotoba tx EDN, or a Datomic schema/scalar-tx import bundle without
network access.
`adapters/public_page_scribe.cljc` is the production path for public
information: public pages and operator-saved public files are preserved as raw
scribe EDN, then parsed into DataScript/kotoba tx EDN and Datomic scalar bundle
EDN. It has no platform-token mode and materializes EDN only under explicit
`--materialize`.
`adapters/dry_run_fixtures.cljc` exercises the local fixture set and validates
every emitted record without network access or writes.
`fixtures/dry_run/summary.golden.json` pins the dry-run record counts. A second
regulator fixture covers missing optional source-disclosed fields, and negative
fixtures prove malformed source records / malak-imported closure records are
rejected.

## R1 Promotion Rules

A platform source can move from `candidate` to `covered-r1` only when:

- terms/robots/source policy is reviewed and represented as a
  `sourcePolicySnapshot`
- collection requires no login, no sockpuppet account, no anti-bot bypass, and
  no interactive dark-pattern path
- fixture parsing preserves source-limited fields without inventing missing
  values
- `methodNote` states false-positive limits
- `akashi` tests prove no voter/person profile fields are present in output
- malak bridge remains disabled unless a reviewed public IOC fixture is present
- `source-policy-reviews.seed.json` moves the source runtime from `disabled`
  with an attested review transaction
- `source-policy-approval.schema.json` records the review transaction and
  rollback-to-disabled requirements

## R0 Gaps

- Live adapter code exists only for public pages/operator-saved public files.
  No platform-token path is present.
- Regulator bulk and platform ad-library fixture parsers validate output against
  akashi lexicons; no live platform adapter exists.
- Closure fixtures validate link/report/malak candidate records without live
  collection.
- Dry-run CLI exists for local fixture validation only; it has no network mode
  and does not write kotoba records. `--emit-edn` emits deterministic
  DataScript/kotoba tx-data plus a Datomic schema/scalar-tx import bundle for
  external storage/import by a caller.
- Dry-run summary has a golden regression fixture.
- Optional-field and negative fixtures exist for parser regression coverage.
- No live public-page fetch has been materialized in this workspace.
- Fixture EDN and its storage manifest are durably published through GitHub and
  Radicle RID `rad:z2kYxHLH4E6pJHksgzAkRm9ztFgjC`; this is publication
  coverage for the reviewed fixture dataset, not live source coverage.
- Source-policy review workflow enables only the public-page scribe path for Meta/X;
  other live sources remain disabled.
- Source-policy approval format exists; current parser examples are
  fixture-only, not live collection.
- Cell scaffold exists under `40-engine/kotoba/crates/kotoba-kotodama/cells/akashi_*`, but every
  cell raises at import until ADR-2606022300 R1 activation gates are attested.
- Lexicon-specific invariant and fixture parser tests exist in
  `adapters/test_*.cljc` and `methods/test_manifest_invariants.cljc`.
- `source-catalog.seed.json` is planning metadata only; it does not authorize
  collection.
