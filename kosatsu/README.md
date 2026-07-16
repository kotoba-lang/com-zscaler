# 高札 (kosatsu) — crime/sanctions competing-claim observatory

**DID**: `did:web:etzhayyim.com:actor:kosatsu` · **Tier**: B · **Status**: R0 · **ADR**: 2606072000

高札 (kōsatsu) was the public notice-board where each authority posted its own edicts and
designations. kosatsu mirrors that, globally and append-only: every sanctions / export-control /
wanted-notice **designation** is an **event posted by a named authority**, attributed and
primary-sourced — **never etzhayyim's verdict**. Its distinctive object is the **competing-claim
graph**: the same subject under different jurisdictions coexists as parallel datoms, and a
computed **divergence** view makes *"what counts as a sanctionable act varies by political
position"* a first-class, neutral fact.

## Quick start

```bash
./run_tests.sh                         # all 6 suites (79 tests)
cd methods && python3 weave.py         # competing-claim report over the :representative seed
cd methods && python3 analyze.py       # end-to-end → methods/out/intel-report.md
cd methods && python3 social.py        # dry-run divergence posts (member-signed, never published)
cd methods && python3 bridge.py        # cross-actor SoS join keys (tadori/keizu/tsumugi)
cd methods && python3 ingest.py x.json # offline normalize (--live is REFUSED, G8)
```

## The model in one picture

```
authority (asserter, attributed)  ──designation EVENT──▶  subject (named by a public act)
   OFAC / EU / UN / OFSI / MOF /          :listed | :delisted              entity/org/person/
   Interpol / counter-sanction bodies     (append-only, as-of)             vessel/wallet/domain

                         divergence(subject) — computed on read
              ┌───────────────────┬──────────────────────┬─────────────────────┐
          contested            unanimous            single-asserter        coverage-split
   (one delisted what       (≥2 opined,          (only 1 jurisdiction      (listed by some,
    another still lists)      they agree)           took a stance)          silent by others)
```

The seed shows all four signals: `subj-beta` contested (US delisted, EU still lists),
`subj-gamma`/`subj-alpha` unanimous-among-opiners but coverage-split, `subj-delta` single-asserter
(only RU). etzhayyim adopts none of these stances — it reports, attributed, what each authority
itself posted.

## Files

- `00-contracts/schemas/crime-sanctions-ontology.kotoba.edn` — the closed structural vocab (SSoT
  of the invariants the test parses)
- `lex/` — 6 lexicons (assertingAuthority · subjectEntity · designationNotice ·
  competingClaimView · delistingEvent · networkPost)
- `methods/weave.py` — validation + the divergence engine (the heart, G1..G5/G7 anchor)
- `methods/analyze.py · social.py · ingest.py · bridge.py` — report / dry-run posts / offline
  membrane / cross-actor SoS keys
- `data/seed-designation-graph.kotoba.edn` — `:representative` competing-claim seed
- `cells/` — 3 Pregel cell scaffolds (`.solve()` raises at R0, G8) + the publication membrane

See `CLAUDE.md` for the 10 gates and `MATURITY.md` for the per-suite test breakdown.
