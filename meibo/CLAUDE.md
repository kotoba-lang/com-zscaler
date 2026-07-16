# meibo 名簿 — verified legal-institution directory registry

**DID**: `did:web:etzhayyim.com:actor:meibo` (registration deferred, matches
saisei's own R0 posture) · **Tier**: B · **Status**: 🟡 R0 (seed — 10
jurisdictions) · **ADR**: 2607062200 · **depends**: 2607061800 (saisei — the
pattern this actor generalizes) · 2606112301 + 2606112400 (tate — the
30-jurisdiction referral precedent this actor's worklist overlaps) ·
2607022300 (unified actor deploy — motivates self-containment)

## What this is

The honest, lawful fulfillment of a gap gftdcojp's ADR-0016 named in
2026-04-14 and never built: `judge` (200K judges), `bengoshi` (2.5M lawyers),
`adr` (1M ADR cases/yr), `legal-aid` (10M legal-aid cases/yr) were all planned
as bulk-data actors and none were ever implemented. meibo does **not**
attempt that scale — none of the underlying institutions publish a
bulk-exportable dataset, several restrict scraping in their ToS, and
individual professional records (bar numbers, disciplinary history) carry
their own data-protection weight to republish without the institution's own
participation. Instead: a **verified LINK registry** to the official search
tool each institution already runs — the same pattern already proven inside
saisei (`:proc/official-forms-url`, `legal_directory`), generalized into its
own actor and grown to 10 jurisdictions (`data/legal-directory.edn`, 22
entries).

## Hard gates (constitutional — read before any change)

- **G1 institution-level only.** No individual professional records — bar
  number, disciplinary history, docket, personal contact info. Only the
  institution's own official search-tool URL. A schema field for an
  individual's name/bar-number would violate this (tests enforce their
  absence).
- **G2 non-adjudicating.** meibo never says "this lawyer is good" — it points
  at the authoritative place to verify licensing/standing yourself.
- **G10 jurisdiction/provenance honesty.** Every `:dir/url` was verified live
  (WebSearch/WebFetch) before being recorded — never guessed or recalled from
  memory. `coverage_report.cljc` names the ~183 uncovered jurisdictions as an
  explicit worklist (entries drop off automatically as they're covered),
  never silently claims coverage it doesn't have.

## Non-goals

N1 does not ingest/cache/republish individual professional records · N2 does
not rank or recommend a specific professional/firm · N3 does not become a
code-level `require` dependency of saisei/tate/toritsugi (see "Actor
independence" below).

## Actor independence (why this stays self-contained)

Every Tier-B actor here is meant to be split into its own standalone GitHub
repo by `actor:publish` (ADR-2607022300). That's why meibo carries its own
`methods/edn.cljc` copy rather than requiring a shared library namespace —
had saisei's `filing_plan.cljc` instead done
`(require '[meibo.methods.directory ...])`, splitting saisei into
`com-etzhayyim-saisei` would silently break the moment meibo's files aren't
present in that new repo. Consumers (saisei's own 4-jurisdiction
`legal_directory` stays as its own self-contained copy; a future tate wave)
are expected to consume meibo via its own public API surface (once deployed)
or a synced data snapshot — never a source-level dependency.

## Layout

```
20-actors/meibo/
├── CLAUDE.md                # this file
├── README.md
├── manifest.edn              # actor manifest (0 cells — link-registry only, 3 gates, 3 non-goals)
├── data/
│   └── legal-directory.edn   # 22 entries × 10 jurisdictions, each :dir/url verified live
├── methods/                  # clj/bb (.cljc) — kotoba-native, self-contained
│   ├── edn.cljc              # minimal EDN reader (own copy — see Actor independence)
│   ├── directory.cljc        # by-jurisdiction / jurisdictions-covered
│   └── coverage_report.cljc  # honest jurisdiction coverage + named gaps (G10)
├── tests/                    # clj/bb (.cljc) — bb run_tests.sh (9 tests / 139 assertions)
│   ├── test_directory.cljc
│   └── test_coverage.cljc
└── run_tests.sh
```

## Run

```bash
bash 20-actors/meibo/run_tests.sh   # full suite: 9 tests / 139 assertions green

bb --classpath 20-actors -e '(require (quote [meibo.methods.coverage-report :as c])) (print (c/report (c/coverage)))'
```

## Do not

- Do not add a per-individual field (attorney name, bar number, disciplinary
  record) to `data/legal-directory.edn` — G1 (tests enforce their absence).
- Do not record a `:dir/url` you have not verified live — G10. Adding a
  jurisdiction = verify the institution's real URL (WebSearch/WebFetch), then
  one EDN entry + a test; no code change.
- Do not `require` this namespace from another actor's `.cljc` — see Actor
  independence above. Consume via meibo's own API surface or a data snapshot.
