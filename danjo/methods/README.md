# danjo method notes — open, versioned detector heuristics

**ADR**: ADR-2605301600 (§4 G6 open method)
**Owner cells**: `danjo_crossref_engine` + `danjo_statement_consistency` (R0 path-reserved; activate at R2)
**Conforms to**: `com.etzhayyim.danjo.methodNote` Lexicon
**Status**: R0 scaffold — `v1-jp-seed` is a DRAFT 雛形 pending Council Lv6+ ≥3 attestation

## Purpose

danjo's **G6 open-method invariant** requires that every detector heuristic be
**published, open, and versioned** — so the public can audit the *detector
itself*, not only its output. These method notes are the canonical, method-
versioned definitions that the cross-reference cells run; every
`danjo.discrepancyObservation` carries the `methodNoteCid` of the method that
produced it. There is no closed or secret scoring (analogous to how toritate
ships open `valuation/` reference tables, ADR-2605301020).

## Non-adjudication is built into every method (G4)

Each method describes a **FACTUAL cross-reference pattern over public records**,
never a finding of wrongdoing. Two disciplines are mandatory in every entry:

1. **`definition`** states the pattern as a fact about the corpus — e.g. "N
   consecutive single-bid awards from one authority to one awardee" — and never
   as "fraud" / "違反" / a verdict.
2. **`knownFalsePositiveModes`** is required and honest: it documents why a hit
   is **NOT, by itself, evidence of a crime or 不正**. This is the structural
   guard against the actor becoming a defamation vector (N11): the detector
   ships with its own limitations attached.

Severity in a resulting observation is review/routing weight ONLY, never a
wrongdoing score. Legal characterization, if ever sought, routes to external
counsel via chigiri + Public Fund (G4). Named-party publication is gated
separately (G10).

## v1-jp-seed methods (JP-first; 6 seeds)

| methodId | Category | What it factually flags | Dominant false-positive mode |
|---|---|---|---|
| `single-bidder-streak` | single-bidder-streak | ≥N consecutive single-bid awards, one authority → one awardee | lawful sole-source / thin market |
| `awardee-officer-ubo-link` | awardee-officer-ubo-link | awardee shares a public-registry control/officer edge with an authority official | homonym / common-name collision |
| `statement-vs-outlay-divergence` | statement-vs-outlay-divergence | Diet statement figure diverges from published outlay | forecast-vs-realized stage mismatch |
| `outlay-without-appropriation-trace` | outlay-without-appropriation-trace | outlay with no appropriation trace in corpus | corpus-coverage gap (passive-only) |
| `award-amount-anomaly-vs-baseline` | award-amount-anomaly-vs-baseline | award is a robust-z outlier vs category baseline | legitimately large project |
| `modification-inflation` | modification-inflation | cumulative post-award modifications > threshold of original | lawful scope expansion / escalation |

All thresholds in `v1-jp-seed.json` are **placeholder planning figures**, draft
status, `councilAttestation: []`. They are not authoritative until the status
flips to `attested` with ≥3 Council Lv6+ DIDs and they are calibrated against
real pinned `gov.dataset.*` records.

## Versioning

A new method or a threshold change ships as a new `methodNote.version` (and a
new method-pack file `vN-…json`); the prior version is referenced via
`supersedesVersion`. Observations always cite the exact `methodNoteCid` they
were computed under, so historical observations remain reproducible against the
method that produced them.

## Related Files

- `/00-contracts/lexicons/com/etzhayyim/danjo/methodNote.json` — Lexicon schema
- `/00-contracts/lexicons/com/etzhayyim/danjo/discrepancyObservation.json` — the record a method produces
- `/90-docs/adr/2605301600-danjo-public-accountability-oversight-tier-b-actor-r0.md` — §4 G6 open method
- `/20-actors/toritate/valuation/` — sibling open-method-table precedent (ADR-2605301020)
